"""Accuracy by word shape: length and repeated letters, for glide and for tap correction.

Buckets:
  length  2-4, 5-7, 8-10, 11+
  repeats double  (a letter twice in a row: coffee, letter, balloon)
          revisit (a letter again later, not in a row: banana, level, did)
          plain   (no repeated letter)

Glide: synthetic swipes (glide_sim.synth_real) of words sampled from the commonest 20k,
decoded by the glide_sim reference decoder. Tap: the typo_bench sloppy typist (set B
settings; E = fast typist) and the public misspelling lists (set D1), scored by the
suggest_sim reference corrector.

Usage: python3 -I tools/shape_bench.py en [glide|tap|all] [N per bucket]
       POOL=3000 ...  sample everyday words only (default: the commonest 20000)
       CFGS='[{"GEO_LEN_GAIN": 0.1}, {"MAX_COST_LONG": 3.0}]' ...   sweep constants
"""
import json
import os
import random
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import glide_sim as G  # noqa: E402
import suggest_sim as S  # noqa: E402
import typo_bench as T  # noqa: E402
from verify_dict import Bdic  # noqa: E402

LEN_BUCKETS = [(2, 4), (5, 7), (8, 10), (11, 99)]


def len_bucket(w):
    n = len(w)
    for lo, hi in LEN_BUCKETS:
        if lo <= n <= hi:
            return f"{lo}-{hi}" if hi < 99 else f"{lo}+"
    return "?"


def repeat_kind(w):
    keys = [G.base_of(c) for c in w.lower() if c.isalpha()]
    if any(a == b for a, b in zip(keys, keys[1:])):
        return "double"
    if len(set(keys)) < len(keys):
        return "revisit"
    return "plain"


def sample_words(d, per_bucket, rng, pool=20000):
    words = [w for w in G.common_words(d, pool) if w.isalpha() and w.islower()]
    groups = {}
    for w in words:
        groups.setdefault((len_bucket(w), repeat_kind(w)), []).append(w)
    out = {}
    for k, ws in groups.items():
        rng.shuffle(ws)
        out[k] = ws[:per_bucket]
    return out


def table(title, stats):
    print(f"\n{title}")
    kinds = ["plain", "double", "revisit"]
    print(f"  {'length':8}" + "".join(f"{k:>22}" for k in kinds))
    for lo, hi in LEN_BUCKETS:
        lb = f"{lo}-{hi}" if hi < 99 else f"{lo}+"
        row = f"  {lb:8}"
        for k in kinds:
            s = stats.get((lb, k))
            row += f"{'-':>22}" if not s or not s[0] else f"{s[1] / s[0]:>9.0%} /{s[2] / s[0]:>4.0%} (n={s[0]:>3})"
        print(row)


def run_glide(d, lang, groups, rng):
    dec = G.Decoder(d, lang)
    stats, misses = {}, {}
    t0, n = time.time(), 0
    why = {"pruned": 0, "geo": 0, "lm": 0, "twin": 0}
    for key, ws in sorted(groups.items()):
        for w in ws:
            path = G.synth_path(w, dec.centers, rng, realistic=True)
            res, _ = dec.decode(path)
            got = [r[0].lower() for r in res]
            s = stats.setdefault(key, [0, 0, 0])
            s[0] += 1
            s[1] += got[:1] == [w]
            s[2] += w in got[:3]
            n += 1
            if got[:1] != [w]:
                reason = diagnose(dec, path, w, got[0] if got else None)
                why[reason] += 1
                misses.setdefault(key, []).append((w, got[:3], reason))
    table(f"GLIDE {lang}: top-1 / top-3   ({(time.time() - t0) / max(n, 1) * 1000:.0f} ms/word)", stats)
    print(f"  misses: {why}  (pruned = never a candidate; geo = cut before LM rescoring; "
          f"lm = lost on rescoring; twin = same keys, doubled letters differ)")
    for key in sorted(misses):
        print(f"  miss {key}: " + "; ".join(f"{w}→{g[0] if g else '∅'} [{r}]" for w, g, r in misses[key][:8]))
    return stats


