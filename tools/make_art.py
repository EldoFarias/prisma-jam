"""Prisma Jam art: launcher icons, Play Store icon / feature graphic and a level preview.

python3 tools/make_art.py <project_root> [levels.json]
"""
import json, os, sys, random
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = sys.argv[1]
RES = os.path.join(ROOT, "app/src/main/res")
STORE = os.path.join(ROOT, "store")
os.makedirs(STORE, exist_ok=True)

BLOCKS = [None, (34, 211, 238), (232, 121, 249), (163, 230, 53), (251, 146, 60), (244, 63, 94), (139, 92, 246), (250, 204, 21)]
BG_TOP, BG_BOT = (7, 7, 22), (20, 12, 51)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vgrad(w, h, top, bot):
    g = Image.new("RGB", (1, h))
    for y in range(h):
        g.putpixel((0, y), lerp(top, bot, y / max(1, h - 1)))
    return g.resize((w, h))


def background(w, h):
    bg = vgrad(w, h, BG_TOP, BG_BOT).convert("RGBA")
    for (cx, cy, r, col, a) in [(0.25, 0.2, 0.55, (124, 58, 237), 0.55), (0.9, 0.7, 0.45, (6, 182, 212), 0.35), (0.15, 1.0, 0.5, (219, 39, 119), 0.4)]:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        R = r * w
        ImageDraw.Draw(layer).ellipse([cx * w - R, cy * h - R, cx * w + R, cy * h + R], fill=col + (int(255 * a),))
        bg.alpha_composite(layer.filter(ImageFilter.GaussianBlur(R * 0.5)))
    return bg


def block_mask(cells, cell, size, gap=0.11):
    """Union of cells, inset and with rounded corners (no seams on concave corners)."""
    W, H = size
    pad = int(cell)
    m = Image.new("L", (W + 2 * pad, H + 2 * pad), 0)
    d = ImageDraw.Draw(m)
    for (r, c) in cells:
        d.rectangle([pad + c * cell, pad + r * cell, pad + (c + 1) * cell, pad + (r + 1) * cell], fill=255)
    inset = max(1, int(cell * gap))
    m = m.filter(ImageFilter.MinFilter(inset * 2 + 1))
    m = m.filter(ImageFilter.GaussianBlur(cell * 0.13)).point(lambda a: 255 if a > 128 else 0)
    m = m.filter(ImageFilter.GaussianBlur(0.8))
    return m.crop((pad, pad, pad + W, pad + H))


def solid(W, H, color):
    return Image.new("RGBA", (W, H), color + (255,))


