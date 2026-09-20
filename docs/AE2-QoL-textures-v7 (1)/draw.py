#!/usr/bin/env python3
"""AE2-QoL v6 — per-face textures. Side=infinity dual, top=v5, unique fronts."""
from PIL import Image
from pathlib import Path
import io, base64, zipfile

ROOT = Path("/home/user/texture-v7")
BLK = ROOT / "assets/ae2_qof/textures/blocks/machine"
ITM = ROOT / "assets/ae2_qof/textures/items"
PREV = ROOT / "previews"
BLK.mkdir(parents=True, exist_ok=True)
ITM.mkdir(parents=True, exist_ok=True)
PREV.mkdir(parents=True, exist_ok=True)

OUTR, HI = (214, 216, 228), (238, 240, 252)
P1, P2, P3, P4 = (198, 210, 250), (176, 190, 246), (154, 172, 238), (138, 158, 226)
CR, CAP, CAPD = (80, 92, 110), (172, 244, 244), (148, 220, 222)
ICE, ICEH, ICEM = (212, 230, 232), (228, 242, 244), (186, 204, 208)
PAD, TICK, WELL, WEL2, CORE, COR2 = (122, 128, 132), (68, 72, 76), (70, 74, 78), (46, 48, 50), (186, 210, 212), (154, 168, 170)

AE_INK, AE_W, AE_L, AE_G, AE_M = (35, 38, 43), (234, 235, 236), (217, 219, 220), (198, 201, 203), (142, 147, 152)
AE_CY, AE_SH, AE_DP, AE_SC, AE_SL = (0, 179, 204), (23, 182, 206), (14, 126, 147), (12, 48, 56), (8, 32, 38)


def nb():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 255))


def ni():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def P(im, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c if len(c) == 4 else (*c, a))