def key_seq(w):
    out = []
    for c in w.lower():
        k = G.base_of(c)
        if k.isalpha() and (not out or out[-1] != k):
            out.append(k)
    return out


def diagnose(dec, raw_path, w, top):
    """Why the decoder missed w."""
    if top is not None and key_seq(top) == key_seq(w):
        return "twin"
    path = G.thin(raw_path)
    cands = list(dict.fromkeys(dec.candidates(path)))
    node = dec.d.find(w)
    if node not in cands:
        return "pruned"
    rs = G.resample(path)
    norm, ulen = G.normalize(rs), G.path_length(path)
    geo = sorted(((dec.geo_score(rs, norm, ulen, dec.ideal(c), path), c) for c in cands), reverse=True)
    rank = [c for _, c in geo].index(node)
    return "geo" if rank >= G.MAX_GEO_KEEP else "lm"


def run_tap(d, lang, groups, rng):
    for name, typist in (
        ("B normal", T.Typist(lang, rng)),
        ("E fast", T.Typist(lang, rng, sigma_x=0.36, sigma_y=0.28, slips=(0.05, 0.09, 0.12))),
    ):
        stats, misses = {}, {}
        for key, ws in sorted(groups.items()):
            for w in ws:
                # Type until the word comes out wrong (we only score real typos).
                for _ in range(20):
                    typed, taps = typist.type_word(w)
                    if typed != w and d.find(typed) is None:
                        break
                else:
                    continue
                _, auto, top = S.suggest(d, lang, typed, None, taps)
                out = (auto or typed).lower()
                s = stats.setdefault(key, [0, 0, 0])
                s[0] += 1
                s[1] += out == w
                s[2] += out == w or w in [t[0].lower() for t in top[:3]]
                if out != w:
                    misses.setdefault(key, []).append((typed, w, out))
        table(f"TAP {lang} {name}: autocorrected right / in bar", stats)
        for key in sorted(misses):
            print(f"  miss {key}: " + "; ".join(f"{t}→{o} (want {w})" for t, w, o in misses[key][:6]))
    if lang == "en":
        stats, misses = {}, {}
        for typed, _, w, _ in T.load_lists(d, lang, 3000, rng):
            key = (len_bucket(w), repeat_kind(w))
            _, auto, top = S.suggest(d, lang, typed, None, None)
            out = (auto or typed).lower()
            s = stats.setdefault(key, [0, 0, 0])
            s[0] += 1
            s[1] += out == w
            s[2] += out == w or w in [t[0].lower() for t in top[:3]]
            if out != w:
                misses.setdefault(key, []).append((typed, w, out))
        table(f"TAP {lang} D1 misspelling lists: autocorrected right / in bar", stats)
        for key in sorted(misses):
            print(f"  miss {key}: " + "; ".join(f"{t}→{o} (want {w})" for t, w, o in misses[key][:6]))


def main():
    lang = sys.argv[1]
    what = sys.argv[2] if len(sys.argv) > 2 else "all"
    per = int(sys.argv[3]) if len(sys.argv) > 3 else 40
    # POOL=3000 samples everyday words only, so a fix for rare words can't hide a loss there.
    pool = int(os.environ.get("POOL", "20000"))
    d = Bdic(os.path.join(T.ROOT, "app", "src", "main", "assets", "dict", f"{lang}.bdic"))
    groups = sample_words(d, per, random.Random(11), pool)
    # CFGS='[{"GEO_LEN_GAIN": 0.1}]' sets constants in glide_sim / suggest_sim, one run each.
    for cfg in json.loads(os.environ.get("CFGS", "[{}]")):
        for k, v in cfg.items():
            setattr(G if hasattr(G, k) else S, k, v)
        S._piece_cache.clear()
        if cfg:
            print(f"\n=== {cfg}")
        if what in ("tap", "all"):
            run_tap(d, lang, groups, random.Random(5))
        if what in ("glide", "all"):
            run_glide(d, lang, groups, random.Random(3))


if __name__ == "__main__":
    main()
