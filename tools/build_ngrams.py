"""Builds the sentence-context model (.ngram) for next-word prediction from the n-gram counts.

Usage: python3 -I tools/build_ngrams.py en|es
  needs app/src/main/assets/dict/<lang>.bdic (node ids) and tools/.cache/ngrams/<lang>.o{3,4}.*
  (tools/ngram_count.py) -> app/src/main/assets/dict/<lang>.ngram

The .bdic already holds P(next | previous word). This file adds the two- and three-word contexts
("how are" -> you, "i want to" -> go), like the 4-gram models of AOSP LatinIME and Gboard's
pre-neural decoder. Probabilities use interpolated absolute discounting (Kneser-Ney's discount
D = n1 / (n1 + 2 n2)) on top of the app's own bigram score, so the app combines them exactly:

  P2(w | c)     = the app's bigram mix (Suggester.lm: 0.65 P(w|c) + 0.35 P(w), or 0.65 P(w))
  P3(w | b c)   = stored entry, else gamma(b c) * P2(w | c),        context missing: P2(w | c)
  P4(w | a b c) = stored entry, else gamma(a b c) * P3(w | b c),    context missing: P3(w | b c)

Format (little-endian, version 1):
  "BNGR" u16 version u16 reserved u32 nodeCount (must match the .bdic)
  then for order 3 and order 4:
  u32 m | i64 ctxKey[m] (sorted) | u32 start[m+1] | i32 next[k] | u8 cost[k] | u8 backoff[m] | pad4
ctxKey packs node ids 21 bits each, oldest word first: (a << 21) | b, or (a << 42) | (b << 21) | c.
Node 0 (the trie root) means the start of a sentence. cost = round(-ln P * 16), backoff =
round(-ln gamma * 16), both clamped to 0..255. Entries of a context are sorted best first.
"""
import gzip
import json
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from verify_dict import Bdic  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache", "ngrams")
DICT = os.environ.get("BDIC_DIR") or os.path.join(ROOT, "app", "src", "main", "assets", "dict")
OUT = os.environ.get("NGRAM_OUT") or os.path.join(ROOT, "app", "src", "main", "assets", "dict")

# Mirrors predict/Suggester.kt and dict/BinaryDictionary.kt.
BIGRAM_WEIGHT = 0.65
BIGRAM_MISSING = 0.65
UNIGRAM_OFFSET = 4.0

# Pruning, tuned with tools/predict_bench.py for the size budget. Like Stolcke's entropy
# pruning, every candidate entry gets a gain = count * ln(P / backoff estimate) (how much the
# longer context adds over the shorter one, weighted by how often it is used) and only the KEEP
# best entries of each order are stored, whatever their context.
MIN_CTX = {3: int(os.environ.get("MIN_CTX3", "6")), 4: int(os.environ.get("MIN_CTX4", "6"))}
MIN_ENT = {3: int(os.environ.get("MIN_ENT3", "3")), 4: int(os.environ.get("MIN_ENT4", "3"))}
TOP = {3: int(os.environ.get("TOP3", "12")), 4: int(os.environ.get("TOP4", "8"))}
KEEP = {3: int(os.environ.get("KEEP3", "250000")), 4: int(os.environ.get("KEEP4", "130000"))}
# Candidates must beat what the shorter context already predicts by this factor.
KEEP_RATIO = float(os.environ.get("KEEP_RATIO", "1.5"))
BITS = 21


class Lower:
    """The app's bigram score P2(w | c), from the .bdic."""

    def __init__(self, d):
        self.d = d
        self.big = {}
        for i, p in enumerate(d.prev):
            self.big[p] = {d.next[j]: math.exp(-d.cost[j] / 16) for j in range(d.start[i], d.start[i + 1])}

    def uni(self, node):
        return math.exp((self.d.freq[node] / 255 - 1) * self.d.log_range - UNIGRAM_OFFSET)

    def p2(self, w, c):
        pu = self.uni(w)
        pb = self.big.get(c, {}).get(w)
        return BIGRAM_WEIGHT * pb + (1 - BIGRAM_WEIGHT) * pu if pb is not None else BIGRAM_MISSING * pu


def word_nodes(d):
    """Lowercase key -> node for every word in the trie, plus "<s>" -> 0."""
    keys = {}
    for node in range(1, d.n):
        if not d.freq[node]:
            continue
        s, n = [], node
        while n > 0:
            s.append(chr(d.chars[n]))
            n = d.parent[n]
        keys["".join(reversed(s))] = node
    keys["<s>"] = 0
    return keys


def discount(stats, n):
    c = stats[str(n)]
    n1, n2 = c.get("1", 0), c.get("2", 0)
    return n1 / (n1 + 2 * n2) if n1 + n2 else 0.75