def infinity_dual(im, tint=None, center=None):
    for y in range(16):
        for x in range(16):
            P(im, x, y, OUTR)
    for y in range(1, 15):
        for x in range(1, 15):
            P(im, x, y, HI)

    def quad(x0, y0, fx, fy):
        for yy in range(6):
            for xx in range(6):
                d = xx + yy
                c = P1 if d <= 2 else P2 if d <= 5 else P3 if d <= 8 else P4
                x = x0 + (5 - xx if fx else xx)
                y = y0 + (5 - yy if fy else yy)
                if tint:
                    c = tuple(max(0, min(255, (c[i] * 5 + tint[i]) // 6)) for i in range(3))
                P(im, x, y, c)
    quad(1, 1, 0, 0); quad(9, 1, 1, 0); quad(1, 9, 0, 1); quad(9, 9, 1, 1)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        P(im, x, y, HI)
        P(im, x + (1 if x == 2 else -1), y, P1)
        P(im, x, y + (1 if y == 2 else -1), P1)
    for y in range(1, 15):
        P(im, 7, y, CR); P(im, 8, y, CR)
    for x in range(1, 15):
        P(im, x, 7, CR); P(im, x, 8, CR)
    for x, y, c in [
        (7, 0, CAP), (8, 0, CAP), (7, 15, CAPD), (8, 15, CAPD),
        (0, 7, CAP), (0, 8, CAP), (15, 7, CAPD), (15, 8, CAPD),
        (1, 7, CAP), (1, 8, CAPD), (14, 7, CAP), (14, 8, CAPD),
    ]:
        P(im, x, y, c)
    if center:
        P(im, 7, 7, center); P(im, 8, 7, center)
        P(im, 7, 8, center); P(im, 8, 8, tuple(max(0, v - 28) for v in center))
    return im


def wireless_casing(im, well_fill=None, core=None):
    for y in range(16):
        for x in range(16):
            d = x + y
            P(im, x, y, ICEH if d <= 10 else (ICE if d <= 20 else ICEM))
    for x in range(6, 10):
        P(im, x, 0, PAD); P(im, x, 15, PAD); P(im, 0, x, PAD); P(im, 15, x, PAD)
    for x in range(7, 9):
        P(im, x, 1, TICK); P(im, x, 14, TICK); P(im, 1, x, TICK); P(im, 14, x, TICK)
        P(im, x, 0, TICK); P(im, x, 15, TICK); P(im, 0, x, TICK); P(im, 15, x, TICK)
    cx, cy = 7.5, 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            r2 = (x - cx) ** 2 + (y - cy) ** 2
            if r2 > 28:
                continue
            if r2 > 20:
                P(im, x, y, PAD)
            else:
                P(im, x, y, well_fill if well_fill else (WEL2 if r2 < 6 else WELL))
    if core is None and well_fill is None:
        for y in range(6, 10):
            for x in range(6, 10):
                P(im, x, y, COR2)
        P(im, 7, 7, CORE); P(im, 8, 7, CORE); P(im, 7, 8, CORE); P(im, 8, 8, COR2)
    elif core:
        hi = core[0] if isinstance(core, tuple) and len(core) == 2 else core
        for y in range(6, 10):
            for x in range(6, 10):
                P(im, x, y, hi)
    return im


def bolt(im, hi, mid, lo):
    """Original lightning."""
    for x, y, c in [
        (9, 3, hi), (10, 3, mid),
        (8, 4, hi),
        (7, 5, mid), (8, 5, hi), (9, 5, mid), (10, 5, lo),
        (6, 6, mid),
        (7, 7, hi), (8, 7, mid), (9, 7, hi),
        (9, 8, mid),
        (7, 9, lo), (8, 9, hi),
        (6, 10, mid), (7, 10, mid),
        (5, 11, lo), (6, 11, mid),
    ]:
        P(im, x, y, c)


def burst(im, hi, mid, lo):
    """Output counterpart of lightning — outward 3-prong burst."""
    for x, y, c in [
        (8, 3, hi),
        (6, 4, mid), (8, 4, hi), (10, 4, mid),
        (5, 5, lo), (8, 5, mid), (11, 5, lo),
        (4, 6, lo), (7, 6, hi), (8, 6, hi), (9, 6, hi), (12, 6, lo),
        (8, 7, mid),
        (7, 8, mid), (9, 8, mid),
        (6, 9, lo), (8, 9, hi), (10, 9, lo),
        (5, 10, lo), (8, 10, mid), (11, 10, lo),
        (8, 11, lo),
    ]:
        P(im, x, y, c)


def framed(front):
    """Nest current front into infinity-dual outer frame (same as side/bottom)."""
    im = infinity_dual(nb())
    for y in range(3, 13):
        for x in range(3, 13):
            P(im, x, y, front.getpixel((x, y))[:3])
    for i in range(3, 13):
        P(im, i, 2, (28, 32, 40))
        P(im, i, 13, (58, 64, 76))
        P(im, 2, i, (28, 32, 40))
        P(im, 13, i, (58, 64, 76))
    P(im, 2, 2, (22, 24, 30))
    P(im, 13, 2, (40, 44, 52))
    P(im, 2, 13, (40, 44, 52))
    P(im, 13, 13, (58, 64, 76))
    return im


def hatch_frame(im, border_hi, border_lo):
    """Shared front for the four adaptive hatches — dark well, 1px colored rim."""
    for y in range(16):
        for x in range(16):
            t = y / 15
            b = tuple(int(border_hi[i] * (1 - t) + border_lo[i] * t) for i in range(3))
            P(im, x, y, b)
    for y in range(1, 15):
        for x in range(1, 15):
            P(im, x, y, (4, 6, 12))
    return im


# ---- fronts ----
def front_a1():
    """OP-hatch language, drawn in the 10×10 well (3–12)."""
    im = nb()
    Y, Y2, SCR = (230, 220, 20), (180, 186, 22), (0, 8, 48)
    for y in range(3, 13):
        for x in range(3, 13):
            P(im, x, y, (20, 20, 24))
    for y in range(3, 13):
        for x in range(3, 5):
            P(im, x, y, Y if 5 <= y <= 8 else Y2)
    for y in range(3, 8):
        for x in range(6, 13):
            P(im, x, y, SCR)
    P(im, 6, 3, (0, 220, 220)); P(im, 7, 3, (0, 220, 220))
    P(im, 11, 5, (0, 220, 220)); P(im, 12, 5, (0, 180, 180))
    P(im, 7, 8, (220, 32, 32)); P(im, 8, 8, (180, 16, 16))
    P(im, 9, 8, (32, 220, 48)); P(im, 10, 8, (16, 160, 32))
    P(im, 11, 8, (32, 64, 220)); P(im, 12, 8, (24, 40, 180))
    for x0, col in ((6, (190, 190, 190)), (8, (150, 150, 150)), (10, (190, 190, 190))):
        P(im, x0, 10, col); P(im, x0 + 1, 10, col)
        P(im, x0, 11, col); P(im, x0 + 1, 11, col)
    return im


def front_a2():
    """Thread-hatch language: blue rim, hash, cyan glow. Original pixels."""
    im = nb()
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            P(im, x, y, (int(10 + 8 * (1 - t)), int(80 + 40 * (1 - y / 15)), int(220 - 80 * t)))
    for y in range(1, 15):
        for x in range(1, 15):
            P(im, x, y, (0, 0, 0))
    BL = (70, 140, 230)
    for x in range(3, 13):
        P(im, x, 5, BL); P(im, x, 9, BL)
    for y in range(3, 13):
        P(im, 5, y, BL); P(im, 9, y, BL)
    P(im, 3, 3, (0, 40, 160)); P(im, 4, 3, (0, 40, 160))
    P(im, 3, 4, (0, 40, 160)); P(im, 4, 4, BL)
    P(im, 11, 3, BL); P(im, 12, 4, (0, 32, 80))
    GL = (120, 230, 255)
    P(im, 8, 8, GL); P(im, 11, 8, GL)
    P(im, 8, 11, GL); P(im, 11, 11, GL)
    P(im, 9, 8, BL); P(im, 8, 9, BL)
    return im


def front_hatch(kind, hi, mid, lo, rim_hi, rim_lo):
    im = hatch_frame(nb(), rim_hi, rim_lo)
    if kind == "in":
        bolt(im, hi, mid, lo)
    else:
        burst(im, hi, mid, lo)
    return im


def iridescent_field(im, drain=False):
    """Original oil-slick: large color regions, 图2 language. Not a checker."""
    def lerp(a, b, t):
        t = max(0.0, min(1.0, t))
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    gold, mag, grn, deep = (236, 196, 110), (168, 72, 128), (96, 176, 108), (42, 20, 56)
    for y in range(16):
        for x in range(16):
            u, v = x / 15, y / 15
            top = lerp(gold, grn, u * 0.65)
            bot = lerp(mag, deep, u)
            c = lerp(top, bot, v * 0.85 + 0.1)
            if (x + 2 * y) % 6 == 0:
                c = tuple(min(255, c[i] + 14) for i in range(3))
            if drain:
                r2 = (x - 7.5) ** 2 + (y - 7.5) ** 2
                if r2 < 6:
                    c = (8, 10, 14)
                elif r2 < 14:
                    c = tuple(c[i] * 2 // 5 for i in range(3))
                else:
                    c = tuple(c[i] * 3 // 5 for i in range(3))
            P(im, x, y, c)
    if drain:
        for y in range(6, 10):
            for x in range(6, 10):
                P(im, x, y, (6, 8, 12))
        P(im, 7, 7, (32, 36, 44)); P(im, 8, 8, (18, 20, 26))
    else:
        P(im, 2, 1, (248, 228, 150)); P(im, 3, 2, (240, 210, 130))
        P(im, 12, 6, (170, 220, 140))
    return im


def front_a7():
    return iridescent_field(nb(), drain=False)


def front_a8():
    return iridescent_field(nb(), drain=True)


def b1():
    im = ni()
    P(im, 12, 0, AE_INK); P(im, 12, 1, AE_M); P(im, 13, 0, AE_CY); P(im, 13, 1, AE_INK)
    for x in range(2, 14):
        P(im, x, 2, AE_INK); P(im, x, 14, AE_INK)
    for y in range(3, 14):
        P(im, 1, y, AE_INK); P(im, 14, y, AE_INK)
    P(im, 2, 3, AE_INK); P(im, 13, 3, AE_INK); P(im, 2, 13, AE_INK); P(im, 13, 13, AE_INK)
    for y in range(3, 14):
        for x in range(2, 14):
            if im.getpixel((x, y))[3] == 0:
                P(im, x, y, AE_G)
    for x in range(3, 13):
        P(im, x, 3, AE_W); P(im, x, 13, AE_M)
    for y in range(4, 13):
        P(im, 2, y, AE_L); P(im, 13, y, AE_M)
    for y in range(5, 12):
        for x in range(3, 13):
            P(im, x, y, AE_SC)
    for x in range(3, 13):
        P(im, x, 5, AE_DP)
    for ox, oy in ((4, 6), (7, 6), (4, 9), (7, 9)):
        P(im, ox, oy, AE_SH); P(im, ox + 1, oy, AE_CY); P(im, ox, oy + 1, AE_CY); P(im, ox + 1, oy + 1, AE_DP)
    for y in range(6, 12):
        P(im, 9, y, AE_SL)
    for y, col in ((6, AE_CY), (8, AE_SH), (10, AE_DP)):
        P(im, 10, y, col); P(im, 11, y, col); P(im, 12, y, col)
    for x in range(5, 11):
        P(im, x, 12, AE_M)
    P(im, 7, 12, AE_INK); P(im, 8, 12, AE_INK)
    P(im, 14, 11, AE_DP); P(im, 15, 12, AE_CY); P(im, 15, 13, AE_SH); P(im, 14, 14, AE_DP)
    return im


def b2():
    im = ni()
    for y in range(1, 15):
        for x in range(1, 15):
            P(im, x, y, AE_INK)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        P(im, x, y, (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            P(im, x, y, AE_G)
    for x in range(2, 14):
        P(im, x, 2, AE_W); P(im, x, 13, AE_M)
    for y in range(3, 13):
        P(im, 2, y, AE_L); P(im, 13, y, AE_M)
    for y in range(4, 12):
        for x in range(3, 13):
            P(im, x, y, AE_SC)
    for x in range(3, 13):
        P(im, x, 4, AE_DP)
    for ox, oy in ((4, 5), (7, 5), (4, 8), (7, 8)):
        P(im, ox, oy, AE_SH); P(im, ox + 1, oy, AE_CY); P(im, ox, oy + 1, AE_CY); P(im, ox + 1, oy + 1, AE_DP)
    for y in range(5, 11):
        P(im, 9, y, AE_SL)
    for y, col in ((5, AE_CY), (7, AE_SH), (9, AE_DP)):
        P(im, 10, y, col); P(im, 11, y, col); P(im, 12, y, col)
    for x in range(4, 12):
        P(im, x, 12, AE_M)
    P(im, 7, 12, AE_CY); P(im, 8, 12, AE_SH)
    P(im, 2, 2, AE_W); P(im, 13, 2, AE_L)
    return im


MACHINES = [
    ("universal_maintenance_hatch", "A1", "万能维护仓",
     front_a2,
     lambda: infinity_dual(nb(), tint=(40, 180, 180), center=(40, 230, 230)),
     "正面：线程修改仓语言（蓝框+#格+青辉）。顶：十字双仓。侧：图一共用。"),
    ("adaptive_net_terminal", "A2", "自适应电网终端",
     front_a1,
     lambda: infinity_dual(nb(), tint=(40, 80, 180), center=(40, 120, 255)),
     "正面：OP 仓语言（黄条+屏+RGB键）。顶：十字双仓。侧：图一共用。"),
    ("adaptive_net_hatch", "A3", "自适应电网输入仓",
     lambda: front_hatch("in", (160, 255, 170), (48, 220, 96), (16, 110, 48), (40, 180, 80), (8, 60, 24)),
     lambda: infinity_dual(nb(), tint=(40, 180, 70), center=(80, 230, 90)),
     "四舱统一暗井。输入：绿色闪电。"),
    ("adaptive_net_laser_hatch", "A4", "激光源仓",
     lambda: front_hatch("out", (180, 255, 252), (40, 230, 230), (16, 120, 124), (20, 180, 180), (8, 70, 80)),
     lambda: wireless_casing(nb(), well_fill=(12, 40, 44), core=((160, 255, 250), (20, 140, 150))),
     "四舱统一暗井。输出：青色外扩爆发符（闪电的成对符号）。顶：无线机箱。"),
    ("adaptive_net_dynamo_hatch", "A5", "动力仓",
     lambda: front_hatch("out", (255, 220, 120), (232, 180, 48), (160, 110, 30), (180, 140, 30), (80, 50, 10)),
     lambda: wireless_casing(nb(), well_fill=(36, 28, 12), core=((240, 200, 70), (160, 110, 30))),
     "四舱统一暗井。输出：金色外扩爆发符。顶：无线机箱。"),
    ("adaptive_net_laser_target", "A6", "激光靶仓",
     lambda: front_hatch("in", (180, 255, 252), (40, 230, 230), (16, 120, 124), (20, 140, 160), (8, 50, 70)),
     lambda: wireless_casing(nb()),
     "四舱统一暗井。输入：青色闪电。顶：无线机箱准星。"),
    ("wireless_energy_input", "A7", "无线EU输入终端",
     front_a7,
     lambda: wireless_casing(nb(), well_fill=(48, 28, 72)),
     "正面：图二虹彩能量场（原创像素）。顶：无线机箱。侧：图一共用。"),
    ("wireless_energy_output", "A8", "无线EU输出终端",
     front_a8,
     lambda: wireless_casing(nb(), well_fill=(16, 16, 18), core=((40, 42, 44), (8, 8, 10))),
     "正面：与 A7 成对的虹彩场，能量向中心暗孔汇入。"),
]


def save(im, path):
    im.save(path)
    im.resize((512, 512), Image.Resampling.NEAREST).save(PREV / (path.stem + "_512.png"))


def uri(im, size=512):
    b = io.BytesIO()
    im.resize((size, size), Image.Resampling.NEAREST).save(b, format="PNG")
    return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()


def main():
    side = infinity_dual(nb())
    save(side, BLK / "casing_side.png")
    rows = []
    for name, code, title, ffront, ftop, note in MACHINES:
        fr, tp = framed(ffront()), ftop()
        save(fr, BLK / f"{name}.png")
        save(tp, BLK / f"{name}_top.png")
        save(side, BLK / f"{name}_side.png")
        rows.append((fr, tp, side, name, code, title, note))
        print("wrote", name)
    save(b1(), ITM / "merged_terminal_wireless.png")
    save(b2(), ITM / "merged_terminal_part.png")

    # overview: 8 fronts + 8 tops
    ov = Image.new("RGBA", (8 * 256, 2 * 256), (0, 0, 0, 0))
    for i, (fr, tp, *_) in enumerate(rows):
        ov.paste(fr.resize((256, 256), Image.Resampling.NEAREST), (i * 256, 0))
        ov.paste(tp.resize((256, 256), Image.Resampling.NEAREST), (i * 256, 256))
    ov.save(ROOT / "overview.png")

    cards = []
    for fr, tp, side, name, code, title, note in rows:
        cards.append(
            f'<article><code>{name}.png</code><h2>{code} / {title}</h2>'
            f'<div class="faces"><figure><img src="{uri(fr)}"><figcaption>正面 FRONT</figcaption></figure>'
            f'<figure><img src="{uri(tp)}"><figcaption>顶面 TOP</figcaption></figure>'
            f'<figure><img src="{uri(side)}"><figcaption>侧面 SIDE</figcaption></figure></div>'
            f'<p>{note}16×16 原生网格。</p></article>'
        )
    html = f"""<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>AE2-QoL 材质 v6</title>
<style>
*{{box-sizing:border-box}}body{{margin:0;background:#0e1014;color:#d2d6d8;font:15px/1.75 system-ui,sans-serif}}
main{{max-width:1180px;margin:auto;padding:52px 28px}}h1{{font-size:34px;color:#eef6f7;margin:8px 0 0}}
.eyebrow{{color:#9db4ff;letter-spacing:2.5px;font-size:12px}}
article{{background:#161a20;border:1px solid #2c333c;padding:18px;margin:0 0 28px}}
.faces{{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}}
img{{display:block;width:100%;image-rendering:pixelated;background:#111418;background-image:conic-gradient(#1a1e24 25%,transparent 0 50%,#1a1e24 0 75%,transparent 0);background-size:16px 16px}}
figcaption{{font-size:12px;color:#9aa3a8;margin-top:6px}}p{{color:#9aa3a8}}code{{font-size:12px;color:#eef6f7;overflow-wrap:anywhere}}
.note{{border-left:3px solid #9db4ff;background:#12181c;padding:16px 18px;margin:24px 0}}
@media(max-width:700px){{.faces{{grid-template-columns:1fr 1fr}}}}
</style>
<main>
<div class="eyebrow">AE2-QOL · V6 · 分面</div>
<h1>侧面十字双仓 · 正面功能核 · 顶面上一版</h1>
<div class="note">
<strong>侧面 / 底面</strong>：图一无限输入双仓构图（<code>casing_side.png</code>，每台也有 <code>*_side.png</code>）。<br>
<strong>顶面</strong>：v5 那一套（<code>*_top.png</code>）。<br>
<strong>正面</strong>：与侧面同一套十字双仓外框，中间 10×10 凹井嵌套功能核（虹彩 / #格 / 闪电 / 爆发符 / OP 面板）。<br>
像素原创，未复制材质包。模组需分别把顶/侧/正注册到这些文件；若仍只用一张，默认 <code>{'{name}.png'}</code> 是正面。
</div>
{''.join(cards)}
<h2>物品</h2>
<p>B1/B2 保持 AE 语言，见 <code>textures/items/</code>。</p>
</main></html>"""
    (ROOT / "材质交付.html").write_text(html, encoding="utf-8")
    readme = """# AE2-QoL 材质 v6 · 分面

| 面 | 文件 | 来源 |
|---|---|---|
| 正面 FRONT | `{name}.png` | 图2/3/4 与统一舱室 |
| 顶面 TOP | `{name}_top.png` | v5 十字双仓 / 无线机箱 |
| 侧面 SIDE | `{name}_side.png` 与 `casing_side.png` | 图一无限输入双仓 |

四个自适应舱（A3 输入 / A6 输入 / A4 输出 / A5 输出）共用暗井边框；输入=闪电，输出=三叉外扩符。

模组若尚未分面注册，需要把 GT 机器的 top/side/front 分别指向这些 PNG。未做游戏内验证。
"""
    for fr, tp, side, name, code, title, note in rows:
        readme += f"\n## {name} — {title}\n{note}\n"
    (ROOT / "README.md").write_text(readme, encoding="utf-8")
    zpath = ROOT / "AE2-QoL-textures-v7.zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for f in ROOT.rglob("*"):
            if f.is_file() and f.suffix != ".zip" and "ref" not in f.parts:
                z.write(f, f.relative_to(ROOT))
    print("zip", zpath)


if __name__ == "__main__":
    main()
