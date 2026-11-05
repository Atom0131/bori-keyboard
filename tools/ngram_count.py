"""Counts word 2-, 3- and 4-grams in Tatoeba + OpenSubtitles for next-word prediction.

Usage: python3 -I tools/ngram_count.py en|es
  -> tools/.cache/ngrams/<lang>.o{2,3,4}.ent.gz   "w1 w2 ... wn<TAB>count"  (count >= 2)
     tools/.cache/ngrams/<lang>.o{2,3,4}.ctx.gz   "w1 ... w(n-1)<TAB>total<TAB>distinct"
     tools/.cache/ngrams/<lang>.stats.json        counts-of-counts per order (for discounts)
     tools/.cache/ngrams/<lang>.holdout.txt       held-out subtitle lines for predict_bench.py

Inputs: tools/.cache/raw/<tatoeba>_sentences.tsv.bz2 and tools/.cache/raw/opensubs/<lang>.20M.txt.gz
(see tools/README.md). Words are counted as typed (lowercase), not limited to the dictionary,
so these files don't depend on the trie: build_dict.py and build_ngrams.py filter them later.
"<s>" marks the start of a sentence or clause. Context totals include every following word, so
probabilities stay honest after rare n-grams are dropped.

Memory: chunks of lines are counted in worker processes and written as sorted files, then merged
with GNU sort -m (streaming), so peak memory stays a few hundred MB per worker.
"""
import bz2
import gzip
import json
import multiprocessing as mp
import os
import re
import shutil
import subprocess
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "tools", ".cache", "raw")
OUT = os.path.join(ROOT, "tools", ".cache", "ngrams")
TATOEBA = {"en": "eng", "es": "spa"}
ORDERS = (2, 3, 4)
CHUNK = 100_000
WORKERS = 3
# Subtitle lines from HOLDOUT_FROM on are kept out of the counts for predict_bench.py, as one
# block: the same film often appears several times in a row, so scattered held-out lines would
# also be in the counts word for word. Every HOLDOUT_EVERY-th line of the block is saved.
HOLDOUT_FROM = 19_000_000
HOLDOUT_EVERY = 25
HOLDOUT_KEEP = 40_000
WORD_RX = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)*")
CLAUSE_RX = re.compile(r"[.!?;:¿¡\"()…\[\]]+")
# Tatoeba's example sentences are dominated by these names; never predict them.
NAMES = {"tom", "mary", "john", "ken", "maría", "jack", "jim"}


def clauses(text):
    """Lists of lowercase tokens, "<s>" first, one per clause. Commas don't break context."""
    text = text.replace("’", "'").replace("‘", "'")
    for clause in CLAUSE_RX.split(text):
        toks = [t.lower() for t in WORD_RX.findall(clause)]
        if toks:
            yield ["<s>"] + toks


def count_chunk(args):
    lang, idx, lines = args
    counts = {n: Counter() for n in ORDERS}
    for line in lines:
        for toks in clauses(line):
            for i in range(1, len(toks)):
                w = toks[i]
                if w in NAMES:
                    continue
                for n in ORDERS:
                    if i - n + 1 < 0:
                        break
                    counts[n][" ".join(toks[i - n + 1:i + 1])] += 1
    for n in ORDERS:
        path = os.path.join(OUT, f"tmp_{lang}", f"o{n}", f"{idx:05d}.txt")
        with open(path, "w", encoding="utf-8") as f:
            for k in sorted(counts[n], key=lambda s: s.encode("utf-8")):
                f.write(f"{k}\t{counts[n][k]}\n")
    return idx


