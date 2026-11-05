"""Next-word prediction benchmark: how often the word the user types next is in the strip.

Usage: python3 -I tools/predict_bench.py en|es [lines]
  BASE_BDIC=<old .bdic>  also scores the old app (bigrams only, raw bigram score), e.g. a copy
                         of the dictionary from before the n-gram work.
Test text: tools/.cache/ngrams/<lang>.holdout.txt (subtitle lines tools/ngram_count.py kept out
of the counts). For every word with a known previous word it asks for 3 predictions, like the
strip after a space, and reports top-1 / top-3 accuracy. Gboard's paper (Hard et al. 2018)
reports ~13-16% top-1 / ~22-27% top-3 for n-gram and neural models on real typing.

Mirrors Suggester.predict / Suggester.lm (without the user's learned words).
"""
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_ngrams import Lower, word_nodes  # noqa: E402
from ngram_count import NAMES, clauses  # noqa: E402
from verify_dict import Bdic  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DICT = os.path.join(ROOT, "app", "src", "main", "assets", "dict")
CACHE = os.path.join(ROOT, "tools", ".cache", "ngrams")
BITS = 21


class Ngram:
    """Reader for .ngram, mirroring dict/NgramModel.kt."""

    def __init__(self, path, n_nodes):
        b = open(path, "rb").read()
        assert b[:4] == b"BNGR"
        ver, _, nodes = struct.unpack_from("<HHI", b, 4)
        assert ver == 1 and nodes == n_nodes, (ver, nodes, n_nodes)
        o = 12
        self.tables = []
        for _ in range(2):
            (m,) = struct.unpack_from("<I", b, o); o += 4
            keys = struct.unpack_from(f"<{m}q", b, o); o += 8 * m
            start = struct.unpack_from(f"<{m + 1}I", b, o); o += 4 * (m + 1)
            k = start[-1]
            nxt = struct.unpack_from(f"<{k}i", b, o); o += 4 * k
            cost = b[o:o + k]; o += k
            back = b[o:o + m]; o += m
            o = (o + 3) & ~3
            t = {}
            for i, key in enumerate(keys):
                t[key] = (-back[i] / 16, [(nxt[j], -cost[j] / 16) for j in range(start[i], start[i + 1])])
            self.tables.append(t)
        assert o == len(b)
        self.entries = sum(len(v[1]) for t in self.tables for v in t.values())

    def resolve(self, a, b, c):
        if b is None or b < 0 or c <= 0:
            return None, None
        r3 = self.tables[0].get((b << BITS) | c)
        r4 = self.tables[1].get((a << 2 * BITS) | (b << BITS) | c) if a is not None and a >= 0 and b > 0 else None
        return r3, r4

    @staticmethod
    def score(r, node, s2):
        s = s2
        for row in r:
            if row is not None:
                back, lst = row
                s = next((lp for nx, lp in lst if nx == node), back + s)
        return s


class Model:
    def __init__(self, bdic, ngram=None, raw_bigrams=False):
        self.d = Bdic(bdic)
        self.low = Lower(self.d)
        self.nodes = word_nodes(self.d)
        self.ng = Ngram(ngram, self.d.n) if ngram else None
        self.raw = raw_bigrams

    def predict(self, ctx_words, k=3):
        """ctx_words: tokens before the cursor, "<s>" first if the sentence starts there."""
        ids = []
        for w in reversed(ctx_words[-4:]):
            n = self.nodes.get(w)
            if n is None:
                break
            ids.append(n)
            if n == 0:
                break
        ids.reverse()
        if not ids:
            return []
        c = ids[-1]
        big = self.low.big.get(c, {})
        if self.raw:
            # The old app: raw bigram score, nothing else.
            return [n for n, _ in sorted(big.items(), key=lambda x: -x[1])[:k]]
        b = ids[-2] if len(ids) >= 2 else None
        a = ids[-3] if len(ids) >= 3 else None
        r = self.ng.resolve(a, b, c) if self.ng else (None, None)
        cands = set(big)
        for row in r:
            if row is not None:
                cands.update(nx for nx, _ in row[1])
        scored = []
        for w in cands:
            s2 = math.log(self.low.p2(w, c))
            scored.append((Ngram.score(r, w, s2), w))
        scored.sort(reverse=True)
        return [w for _, w in scored[:k]]


def run(lang, model, lines):
    top1 = top3 = total = 0
    deep_total = deep3 = 0
    for line in lines:
        for toks in clauses(line):
            for i in range(1, len(toks)):
                target = model.nodes.get(toks[i])
                if not target or toks[i] in NAMES:
                    continue
                preds = model.predict(toks[:i])
                total += 1
                if preds[:1] == [target]:
                    top1 += 1
                if target in preds[:3]:
                    top3 += 1
                if i >= 2:
                    deep_total += 1
                    deep3 += target in preds[:3]
    return top1 / total * 100, top3 / total * 100, deep3 / max(1, deep_total) * 100, total


def main():
    lang = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 4000
    with open(os.path.join(CACHE, f"{lang}.holdout.txt"), encoding="utf-8") as f:
        lines = [x for x in f.read().split("\n") if x][:n]
    configs = []
    if os.environ.get("BASE_BDIC"):
        configs.append(("old app (Tatoeba bigrams, raw)", Model(os.environ["BASE_BDIC"], raw_bigrams=True)))
    bdic = os.environ.get("BDIC") or os.path.join(DICT, f"{lang}.bdic")
    ngram = os.environ.get("NGRAM") or os.path.join(DICT, f"{lang}.ngram")
    configs.append(("new bigrams only", Model(bdic)))
    if os.path.exists(ngram):
        m = Model(bdic, ngram)
        configs.append((f"new bigrams + n-grams ({m.ng.entries} entries)", m))
    for name, m in configs:
        t1, t3, d3, total = run(lang, m, lines)
        print(f"{lang} {name:48s} top-1 {t1:5.1f}%  top-3 {t3:5.1f}%  top-3 mid-sentence {d3:5.1f}%  ({total} words)")


if __name__ == "__main__":
    main()
