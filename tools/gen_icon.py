#!/usr/bin/env python3
"""Generate the Bori Keyboard launcher icon layers as Android vectors (and SVG previews).

Same family as the other Bori apps (Bori habit tracker, Bori - Code): celeste field from the
Puerto Rico flag (#4FB4F2), a red mark, white, and the coquí petroglyph in coquí blue below.
Here the mark is a small keyboard of white keys with a red swipe trail gliding across them
(the habit tracker's red heartbeat line, as a glide-typing stroke).

The coquí strokes are copied from Bori - Code's scripts/gen_bori_code_icon.py (traced off the
habit tracker's source art); here they are drawn as plain solid strokes, like the habit tracker.

    python3 tools/gen_icon.py                 # write the drawables
    python3 tools/gen_icon.py --preview DIR   # SVGs only, nothing written to res/

Launcher layers use a 108-unit canvas whose guaranteed-visible area is the circle of radius 33
around (54,54); the script fails if anything pokes out of it.
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")

COLORS = {"bg": "#4FB4F2", "key": "#FFFFFF", "trail": "#D10002", "coqui": "#0E4F8A", "mono": "#000000"}

COQUI_STROKES = [
    [(491, 813), (481, 824), (484, 834), (513, 836)],
    [(513, 836), (540, 834), (548, 829)],
    [(513, 836), (513, 826)],
    [(513, 836), (511, 852), (511, 865)],
    [(511, 865), (497, 852), (480, 855), (472, 866), (482, 877), (495, 882), (494, 890)],
    [(511, 865), (527, 852), (545, 856), (552, 868), (546, 878), (537, 884), (538, 890)],
]
COQUI_DOTS = [(491, 812), (548, 829), (506, 817), (526, 821)]  # hands, eyes
SAFE_R = 33.0

# Keyboard block
KB_LEFT, KB_RIGHT, KB_TOP = 30.0, 78.0, 31.5
KEY_GAP, ROW_GAP, KEY_H, KEY_R = 1.7, 1.9, 6.0, 1.3
COLS = 5
# Row layouts: (offset in key widths, number of keys); last row = spacebar spanning columns 1..3.
ROWS = [(0.0, 5), (0.5, 4), (0.0, 5)]
TRAIL_W = 3.0
# Coquí under the keyboard
COQUI_CX, COQUI_TOP, COQUI_H, COQUI_W = 54.0, 71.0, 14.0, 1.25


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def key_w():
    return (KB_RIGHT - KB_LEFT - KEY_GAP * (COLS - 1)) / COLS


def keys():
    """[(x, y, w, h)] for every key, letters then the spacebar."""
    kw = key_w()
    out = []
    y = KB_TOP
    for off, n in ROWS:
        for i in range(n):
            x = KB_LEFT + (off + i) * (kw + KEY_GAP)
            out.append((x, y, kw, KEY_H))
        y += KEY_H + ROW_GAP
    # Spacebar: columns 1..3.
    x0 = KB_LEFT + 1 * (kw + KEY_GAP)
    x1 = KB_LEFT + 3 * (kw + KEY_GAP) + kw
    out.append((x0, y, x1 - x0, KEY_H))
    return out


def key_center(row, col):
    kw = key_w()
    off = ROWS[row][0]
    x = KB_LEFT + (off + col) * (kw + KEY_GAP) + kw / 2
    y = KB_TOP + row * (KEY_H + ROW_GAP) + KEY_H / 2
    return x, y


def trail_points():
    """A glide over a few keys: down-left start, up over the top row, ending with the finger."""
    return [key_center(2, 0), key_center(0, 1), key_center(1, 2), key_center(0, 3), key_center(1, 3)]


def catmull(points, steps=10):
    """Smooth path through the points (Catmull-Rom as cubic Béziers)."""
    p = [points[0]] + points + [points[-1]]
    d = f"M{f(p[1][0])},{f(p[1][1])}"
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f" C{f(c1[0])},{f(c1[1])} {f(c2[0])},{f(c2[1])} {f(p2[0])},{f(p2[1])}"
    return d


def round_rect(x, y, w, h, r):
    return (f"M{f(x + r)},{f(y)} h{f(w - 2 * r)} a{f(r)},{f(r)} 0 0,1 {f(r)},{f(r)} v{f(h - 2 * r)} "
            f"a{f(r)},{f(r)} 0 0,1 {f(-r)},{f(r)} h{f(-(w - 2 * r))} a{f(r)},{f(r)} 0 0,1 {f(-r)},{f(-r)} "
            f"v{f(-(h - 2 * r))} a{f(r)},{f(r)} 0 0,1 {f(r)},{f(-r)} z")


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 z"


def coqui():
    xs = [x for st in COQUI_STROKES for x, _ in st]
    ys = [y for st in COQUI_STROKES for _, y in st]
    k = COQUI_H / (max(ys) - min(ys))
    ox = (min(xs) + max(xs)) / 2
    m = lambda x, y: (COQUI_CX + (x - ox) * k, COQUI_TOP + (y - min(ys)) * k)
    strokes = []
    for st in COQUI_STROKES:
        pts = [m(*p) for p in st]
        strokes.append("M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts))
    dots = []
    for i, p in enumerate(COQUI_DOTS):
        x, y = m(*p)
        dots.append(circle(x, y, 1.15 if i < 2 else 0.6))
    return " ".join(strokes), " ".join(dots), [m(x, y) for st in COQUI_STROKES for x, y in st]


def check_safe():
    worst = 0.0
    for x, y, w, h in keys():
        for cx, cy in ((x, y), (x + w, y), (x, y + h), (x + w, y + h)):
            worst = max(worst, math.hypot(cx - 54, cy - 54))
    _, _, pts = coqui()
    worst = max(worst, max(math.hypot(x - 54, y - 54) + COQUI_W for x, y in pts))
    if worst > SAFE_R:
        sys.exit(f"art leaves the safe circle: {worst:.2f} > {SAFE_R}")
    kb_bottom = keys()[-1][1] + KEY_H
    if min(y for _, y in pts) - COQUI_W - kb_bottom < 1.5:
        sys.exit("coquí touches the keyboard")
    return worst


def layer(key_color, trail_color, coqui_color, mono=False):
    """Android vector body. In the one-colour layer the keys are outlines so the trail shows."""
    lines = ["    <!-- Keys -->\n"]
    for x, y, w, h in keys():
        d = round_rect(x, y, w, h, KEY_R)
        if mono:
            lines.append(f'    <path android:strokeColor="{key_color}" android:strokeWidth="0.9" android:pathData="{d}" />\n')
        else:
            lines.append(f'    <path android:fillColor="{key_color}" android:pathData="{d}" />\n')
    lines.append("    <!-- Swipe trail -->\n")
    lines.append(f'    <path android:strokeColor="{trail_color}" android:strokeWidth="{f(TRAIL_W)}" '
                 f'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{catmull(trail_points())}" />\n')
    ex, ey = trail_points()[-1]
    lines.append(f'    <path android:fillColor="{trail_color}" android:pathData="{circle(ex, ey, TRAIL_W * 0.95)}" />\n')
    s, d, _ = coqui()
    lines.append("    <!-- Coquí petroglyph -->\n")
    lines.append(f'    <path android:strokeColor="{coqui_color}" android:strokeWidth="{f(COQUI_W)}" '
                 f'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{s}" />\n')
    lines.append(f'    <path android:fillColor="{coqui_color}" android:pathData="{d}" />\n')
    return "".join(lines)


def vector(body, comment):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!--\n  {comment}\n  Generated by tools/gen_icon.py — edit the script, not this file.\n-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + body + "</vector>\n"
    )


def svg(size=512, mono=False, round_mask=False):
    c = COLORS
    kc, tc, cc = (("#202020",) * 3) if mono else (c["key"], c["trail"], c["coqui"])
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">']
    if round_mask:
        parts.append('<defs><clipPath id="m"><circle cx="54" cy="54" r="40"/></clipPath></defs><g clip-path="url(#m)">')
    if not mono:
        parts.append(f'<rect width="108" height="108" fill="{c["bg"]}"/>')
    for x, y, w, h in keys():
        d = round_rect(x, y, w, h, KEY_R)
        parts.append(f'<path d="{d}" fill="none" stroke="{kc}" stroke-width="0.9"/>' if mono else f'<path d="{d}" fill="{kc}"/>')
    parts.append(f'<path d="{catmull(trail_points())}" fill="none" stroke="{tc}" stroke-width="{f(TRAIL_W)}" '
                 'stroke-linecap="round" stroke-linejoin="round"/>')
    ex, ey = trail_points()[-1]
    parts.append(f'<path d="{circle(ex, ey, TRAIL_W * 0.95)}" fill="{tc}"/>')
    s, d, _ = coqui()
    parts.append(f'<path d="{s}" fill="none" stroke="{cc}" stroke-width="{f(COQUI_W)}" stroke-linecap="round" stroke-linejoin="round"/>')
    parts.append(f'<path d="{d}" fill="{cc}"/>')
    if round_mask:
        parts.append("</g>")
    parts.append("</svg>")
    return "\n".join(parts)


def main():
    args = sys.argv[1:]
    worst = check_safe()
    if "--preview" in args:
        out = args[args.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        open(os.path.join(out, "icon.svg"), "w").write(svg())
        open(os.path.join(out, "icon-round.svg"), "w").write(svg(round_mask=True))
        open(os.path.join(out, "icon-mono.svg"), "w").write(svg(mono=True))
        print(f"furthest point {worst:.2f} of {SAFE_R}; previews in {out}")
        return
    draw = os.path.join(RES, "drawable")
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(draw, exist_ok=True)
    os.makedirs(anydpi, exist_ok=True)
    w = lambda d, n, t: open(os.path.join(d, n), "w").write(t)
    w(draw, "ic_launcher_background.xml", vector(
        f'    <path android:fillColor="{COLORS["bg"]}" android:pathData="M0,0 h108 v108 h-108 z" />\n',
        "Bori Keyboard launcher background: celeste blue from the flag, as on the other Bori apps."))
    w(draw, "ic_launcher_foreground.xml", vector(
        layer(COLORS["key"], COLORS["trail"], COLORS["coqui"]),
        "Bori Keyboard launcher foreground: white keys, a red swipe trail, the coquí below."))
    w(draw, "ic_launcher_monochrome.xml", vector(
        layer(COLORS["mono"], COLORS["mono"], COLORS["mono"], mono=True),
        "Bori Keyboard themed-icon layer: the same mark in one colour; the launcher tints it."))
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Bori Keyboard launcher icon (tools/gen_icon.py). -->\n"
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
        "</adaptive-icon>\n"
    )
    w(anydpi, "ic_launcher.xml", adaptive)
    w(anydpi, "ic_launcher_round.xml", adaptive)
    print(f"furthest point {worst:.2f} of {SAFE_R}; drawables written")


if __name__ == "__main__":
    main()
