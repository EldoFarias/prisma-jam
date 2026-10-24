"""Store screenshots: phone prints (Releases -> telas) framed at 1080x1920 with the captions of listings.py.

python3 store/make_screenshots.py <dir with phone-*.png>   ->   store/screenshots/<play-locale>/<play-locale>_1.png ...
"""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from listings import L

SHOTS = sys.argv[1]
OUT = os.path.join(HERE, "screenshots")
W, H = 1080, 1920
# (print, caption index in listings.py)
PICK = [("game1", 0), ("game12", 2), ("daily", 3), ("map", 5), ("themes", 6)]
FONTS = ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "C:/Windows/Fonts/segoeuib.ttf"]
FONTS_HI = ["/usr/share/fonts/truetype/noto/NotoSansDevanagari-Bold.ttf", "C:/Windows/Fonts/NirmalaB.ttf"]


def font(size, hindi=False):
    for path in (FONTS_HI if hindi else FONTS):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise SystemExit("fonte não encontrada")


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background():
    g = Image.new("RGB", (1, H))
    for y in range(H):
        g.putpixel((0, y), lerp((7, 7, 22), (20, 12, 51), y / (H - 1)))
    bg = g.resize((W, H)).convert("RGBA")
    for (cx, cy, r, col, a) in [(0.25, 0.1, 0.55, (124, 58, 237), 0.55), (0.9, 0.6, 0.45, (6, 182, 212), 0.35), (0.15, 1.0, 0.5, (219, 39, 119), 0.4)]:
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        R = r * W
        ImageDraw.Draw(layer).ellipse([cx * W - R, cy * H - R, cx * W + R, cy * H + R], fill=col + (int(255 * a),))
        bg.alpha_composite(layer.filter(ImageFilter.GaussianBlur(R * 0.5)))
    return bg


def wrap(draw, text, f, width):
    lines, cur = [], ""
    for word in text.split():
        test = (cur + " " + word).strip()
        if draw.textlength(test, font=f) <= width or not cur:
            cur = test
        else:
            lines.append(cur)
            cur = word
    return lines + [cur]


def phone(name):
    img = Image.open(os.path.join(SHOTS, f"phone-{name}.png")).convert("RGBA")
    img = img.crop((0, 90, img.width, img.height - 60))  # status bar and gesture bar
    h = 1500
    img = img.resize((round(img.width * h / img.height), h), Image.LANCZOS)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], 48, fill=255)
    img.putalpha(mask)
    return img


def compose(shot, caption, hindi):
    canvas = background()
    d = ImageDraw.Draw(canvas)
    f = font(76, hindi)
    lines = wrap(d, caption, f, W - 140)
    y = 200 - len(lines) * 46
    for line in lines:
        d.text((W / 2, y), line, font=f, fill=(255, 255, 255), anchor="mt")
        y += 96
    x, top = (W - shot.width) // 2, 360
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle([x - 6, top - 6, x + shot.width + 6, top + shot.height + 6], 54, fill=(167, 139, 250, 170))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(18)))
    canvas.alpha_composite(shot, (x, top))
    return canvas.convert("RGB")


if __name__ == "__main__":
    shots = {name: phone(name) for name, _ in PICK}
    for loc, t in L.items():
        d = os.path.join(OUT, loc)
        os.makedirs(d, exist_ok=True)
        for i, (name, cap) in enumerate(PICK, 1):
            # The Play Console asset library shows only file names, so they carry the locale.
            compose(shots[name], t["captions"][cap], loc == "hi-IN").save(os.path.join(d, f"{loc}_{i}.png"), optimize=True)
        print(loc, len(PICK))