def read_contexts(lang, n, nodes):
    """{ctx tuple of nodes: (total, distinct)} for contexts seen often enough."""
    out = {}
    with gzip.open(os.path.join(CACHE, f"{lang}.o{n}.ctx.gz"), "rt", encoding="utf-8") as f:
        for line in f:
            ctx, tot, distinct = line.rstrip("\n").split("\t")
            tot = int(tot)
            if tot < MIN_CTX[n]:
                continue
            ids = tuple(nodes.get(w, -1) for w in ctx.split(" "))
            if -1 in ids or 0 in ids[1:]:
                continue
            out[ids] = (tot, int(distinct))
    return out


def read_entries(lang, n, nodes, contexts):
    """{ctx: [(next node, count)]} for the kept contexts."""
    out = {}
    with gzip.open(os.path.join(CACHE, f"{lang}.o{n}.ent.gz"), "rt", encoding="utf-8") as f:
        for line in f:
            key, c = line.rstrip("\n").split("\t")
            c = int(c)
            if c < MIN_ENT[n]:
                continue
            ws = key.split(" ")
            w = nodes.get(ws[-1])
            if not w:
                continue
            ids = tuple(nodes.get(x, -1) for x in ws[:-1])
            if ids not in contexts:
                continue
            out.setdefault(ids, []).append((w, c))
    return out


class Table:
    """One order's stored contexts: {ctx: (gamma, {next: P})}."""

    def __init__(self):
        self.ctx = {}

    def p(self, ctx, w, lower):
        e = self.ctx.get(ctx)
        if e is None:
            return lower(ctx[1:], w)
        gamma, probs = e
        return probs.get(w, gamma * lower(ctx[1:], w))


def quant(p):
    return max(0, min(255, round(-math.log(max(p, 1e-30)) * 16)))


def build_order(n, contexts, entries, D, lower_p):
    cands = []
    gammas = {}
    for ctx, lst in entries.items():
        tot, distinct = contexts[ctx]
        gamma = min(1.0, D * distinct / tot)
        gammas[ctx] = gamma
        taken = 0
        for w, c in sorted(lst, key=lambda x: -x[1]):
            back = gamma * lower_p(ctx[1:], w)
            p = (c - D) / tot + back
            if p > KEEP_RATIO * back:
                cands.append((c * math.log(p / back), ctx, w, p))
                taken += 1
                if taken == TOP[n]:
                    break
    cands.sort(key=lambda x: -x[0])
    t = Table()
    for _, ctx, w, p in cands[:KEEP[n]]:
        if ctx not in t.ctx:
            # Quantised now so the build-time model matches what the app reads.
            t.ctx[ctx] = (math.exp(-quant(gammas[ctx]) / 16), {})
        t.ctx[ctx][1][w] = math.exp(-quant(p) / 16)
    kept = sum(len(v[1]) for v in t.ctx.values())
    print(f"  order {n}: D={D:.3f}, {len(cands)} candidates, {len(t.ctx)} contexts, {kept} entries")
    return t


def pack(ctx):
    k = 0
    for x in ctx:
        k = (k << BITS) | x
    return k


def write(lang, n_nodes, tables):
    out = bytearray(b"BNGR" + struct.pack("<HHI", 1, 0, n_nodes))
    for t in tables:
        items = sorted((pack(c), c) for c in t.ctx)
        m = len(items)
        out += struct.pack("<I", m)
        out += struct.pack(f"<{m}q", *(k for k, _ in items))
        starts, nexts, costs = [0], [], []
        for _, c in items:
            gamma, probs = t.ctx[c]
            for w, p in sorted(probs.items(), key=lambda x: -x[1]):
                nexts.append(w)
                costs.append(quant(p))
            starts.append(len(nexts))
        out += struct.pack(f"<{m + 1}I", *starts)
        out += struct.pack(f"<{len(nexts)}i", *nexts)
        out += bytes(costs)
        out += bytes(quant(t.ctx[c][0]) for _, c in items)
        while len(out) % 4:
            out.append(0)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"{lang}.ngram")
    with open(path, "wb") as f:
        f.write(out)
    return path, len(out)


def main():
    lang = sys.argv[1]
    d = Bdic(os.path.join(DICT, f"{lang}.bdic"))
    assert d.n < (1 << BITS), "node ids no longer fit in 21 bits"
    nodes = word_nodes(d)
    low = Lower(d)
    stats = json.load(open(os.path.join(CACHE, f"{lang}.stats.json")))
    tables = []

    def p2(ctx, w):
        return low.p2(w, ctx[-1])

    c3 = read_contexts(lang, 3, nodes)
    t3 = build_order(3, c3, read_entries(lang, 3, nodes, c3), discount(stats, 3), p2)
    del c3
    tables.append(t3)

    def p3(ctx, w):
        return t3.p(ctx, w, p2)

    c4 = read_contexts(lang, 4, nodes)
    t4 = build_order(4, c4, read_entries(lang, 4, nodes, c4), discount(stats, 4), p3)
    tables.append(t4)
    path, size = write(lang, d.n, tables)
    print(f"{lang}: {size / 1e6:.1f} MB -> {path}")


if __name__ == "__main__":
    main()
