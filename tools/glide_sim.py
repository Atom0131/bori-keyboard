"""Reference model of the app's glide decoder (swipe/GlideDecoder.kt) plus the synthetic
swipe generator used by the in-app Swipe Test Lab. Used to tune constants here.
Keep constants in sync with the Kotlin code.

Units: x in key widths, y in key heights (same as KeyProximity.distance in the app).

Usage: python3 -I tools/glide_sim.py en [N words] [seed]     accuracy on the N commonest words
       python3 -I tools/glide_sim.py en hello world ...      decode specific words
"""
import math
import os
import random
import sys
import time
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from suggest_sim import key_centers, lm  # noqa: E402
from verify_dict import Bdic  # noqa: E402

# --- constants mirrored in Kotlin ---
RESAMPLE = 40            # points the user and ideal paths are resampled to
RADIUS = 1.3             # an inner letter must be passed within this many key sizes
RADIUS_ENDS = 1.3        # first/last letter vs first/last touch point
W_LOCATION = 6.0
W_SHAPE = 5.0
GEO_WEIGHT = 2.0         # geometric score vs language model
MAX_GEO_KEEP = 80        # candidates kept after geometry, before LM rescoring
STEP = 0.25              # input path is thinned to one point per STEP key sizes
W_ENDS = 2.0             # distance of first/last letter from where the swipe starts/ends
W_ALIGN = 0.0            # mean distance from each letter to its best in-order path point
GEO_LEN_GAIN = 0.0       # extra geometry weight per key width of swipe beyond GEO_LEN_FROM
GEO_LEN_FROM = 4.0       # swipes shorter than this (key widths) use plain GEO_WEIGHT
GEO_LEN_MAX = 2.0        # the length factor is capped at this



def base_of(ch):
    if "a" <= ch <= "z" or ch == "ñ":
        return ch
    d = unicodedata.normalize("NFD", ch)
    return d[0].lower() if d else ch


def thin(path):
    out = [path[0]]
    for p in path[1:]:
        if math.dist(p, out[-1]) >= STEP:
            out.append(p)
    if out[-1] != path[-1]:
        out.append(path[-1])
    return out


def resample(pts, n=RESAMPLE):
    if len(pts) == 1:
        return [pts[0]] * n
    seg = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    total = sum(seg)
    if total == 0:
        return [pts[0]] * n
    out = []
    step = total / (n - 1)
    i, acc = 0, 0.0
    for k in range(n):
        target = k * step
        while i < len(seg) - 1 and acc + seg[i] < target:
            acc += seg[i]
            i += 1
        t = 0 if seg[i] == 0 else min(1.0, (target - acc) / seg[i])
        a, b = pts[i], pts[i + 1]
        out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return out


def normalize(pts):
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    w = max(p[0] for p in pts) - min(p[0] for p in pts)
    h = max(p[1] for p in pts) - min(p[1] for p in pts)
    s = max(w, h, 1e-6)
    return [((p[0] - cx) / s, (p[1] - cy) / s) for p in pts]


def path_length(pts):
    return sum(math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1))


