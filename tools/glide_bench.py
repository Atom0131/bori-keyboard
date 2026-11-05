"""Glide (swipe) benchmark for the reference decoder in glide_sim.py.

Sets (realistic synthetic swipes: finger offset, sloppy aim, overshoot, corner cutting):
  G1  common words, no context
  G2  words of Tatoeba sentences, with the previous word as context (how it is really used)
  G3  mid-frequency words (ranks 2000+), no context
  G4  fast, sloppy swipes of common words (wider aim error, rounder corners, bigger offset)
  M   words from tools/wordlists/<lang>_modern.txt (slang of every decade, AI/tech), no context
  R   the user's own Swipe Trainer recordings, if pasted into
      tools/.cache/raw/swipes/<anything>.json (Swipe Trainer → Copy data)

Usage: python3 -I tools/glide_bench.py en [N]       (N words per set, default 300)
       CFGS='[{"W_ENDS": 3.0}]' python3 -I tools/glide_bench.py en   (sweep)
Keep constants in sync with app/.../swipe/GlideDecoder.kt.
"""
import glob
import json
import math
import os
import random
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import glide_sim as G  # noqa: E402
import typo_bench as TB  # noqa: E402
from verify_dict import Bdic  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWIPES = os.path.join(ROOT, "tools", ".cache", "raw", "swipes")


def synth_fast(word, centers, rng):
    """A hurried swipe: like synth_real but sloppier and rounder."""
    keys = []
    for ch in word.lower():
        k = G.base_of(ch)
        if k in centers and (not keys or keys[-1] != k):
            keys.append(k)
    bx, by = rng.gauss(0, 0.22), rng.gauss(0, 0.2)
    noise = rng.uniform(0.3, 0.45)
    aims = [(centers[k][0] + bx + rng.gauss(0, noise), centers[k][1] + by + rng.gauss(0, noise * 0.8)) for k in keys]
    if len(aims) == 1:
        aims.append((aims[0][0] + 0.3, aims[0][1]))
    (ax, ay), (ex, ey) = aims[-2], aims[-1]
    dl = math.dist((ax, ay), (ex, ey)) or 1.0
    over = rng.uniform(0.0, 0.6)
    aims[-1] = (ex + (ex - ax) / dl * over, ey + (ey - ay) / dl * over)
    pts = []
    for a, b in zip(aims, aims[1:]):
        n = max(2, int(math.dist(a, b) / 0.08))
        for i in range(n):
            t = i / n
            pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    pts.append(aims[-1])
    smooth = rng.randint(4, 9)
    sm = []
    for i in range(len(pts)):
        lo, hi = max(0, i - smooth), min(len(pts), i + smooth + 1)
        sm.append((sum(p[0] for p in pts[lo:hi]) / (hi - lo), sum(p[1] for p in pts[lo:hi]) / (hi - lo)))
    return [(x + rng.gauss(0, 0.05), y + rng.gauss(0, 0.05)) for x, y in sm]


def sentence_items(d, lang, n):
    """(word, previous word) pairs from held-out-ish Tatoeba sentences, swipeable words only."""
    out = []
    for toks in TB.load_sentences(d, lang, n * 2, random.Random(42)):
        for i, w in enumerate(toks):
            if len({G.base_of(c) for c in w if c.isalpha()}) >= 2:
                out.append((w, toks[i - 1] if i else None))
    return out[:n]


def load_recordings(lang):
    """Swipe Trainer exports, converted into key units of our own layout."""
    items = []
    for path in sorted(glob.glob(os.path.join(SWIPES, "*.json"))):
        with open(path, encoding="utf-8") as f:
            o = json.load(f)
        if o.get("lang") != lang:
            continue
        kw, kh = o["kw"], o["kh"]
        # Map the phone's pixels onto glide_sim's key units with a per-file affine fit on
        # the key centres (x and y separately), so layouts with other paddings line up.
        centers = G.key_centers(lang)
        common = [c for c in o["keys"] if c in centers]
        fit = []
        for axis, scale in ((0, kw), (1, kh)):
            px = [o["keys"][c][axis] / scale for c in common]
            ux = [centers[c][axis] for c in common]
            mp, mu = sum(px) / len(px), sum(ux) / len(ux)
            fit.append((mp, mu, scale))
        for s in o["swipes"]:
            path = [((x / fit[0][2] - fit[0][0]) + fit[0][1], (y / fit[1][2] - fit[1][0]) + fit[1][1])
                    for x, y in zip(s["x"], s["y"])]
            items.append((s["w"], None, path))
    return items


def prev_node(d, prev):
    return d.find(prev) if prev is not None else None


def evaluate(dec, d, items):
    t1 = t3 = 0
    misses = []
    t0 = time.time()
    for word, prev, path in items:
        res, _ = dec.decode(path, prev_node(d, prev))
        got = [r[0].lower() for r in res]
        w = word.lower()
        t1 += got[:1] == [w]
        t3 += w in got[:3]
        if got[:1] != [w] and len(misses) < 400:
            misses.append((word, prev, got[:3]))
    n = max(len(items), 1)
    return {"n": len(items), "top1": t1 / n, "top3": t3 / n, "ms": (time.time() - t0) / n * 1000, "misses": misses}


def build_sets(d, lang, dec, n):
    centers = dec.centers
    rng = random.Random(11)
    common = G.common_words(d, n)
    mid = G.common_words(d, 2000 + n)[2000:]
    sets = {
        "G1": [(w, None, G.synth_real(w, centers, rng)) for w in common],
        "G2": [(w, p, G.synth_real(w, centers, rng)) for w, p in sentence_items(d, lang, n)],
        "G3": [(w, None, G.synth_real(w, centers, rng)) for w in mid],
        "G4": [(w, None, synth_fast(w, centers, rng)) for w in common],
    }
    modern = [w[0] for w in TB.load_modern(d, lang) if w[0].isalpha() and len(w[0]) >= 3]
    random.Random(12).shuffle(modern)
    sets["M"] = [(w, None, G.synth_real(w, centers, rng)) for w in modern[:n]]
    rec = load_recordings(lang)
    if rec:
        sets["R"] = rec
    return sets


def main():
    lang = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 300
    d = Bdic(os.environ.get("BDIC", os.path.join(ROOT, "app", "src", "main", "assets", "dict", "{lang}.bdic")).format(lang=lang))
    dec = G.Decoder(d, lang)
    # SET_BDIC=<path with {lang}> picks the test words from another build, so two dictionary
    # builds are compared on the same words.
    sd = Bdic(os.environ["SET_BDIC"].format(lang=lang)) if os.environ.get("SET_BDIC") else d
    sets = build_sets(sd, lang, dec, n)
    cfgs = json.loads(os.environ.get("CFGS", "[{}]"))
    show = os.environ.get("MISSES")
    for cfg in cfgs:
        for k, v in cfg.items():
            setattr(G, k, v)
        parts = []
        for name, items in sets.items():
            r = evaluate(dec, d, items)
            parts.append(f"{name} {r['top1']:.1%}/{r['top3']:.1%}")
            if show and name in show.split(","):
                for m in r["misses"][:40]:
                    print(f"    {name} miss: {m[1] or '':>10} {m[0]:14} -> {m[2]}")
        print(f"{cfg}  " + "  ".join(parts) + f"  ({r['ms']:.0f} ms/word py)", flush=True)


if __name__ == "__main__":
    main()