def jelly(canvas, cells, x, y, cell, color, ice=0, axis="BOTH"):
    """Glossy 3D jelly block like the game: side wall, smooth top, bevel, gloss band and sparkles."""
    rows = max(r for r, _ in cells) + 1
    cols = max(c for _, c in cells) + 1
    depth = int(cell * 0.14)
    W, H = int(cols * cell) + 2, int(rows * cell) + 2 + depth
    top = block_mask(cells, cell, (W, H - depth))
    mask = Image.new("L", (W, H), 0)
    mask.paste(top, (0, 0))
    wall_mask = Image.new("L", (W, H), 0)
    wall_mask.paste(top, (0, depth))
    # contact shadow
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sh.putalpha(wall_mask.point(lambda a: int(a * 0.35)))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(cell * 0.05)), (int(x + cell * 0.04), int(y + cell * 0.06)))
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # side wall
    wall = vgrad(W, H, lerp(color, (0, 0, 0), 0.38), lerp(color, (0, 0, 0), 0.6)).convert("RGBA")
    out.paste(wall, (0, 0), wall_mask)
    # top face: diagonal gradient
    face = Image.new("RGBA", (W, H))
    fp = face.load()
    hi, lo = lerp(color, (255, 255, 255), 0.32), lerp(color, (0, 0, 0), 0.12)
    for yy in range(H):
        for xx in range(0, W, 2):
            t = (xx / max(1, W) + yy / max(1, H - depth)) / 2
            c3 = lerp(hi, color, min(1, t * 2)) if t < 0.5 else lerp(color, lo, (t - 0.5) * 2)
            fp[xx, yy] = c3 + (255,)
            if xx + 1 < W:
                fp[xx + 1, yy] = c3 + (255,)
    # gloss band on the upper part
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gl)
    band = cell * 0.75
    for yy in range(int(band)):
        k = 1 - yy / band
        gd.line([(0, yy), (W, yy)], fill=(255, 255, 255, int(82 * k * k)))
    face.alpha_composite(gl)
    if ice:
        face.alpha_composite(Image.new("RGBA", (W, H), (224, 242, 254, 190)))
    out.paste(face, (0, 0), mask)
    # inner bevel: light rim on top, darker at the bottom
    edge = mask.filter(ImageFilter.MinFilter(max(3, int(cell * 0.08)) | 1))
    rim = ImageChops.subtract(mask, edge)
    rim_img = vgrad(W, H, (255, 255, 255), (40, 20, 60)).convert("RGBA")
    rim_img.putalpha(rim.point(lambda a: int(a * 0.45)))
    out.alpha_composite(rim_img)
    canvas.alpha_composite(out, (int(x), int(y)))
    d = ImageDraw.Draw(canvas)
    # sparkle on the first cell
    r0, c0 = min(cells)
    sx, sy = x + c0 * cell + cell * 0.25, y + r0 * cell + cell * 0.24
    d.ellipse([sx - cell * 0.05, sy - cell * 0.05, sx + cell * 0.05, sy + cell * 0.05], fill=(255, 255, 255, 235))
    d.ellipse([sx + cell * 0.08, sy - cell * 0.09, sx + cell * 0.13, sy - cell * 0.04], fill=(255, 255, 255, 160))
    if ice:
        d.text((x + cols * cell / 2, y + rows * cell / 2), str(ice), font=font(int(cell * 0.42)), fill=(3, 105, 161), anchor="mm")
    if axis != "BOTH":
        cx, cy, L = x + cols * cell / 2, y + rows * cell / 2, cell * 0.3
        w = (255, 255, 255, 220)
        sw = max(2, int(cell * 0.07))
        if axis == "HORIZONTAL":
            d.line([(cx - L, cy), (cx + L, cy)], fill=w, width=sw)
        else:
            d.line([(cx, cy - L), (cx, cy + L)], fill=w, width=sw)


def gate_rect(g, ox, oy, cell, rows, cols, frame):
    pad = cell * 0.1
    th = frame * 0.72
    ins = (frame - th) / 2
    s, st, ln = g["side"], g["start"], g["len"]
    if s == "TOP":
        return (ox + st * cell + pad, oy - frame + ins, ox + (st + ln) * cell - pad, oy - ins)
    if s == "BOTTOM":
        return (ox + st * cell + pad, oy + rows * cell + ins, ox + (st + ln) * cell - pad, oy + rows * cell + frame - ins)
    if s == "LEFT":
        return (ox - frame + ins, oy + st * cell + pad, ox - ins, oy + (st + ln) * cell - pad)
    return (ox + cols * cell + ins, oy + st * cell + pad, ox + cols * cell + frame - ins, oy + (st + ln) * cell - pad)