class Decoder:
    def __init__(self, d, lang):
        self.d = d
        self.centers = key_centers(lang)

    def candidates(self, path):
        """Words whose letters the path passes near, in order (greedy earliest match)."""
        d, C = self.d, self.centers
        P = len(path)
        letters = list(C)
        # next_near[c][j]: first index >= j where the path is within RADIUS of key c.
        next_near = {}
        for c in letters:
            cx, cy = C[c]
            arr = [P] * (P + 1)
            for j in range(P - 1, -1, -1):
                arr[j] = j if math.hypot(path[j][0] - cx, path[j][1] - cy) <= RADIUS else arr[j + 1]
            next_near[c] = arr
        sx, sy = path[0]
        ex, ey = path[-1]
        out = []

        def visit(node, idx, last_key, depth):
            f, cnt = d.first[node], d.count[node]
            for child in range(f, f + cnt) if cnt else ():
                ch = chr(d.chars[child])
                k = base_of(ch)
                if k not in C:
                    # No key (apostrophe, hyphen): free.
                    j = idx
                    key = last_key
                elif k == last_key:
                    j = idx  # doubled letter: no movement needed
                    key = k
                else:
                    if depth == 0:
                        if math.hypot(C[k][0] - sx, C[k][1] - sy) > RADIUS_ENDS:
                            continue
                        j = 0
                    else:
                        j = next_near[k][idx]
                        if j >= P:
                            continue
                    key = k
                # Rare words (flag 32: no corpus frequency) are typed, never swiped.
                if d.freq[child] and depth >= 1 and key is not None and not (d.flags[child] & 36):
                    if math.hypot(C[key][0] - ex, C[key][1] - ey) <= RADIUS_ENDS:
                        out.append(child)
                if d.count[child] and depth < 24:
                    visit(child, j, key, depth + 1)

        visit(0, 0, None, 0)
        return out

    def ideal(self, word_node):
        keys = []
        for ch in self.d.word(word_node).lower():
            k = base_of(ch)
            if k in self.centers and (not keys or keys[-1] != k):
                keys.append(k)
        return [self.centers[k] for k in keys]

    @staticmethod
    def align(path, keys):
        """Mean distance from each key to the path, matching keys to path points in order
        (monotonic dynamic programming; several keys may share a point)."""
        P = len(path)
        prev = None
        for kx, ky in keys:
            row = [math.hypot(px - kx, py - ky) for px, py in path]
            if prev is not None:
                best = math.inf
                for j in range(P):
                    if prev[j] < best:
                        best = prev[j]
                    row[j] += best
            prev = row
        return min(prev) / len(keys)

    def geo_score(self, user_rs, user_norm, user_len, ideal_pts, path=None):
        ideal_rs = resample(ideal_pts)
        ends = math.dist(user_rs[0], ideal_pts[0]) + math.dist(user_rs[-1], ideal_pts[-1])
        loc = sum(math.dist(a, b) for a, b in zip(user_rs, ideal_rs)) / RESAMPLE
        if len(ideal_pts) > 1 and user_len > 1.0:
            shape = sum(math.dist(a, b) for a, b in zip(user_norm, normalize(ideal_rs))) / RESAMPLE
        else:
            shape = 0.0
        al = self.align(path, ideal_pts) if W_ALIGN and path is not None else 0.0
        return -(W_LOCATION * loc + W_SHAPE * shape + W_ENDS * ends + W_ALIGN * al)

    def decode(self, raw_path, prev_node=0, k=5):
        path = thin(raw_path)
        user_rs = resample(path)
        user_norm = normalize(user_rs)
        ulen = path_length(path)
        cands = list(dict.fromkeys(self.candidates(path)))
        geo = [(self.geo_score(user_rs, user_norm, ulen, self.ideal(n), path), n) for n in cands]
        geo.sort(reverse=True)
        # A long swipe is more evidence than a short one, but geo_score is a mean distance.
        gw = GEO_WEIGHT * min(GEO_LEN_MAX, 1.0 + GEO_LEN_GAIN * max(0.0, ulen - GEO_LEN_FROM))
        scored = [(gw * g + lm(self.d, n, prev_node), n) for g, n in geo[:MAX_GEO_KEEP]]
        scored.sort(reverse=True)
        return [(self.d.word(n), round(s, 2)) for s, n in scored[:k]], len(cands)


def synth_path(word, centers, rng, noise=0.22, smooth=3, realistic=False):
    """A plausible human swipe: aim near each key (gaussian error), cut corners, jitter.
    realistic=True adds what real fingers do: a per-swipe offset (finger bias), sloppier
    aim, end overshoot in the direction of travel and variable corner cutting."""
    if realistic:
        return synth_real(word, centers, rng)
    keys = []
    for ch in word.lower():
        k = base_of(ch)
        if k in centers and (not keys or keys[-1] != k):
            keys.append(k)
    aims = [(centers[k][0] + rng.gauss(0, noise), centers[k][1] + rng.gauss(0, noise * 0.8)) for k in keys]
    if len(aims) == 1:
        aims.append((aims[0][0] + 0.3, aims[0][1]))
    pts = []
    for a, b in zip(aims, aims[1:]):
        n = max(2, int(math.dist(a, b) / 0.08))
        for i in range(n):
            t = i / n
            pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    pts.append(aims[-1])
    # Corner cutting: moving average.
    sm = []
    for i in range(len(pts)):
        lo, hi = max(0, i - smooth), min(len(pts), i + smooth + 1)
        sm.append((sum(p[0] for p in pts[lo:hi]) / (hi - lo), sum(p[1] for p in pts[lo:hi]) / (hi - lo)))
    return [(x + rng.gauss(0, 0.03), y + rng.gauss(0, 0.03)) for x, y in sm]


