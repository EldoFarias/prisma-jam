"""Composes a calm, seamless lo-fi loop for Prisma Blocks (original, procedurally synthesized)."""
import sys, wave, subprocess, os
import numpy as np

OUT = sys.argv[1]
SR = 44100
BPM = 76
BEAT = 60 / BPM
BAR = 4 * BEAT
CHORDS = [  # (pad voicing midi, bass root midi), 2 bars each
    ([53, 57, 60, 64], 41),  # Fmaj7
    ([52, 55, 59, 62], 40),  # Em7
    ([50, 53, 57, 60], 38),  # Dm7
    ([48, 52, 55, 59], 36),  # Cmaj7
]
BARS_PER_CHORD = 2
LOOP = len(CHORDS) * BARS_PER_CHORD * BAR
TAIL = 4.0
N = int(SR * (LOOP + TAIL))
rng = np.random.default_rng(5)


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):  # small arrays only
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def add(buf, start_s, sig):
    i = max(0, int(start_s * SR))
    j = min(len(buf), i + len(sig))
    buf[i:j] += sig[: j - i]


def adsr(n, a, d, s, r, sus_len):
    t = np.arange(n) / SR
    env = np.zeros(n)
    env = np.where(t < a, t / a, env)
    dd = (t >= a) & (t < a + d)
    env = np.where(dd, 1 - (1 - s) * (t - a) / d, env)
    ss = (t >= a + d) & (t < sus_len)
    env = np.where(ss, s, env)
    rr = t >= sus_len
    env = np.where(rr, s * np.exp(-(t - sus_len) / r), env)
    return env


pad = np.zeros(N)
arp = np.zeros(N)
bass = np.zeros(N)
hats = np.zeros(N)

for ci, (voicing, root) in enumerate(CHORDS):
    start = ci * BARS_PER_CHORD * BAR
    dur = BARS_PER_CHORD * BAR
    # Pad: soft detuned triangle-ish tones, slow attack, long release
    n = int(SR * (dur + 2.5))
    t = np.arange(n) / SR
    env = adsr(n, 0.9, 0.6, 0.8, 0.9, dur)
    chord = np.zeros(n)
    for m in voicing:
        f = hz(m)
        for det in (-0.12, 0.0, 0.12):
            ff = f * 2 ** (det / 12)
            ph = 2 * np.pi * ff * t + rng.random() * 6.28
            chord += np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    trem = 1 + 0.05 * np.sin(2 * np.pi * 0.25 * t)
    add(pad, start, chord * env * trem)

    # Bass: warm sine on beat 1 and the "and" of 3
    for b in range(BARS_PER_CHORD):
        for off, length in ((0.0, 2.2 * BEAT), (2.5 * BEAT, 1.3 * BEAT)):
            n = int(SR * (length + 0.4))
            t = np.arange(n) / SR
            env = adsr(n, 0.01, 0.2, 0.7, 0.15, length)
            f = hz(root)
            add(bass, start + b * BAR + off, (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)) * env)

    # Arpeggio: bell-like plucks, gentle up-down pattern in 8th notes, an octave up
    tones = [m + 12 for m in voicing]
    pattern = [0, 1, 2, 3, 2, 1, 2, 3]
    for b in range(BARS_PER_CHORD):
        for k, p in enumerate(pattern):
            if (b == 1 and k in (6, 7)):
                continue  # breathing space at the end of each chord
            when = start + b * BAR + k * BEAT / 2 + rng.normal(0, 0.006)
            f = hz(tones[p])
            n = int(SR * 1.4)
            t = np.arange(n) / SR
            env = np.minimum(t / 0.004, 1) * np.exp(-t * 3.2)
            tone = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 6) \
                + 0.08 * np.sin(2 * np.pi * 3.01 * f * t) * np.exp(-t * 9)
            vel = 0.75 + 0.25 * rng.random()
            add(arp, when, tone * env * vel)

    # Soft shaker on off-beats
    for b in range(BARS_PER_CHORD):
        for k in range(4):
            when = start + b * BAR + k * BEAT + BEAT / 2
            n = int(SR * 0.09)
            noise = rng.normal(0, 1, n)
            noise = noise - np.convolve(noise, np.ones(6) / 6, mode="same")  # crude high-pass
            env = np.exp(-np.arange(n) / SR * 45)
            add(hats, when, noise * env * (0.6 + 0.4 * rng.random()))

# Gentle low-pass on the pad for warmth (vectorized one-pole via lfilter-like loop on decimated copy)
def smooth(x, k):
    kernel = np.ones(k) / k
    return np.convolve(x, kernel, mode="same")

pad = smooth(pad, 9)
mix = 0.20 * pad / np.max(np.abs(pad)) + 0.30 * bass / np.max(np.abs(bass)) \
    + 0.28 * arp / np.max(np.abs(arp)) + 0.05 * hats / np.max(np.abs(hats))

# Simple echo for the arp/pad space
delay = int(SR * BEAT * 0.75)
echo = np.zeros_like(mix)
echo[delay:] = mix[:-delay] * 0.22
mix = mix + echo

# Make it loop seamlessly: fold the tail back onto the start
L = int(SR * LOOP)
loop = mix[:L].copy()
tail = mix[L:]
loop[: len(tail)] += tail
loop = loop / np.max(np.abs(loop)) * 0.85

wav_path = os.path.join(os.path.dirname(OUT), "_music.wav")
with wave.open(wav_path, "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((loop * 32767).astype(np.int16).tobytes())
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "3", OUT], check=True)
os.remove(wav_path)
print("ok", round(LOOP, 2), "s", os.path.getsize(OUT) // 1024, "KB")