def batches(lang, holdout):
    """(lang, index, lines) chunks from Tatoeba and the subtitles; fills [holdout]."""
    buf, idx = [], 0
    tat = os.path.join(RAW, f"{TATOEBA[lang]}_sentences.tsv.bz2")
    with bz2.open(tat, "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                buf.append(parts[2])
            if len(buf) == CHUNK:
                yield lang, idx, buf
                buf, idx = [], idx + 1
    subs = os.path.join(RAW, "opensubs", f"{lang}.20M.txt.gz")
    with gzip.open(subs, "rt", encoding="utf-8", errors="replace") as f:
        for i, line in enumerate(f):
            line = line.strip().lstrip("-").strip()
            if not line:
                continue
            if i >= HOLDOUT_FROM:
                if i % HOLDOUT_EVERY == 0 and len(holdout) < HOLDOUT_KEEP:
                    holdout.append(line)
                continue
            buf.append(line)
            if len(buf) == CHUNK:
                yield lang, idx, buf
                buf, idx = [], idx + 1
    if buf:
        yield lang, idx, buf


def merge(lang, n, stats):
    """Merges the sorted chunk files of order [n] into the .ent/.ctx files."""
    d = os.path.join(OUT, f"tmp_{lang}", f"o{n}")
    files = sorted(os.path.join(d, x) for x in os.listdir(d))
    env = dict(os.environ, LC_ALL="C")
    proc = subprocess.Popen(
        ["sort", "-m", "-t", "\t", "-k1,1", "--batch-size=1024", "-T", d] + files,
        stdout=subprocess.PIPE, env=env,
    )
    coc = Counter()
    ent = gzip.open(os.path.join(OUT, f"{lang}.o{n}.ent.gz"), "wt", encoding="utf-8", compresslevel=3)
    ctxf = gzip.open(os.path.join(OUT, f"{lang}.o{n}.ctx.gz"), "wt", encoding="utf-8", compresslevel=3)
    cur_ctx, tot, distinct = None, 0, 0
    pending = []

    def flush_ctx():
        if cur_ctx is not None and tot >= 2:
            ctxf.write(f"{cur_ctx}\t{tot}\t{distinct}\n")
            for k, c in pending:
                ent.write(f"{k}\t{c}\n")

    def emit(key, c):
        nonlocal cur_ctx, tot, distinct, pending
        coc[min(c, 4)] += 1
        ctx = key.rsplit(" ", 1)[0]
        if ctx != cur_ctx:
            flush_ctx()
            cur_ctx, tot, distinct, pending = ctx, 0, 0, []
        tot += c
        distinct += 1
        if c >= 2:
            pending.append((key, c))

    last, acc = None, 0
    for raw in proc.stdout:
        key, c = raw.decode("utf-8").rstrip("\n").split("\t")
        if key == last:
            acc += int(c)
            continue
        if last is not None:
            emit(last, acc)
        last, acc = key, int(c)
    if last is not None:
        emit(last, acc)
    flush_ctx()
    ent.close()
    ctxf.close()
    if proc.wait() != 0:
        raise SystemExit(f"sort failed for order {n}")
    stats[str(n)] = {str(k): v for k, v in sorted(coc.items())}
    shutil.rmtree(d)


def main():
    lang = sys.argv[1]
    tmp = os.path.join(OUT, f"tmp_{lang}")
    shutil.rmtree(tmp, ignore_errors=True)
    for n in ORDERS:
        os.makedirs(os.path.join(tmp, f"o{n}"))
    holdout = []
    done = 0
    # Bounded queue: Pool.imap would read the whole corpus into memory ahead of the workers.
    with mp.get_context("fork").Pool(WORKERS) as pool:
        running = []
        for b in batches(lang, holdout):
            running.append(pool.apply_async(count_chunk, (b,)))
            while len(running) >= WORKERS * 2:
                running.pop(0).get()
                done += 1
                if done % 20 == 0:
                    print(f"{lang}: {done} chunks counted", flush=True)
        for r in running:
            r.get()
    with open(os.path.join(OUT, f"{lang}.holdout.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(holdout) + "\n")
    stats = {}
    for n in ORDERS:
        merge(lang, n, stats)
        print(f"{lang}: order {n} merged, counts-of-counts {stats[str(n)]}", flush=True)
    with open(os.path.join(OUT, f"{lang}.stats.json"), "w") as f:
        json.dump(stats, f, indent=1)
    shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