def synth_real(word, centers, rng):
    keys = []
    for ch in word.lower():
        k = base_of(ch)
        if k in centers and (not keys or keys[-1] != k):
            keys.append(k)
    bx, by = rng.gauss(0, 0.15), rng.gauss(0, 0.15)
    noise = rng.uniform(0.2, 0.35)
    aims = [(centers[k][0] + bx + rng.gauss(0, noise), centers[k][1] + by + rng.gauss(0, noise * 0.8)) for k in keys]
    if len(aims) == 1:
        aims.append((aims[0][0] + 0.3, aims[0][1]))
    # Overshoot past the last key and start slightly early.
    (ax, ay), (bx2, by2) = aims[-2], aims[-1]
    dl = math.dist((ax, ay), (bx2, by2)) or 1.0
    over = rng.uniform(0.0, 0.4)
    aims[-1] = (bx2 + (bx2 - ax) / dl * over, by2 + (by2 - ay) / dl * over)
    pts = []
    for a, b in zip(aims, aims[1:]):
        n = max(2, int(math.dist(a, b) / 0.08))
        for i in range(n):
            t = i / n
            pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    pts.append(aims[-1])
    smooth = rng.randint(2, 6)
    sm = []
    for i in range(len(pts)):
        lo, hi = max(0, i - smooth), min(len(pts), i + smooth + 1)
        sm.append((sum(p[0] for p in pts[lo:hi]) / (hi - lo), sum(p[1] for p in pts[lo:hi]) / (hi - lo)))
    return [(x + rng.gauss(0, 0.04), y + rng.gauss(0, 0.04)) for x, y in sm]


def common_words(d, n):
    import heapq
    heap = [(-d.maxf[0], 0, 0)]
    out = []
    while heap and len(out) < n:
        neg, is_word, node = heapq.heappop(heap)
        if is_word:
            w = d.word(node)
            keys = {base_of(c) for c in w.lower() if c.isalpha()}
            if len(w) >= 2 and len(keys) >= 2:
                out.append(w)
            continue
        if d.freq[node] and not (d.flags[node] & 4) and node:
            heapq.heappush(heap, (-d.freq[node], 1, node))
        f, cnt = d.first[node], d.count[node]
        for ch in range(f, f + cnt) if cnt else ():
            if d.maxf[ch]:
                heapq.heappush(heap, (-d.maxf[ch], 0, ch))
    return out


def main():
    lang = sys.argv[1]
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    d = Bdic(os.path.join(root, "app", "src", "main", "assets", "dict", f"{lang}.bdic"))
    dec = Decoder(d, lang)
    args = sys.argv[2:]
    rng = random.Random(int(args[1]) if len(args) > 1 and args[0].isdigit() else 7)
    if args and not args[0].isdigit():
        for w in args:
            res, nc = dec.decode(synth_path(w, dec.centers, rng))
            print(f"{w:12} cands={nc:5} {res}")
        return
    n = int(args[0]) if args else 300
    words = common_words(d, n)
    top1 = top3 = 0
    misses = []
    t0 = time.time()
    for w in words:
        res, _ = dec.decode(synth_path(w, dec.centers, rng))
        got = [r[0].lower() for r in res]
        if got[:1] == [w.lower()]:
            top1 += 1
        if w.lower() in got[:3]:
            top3 += 1
        elif len(misses) < 25:
            misses.append((w, got[:3]))
    dt = (time.time() - t0) / len(words) * 1000
    print(f"{lang}: {len(words)} words  top1 {top1 / len(words):.1%}  top3 {top3 / len(words):.1%}  {dt:.0f} ms/word (python)")
    for m in misses:
        print("  miss", m)


if __name__ == "__main__":
    main()