def board_image(level, width):
    """Flat board seen from above, like the game: slab, frame, recessed well, gates set into the frame."""
    rows, cols = level["rows"], level["cols"]
    cell = width / (cols + 0.84)
    frame = cell * 0.42
    slab = frame * 0.55
    W, H = int(width), int(cell * rows + frame * 2 + slab)
    bh = cell * rows + frame * 2
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rad = frame * 1.4
    d.rounded_rectangle([0, slab, W - 1, bh + slab - 1], radius=rad, fill=(20, 11, 48, 255), outline=(196, 181, 253, 50))
    d.rounded_rectangle([0, 0, W - 1, bh - 1], radius=rad, fill=(42, 30, 92, 255), outline=(255, 255, 255, 70), width=max(1, int(frame * 0.06)))
    ox = oy = frame
    d.rounded_rectangle([ox - cell * 0.06, oy - cell * 0.06, ox + cols * cell + cell * 0.06, oy + rows * cell + cell * 0.06], radius=cell * 0.24, fill=(11, 6, 32, 255))
    for r in range(rows):
        for c in range(cols):
            m = cell * 0.07
            d.rounded_rectangle([ox + c * cell + m, oy + r * cell + m, ox + (c + 1) * cell - m, oy + (r + 1) * cell - m], radius=cell * 0.18, fill=(34, 25, 74, 255))
    for g in level["gates"]:
        col = BLOCKS[g["color"]]
        x0, y0, x1, y1 = gate_rect(g, ox, oy, cell, rows, cols, frame)
        sp = frame * 0.07
        th = min(x1 - x0, y1 - y0)
        d.rounded_rectangle([x0 - sp, y0 - sp, x1 + sp, y1 + sp], radius=th / 2 + sp, fill=(11, 6, 32, 255))
        d.rounded_rectangle([x0, y0, x1, y1], radius=th / 2, fill=lerp(col, (255, 255, 255), 0.12) + (255,))
        d.rounded_rectangle([x0, y0, x1, y0 + (y1 - y0) * 0.45], radius=th / 2, fill=lerp(col, (255, 255, 255), 0.45) + (255,))
    for (r, c) in level["stones"]:
        m = cell * 0.06
        d.rounded_rectangle([ox + c * cell + m, oy + r * cell + m + cell * 0.12, ox + (c + 1) * cell - m, oy + (r + 1) * cell - m + cell * 0.12], radius=cell * 0.2, fill=(31, 41, 55, 255))
        d.rounded_rectangle([ox + c * cell + m, oy + r * cell + m, ox + (c + 1) * cell - m, oy + (r + 1) * cell - m], radius=cell * 0.2, fill=(107, 114, 128, 255))
    for b in sorted(level["blocks"], key=lambda b: b["r"] + max(r for r, _ in b["cells"])):
        jelly(img, b["cells"], ox + b["c"] * cell, oy + b["r"] * cell, cell, BLOCKS[b["color"]], b["ice"], b["axis"])
    return img


def font(size):
    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)
    except Exception:
        return ImageFont.load_default()


def logo_cluster(canvas, cx, cy, cell, gate=True):
    o = (cx - cell * 1.5, cy - cell * 1.75)
    if gate:
        # The cyan bar below shows the idea: each block leaves through the gate of its color.
        d = ImageDraw.Draw(canvas)
        gx0, gx1 = o[0] + cell * 0.12, o[0] + 2 * cell - cell * 0.12
        gy = o[1] + 3 * cell + cell * 0.32
        th = cell * 0.3
        glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).rounded_rectangle([gx0 - th, gy - th, gx1 + th, gy + th * 2], radius=th, fill=BLOCKS[1] + (150,))
        canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(th * 0.8)))
        d.rounded_rectangle([gx0, gy, gx1, gy + th], radius=th / 2, fill=lerp(BLOCKS[1], (255, 255, 255), 0.15) + (255,))
        d.rounded_rectangle([gx0, gy, gx1, gy + th * 0.45], radius=th / 2, fill=lerp(BLOCKS[1], (255, 255, 255), 0.5) + (255,))
        for k in (0.33, 0.66):
            cxk = gx0 + (gx1 - gx0) * k
            d.line([(cxk - th * 0.3, gy + th * 0.3), (cxk, gy + th * 0.62), (cxk + th * 0.3, gy + th * 0.3)], fill=(255, 255, 255, 240), width=max(1, int(th * 0.14)))
    jelly(canvas, [[0, 0], [0, 1], [1, 0]], o[0], o[1], cell, BLOCKS[2])
    jelly(canvas, [[0, 0], [0, 1]], o[0], o[1] + 2 * cell, cell, BLOCKS[1])
    jelly(canvas, [[0, 0]], o[0] + 2 * cell, o[1], cell, BLOCKS[7])
    jelly(canvas, [[0, 0]], o[0] + 2 * cell, o[1] + cell, cell, BLOCKS[3])
    jelly(canvas, [[0, 0]], o[0] + 2 * cell, o[1] + 2 * cell, cell, BLOCKS[6])


