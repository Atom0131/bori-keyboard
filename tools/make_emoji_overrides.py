#!/usr/bin/env python3
"""Draws Bori's own emoji overrides into tools/emoji_overrides/ (used by build_emoji_packs.py).

- 1f1f5-1f1f7.png: Puerto Rico flag with the light-blue (sky blue) triangle of the 1895 design,
  instead of the navy blue most emoji sets use. Drawn here from geometry: our own artwork.

Usage: python3 -I tools/make_emoji_overrides.py   (needs Pillow)
"""
import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "emoji_overrides")
RED = (230, 30, 43, 255)
WHITE = (255, 255, 255, 255)
SKY_BLUE = (79, 175, 230, 255)


def puerto_rico(width=144):
    ss = 4  # draw big, scale down: smooth edges
    w, h = width * ss, width * 2 // 3 * ss
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=h // 14, fill=255)
    flag = Image.new("RGBA", (w, h), WHITE)
    d = ImageDraw.Draw(flag)
    stripe = h / 5
    for i in (0, 2, 4):
        d.rectangle((0, round(i * stripe), w, round((i + 1) * stripe)), fill=RED)
    tip = h * math.sqrt(3) / 2  # equilateral triangle on the hoist
    d.polygon([(0, 0), (tip, h / 2), (0, h)], fill=SKY_BLUE)
    cx, cy, r = tip / 3, h / 2, h * 0.16
    pts = []
    for k in range(10):
        a = -math.pi / 2 + k * math.pi / 5
        rr = r if k % 2 == 0 else r * 0.382
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=WHITE)
    im.paste(flag, (0, 0), mask)
    return im.resize((w // ss, h // ss), Image.LANCZOS)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    puerto_rico().save(os.path.join(OUT, "1f1f5-1f1f7.png"))
    print("wrote", OUT)