def icon(size, round_mask=False):
    S = size * 4
    img = background(S, S)
    logo_cluster(img, S / 2, S / 2, S * 0.21)
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if round_mask:
        md.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.22, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def foreground(size):
    S = size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    logo_cluster(img, S / 2, S / 2, S * 0.135)
    return img.resize((size, size), Image.LANCZOS)


for name, f in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    d = os.path.join(RES, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    icon(int(48 * f)).save(os.path.join(d, "ic_launcher.png"))
    icon(int(48 * f), True).save(os.path.join(d, "ic_launcher_round.png"))
    foreground(int(108 * f)).save(os.path.join(d, "ic_launcher_foreground.png"))
    background(int(108 * f) * 2, int(108 * f) * 2).resize((int(108 * f), int(108 * f))).save(os.path.join(d, "ic_launcher_background.png"))

big = background(2048, 2048)
logo_cluster(big, 1024, 1024, 2048 * 0.21)
big.convert("RGB").resize((512, 512), Image.LANCZOS).save(os.path.join(STORE, "icon-512.png"))

# Feature graphic + level preview
levels = json.load(open(sys.argv[2])) if len(sys.argv) > 2 else None
W, H = 2048, 1000
fg = background(W, H)
if levels:
    lv = levels[-1]
    # Fit the (portrait) board to ~92% of the banner height.
    width = int(H * 0.92 / (lv["rows"] + 0.84 + 0.23) * (lv["cols"] + 0.84))
    b = board_image(lv, width)
    fg.alpha_composite(b, (1500 - width // 2, (H - b.height) // 2))
t = Image.new("RGBA", (W, H), (0, 0, 0, 0))
td = ImageDraw.Draw(t)
td.text((120, 300), "PRISMA", font=font(190), fill=(255, 255, 255, 255))
td.text((120, 520), "JAM", font=font(190), fill=(255, 255, 255, 255))
grad = Image.new("RGBA", (W, H))
gp = Image.new("RGB", (W, 1))
for x in range(W):
    gp.putpixel((x, 0), lerp((34, 211, 238), (232, 121, 249), min(1, x / (W * 0.45))))
grad = gp.resize((W, H)).convert("RGBA")
glow = Image.new("RGBA", (W, H), (232, 121, 249, 0))
glow.putalpha(t.split()[3].filter(ImageFilter.GaussianBlur(24)).point(lambda a: int(a * 0.7)))
fg.alpha_composite(glow)
fg.alpha_composite(Image.composite(grad, Image.new("RGBA", (W, H), (0, 0, 0, 0)), t.split()[3]))
fg.convert("RGB").resize((1024, 500), Image.LANCZOS).save(os.path.join(STORE, "feature-graphic-1024x500.png"))

if levels:
    # Phone-like preview of a few levels side by side
    tiles = []
    for lv in levels:
        ph = background(720, 1280)
        bw = min(656, int(960 / (lv["rows"] + 1.07) * (lv["cols"] + 0.84)))
        bimg = board_image(lv, bw)
        ph.alpha_composite(bimg, ((720 - bw) // 2, 290 + (960 - bimg.height) // 2))
        pd = ImageDraw.Draw(ph)
        pd.text((360, 140), f"Fase {lv['n']}", font=font(56), fill=(240, 171, 252), anchor="mm")
        pd.rounded_rectangle([60, 200, 660, 250], radius=25, fill=(52, 211, 153))
        pd.text((600, 225), f"{lv['time'] // 60}:{lv['time'] % 60:02d}", font=font(34), fill=(255, 255, 255), anchor="mm")
        tiles.append(ph.convert("RGB").resize((360, 640)))
    sheet = Image.new("RGB", (len(tiles) * 370 - 10, 640), (255, 0, 255))
    for i, ti in enumerate(tiles):
        sheet.paste(ti, (i * 370, 0))
    sheet.save(os.path.join(STORE, "preview-levels.jpg"), quality=88)
print("done")
