"""Builds the typo list: app/src/main/assets/dict/<lang>_typos.txt ("typo<TAB>word" lines).

The keyboard fixes a typed non-word that is on this list to the listed word (Suggester,
TYPO_LIST_*). Three sources:
  1. Keyboard slips of the commonest TARGETS words: a neighbour key, two letters swapped, a
     letter dropped, a doubled letter added or lost, an extra neighbour letter. Only the slips
     the corrector finds hard: in short words (HARD_MAX_LEN) or on the first letter. Kept only when
     one word clearly explains the slip: its likelihood (frequency × how easy the slip is) is
     RATIO× that of any other dictionary word one edit away, and the slip isn't a real token
     in the subtitle counts (names, slang the dictionary lacks).
  2. Wikipedia's misspelling lists (CC BY-SA, like the dictionaries; see ATTRIBUTION.md), from
     tools/.cache/raw/typos/: en wikipedia.dat (Birkbeck's copy of "Lists of common
     misspellings"), es es_list.json. Norvig's sets are only used for testing (licence unclear).
  3. The user's own typos (typo_bench set A).

Usage: python3 -I tools/build_typos.py en|es [--holdout] [--out PATH]
  --holdout leaves out the half of source 2 that typo_bench scores D1 on (D1_HALF=1), so
  the benchmark doesn't grade the list on its own answers. The shipped file uses everything.
"""
import math
import os
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import glide_sim as G  # noqa: E402
import suggest_sim as S  # noqa: E402
import typo_bench as T  # noqa: E402
from verify_dict import Bdic  # noqa: E402

TARGETS = 6000        # slips are generated for this many of the commonest words
COMPETITORS = 60000   # other words checked as alternative explanations
RATIO = 8.0           # the target must be this many times likelier than any other word
FAR_EDIT = 0.05       # a non-neighbour substitution/insertion is this much less likely
DOUBLE_TOP = 300      # these commonest short words also get two-neighbour-key slips (rje -> the)
DOUBLE_WEIGHT = 0.1   # a two-key slip is this much less likely than a one-key slip
HARD_MAX_LEN = 5      # longer words only get first-letter slips (the corrector handles the rest)
REAL_TOKEN = 0.02     # a slip seen this often (vs. its target) in subtitles is a real token
MIN_LEN = 3
MAX_ENTRIES = 25000
NEIGHBOUR = 1.2       # key units between centres


def holdout_half(typo):
    """The half of source 2 that typo_bench D1 scores when D1_HALF=1."""
    return zlib.crc32(typo.encode()) % 2 == 0


def neighbours(lang):
    c = S.key_centers(lang)
    out = {}
    for a, (ax, ay) in c.items():
        out[a] = {b for b, (bx, by) in c.items() if b != a and math.hypot(ax - bx, ay - by) <= NEIGHBOUR}
    return out


def slips(word, near, alphabet):
    """(slip, weight) for every one-edit slip of word; weight 1 for easy slips."""
    out = {}

    def add(s, w):
        if s != word and len(s) >= MIN_LEN and out.get(s, 0) < w:
            out[s] = w

    n = len(word)
    for i, ch in enumerate(word):
        base = S.base_of(ch)
        # Substitution.
        for c in alphabet:
            if c != ch:
                # A dropped accent (así -> asi) is the easiest slip of all.
                easy = c == base or c in near.get(base, ())
                add(word[:i] + c + word[i + 1:], 1.0 if easy else FAR_EDIT)
        # Deletion (a doubled letter losing one is the easiest).
        add(word[:i] + word[i + 1:], 1.0)
        # Swap with the next letter.
        if i + 1 < n and word[i + 1] != ch:
            add(word[:i] + word[i + 1] + ch + word[i + 2:], 1.0)
    for i in range(n + 1):
        prev = S.base_of(word[i - 1]) if i else None
        nxt = S.base_of(word[i]) if i < n else None
        for c in alphabet:
            # An extra letter next to a neighbour (or a doubled letter) is easy.
            easy = c in (prev, nxt) or c in near.get(prev, ()) or c in near.get(nxt, ())
            add(word[:i] + c + word[i:], 1.0 if easy else FAR_EDIT)
    return out


def is_typo(d, s):
    """Not a word, or only a rare one (no corpus frequency; a listed typo still gets fixed)."""
    node = d.find(s)
    return node is None or bool(d.flags[node] & (32 | 64))


def log_p(d, node):
    return (d.freq[node] / 255 - 1) * d.log_range


def modern_words(lang):
    from wordlist_files import word_files
    out = set()
    for path in word_files(lang):
        with open(path, encoding="utf-8") as f:
            for line in f:
                p = line.split("#", 1)[0].split()
                for t in p[:1] + p[3:]:
                    out.add(t.lstrip("=").lower())
    return out


def token_counts(lang, wanted):
    counts = {}
    with open(os.path.join(T.RAW, f"{lang}_freq.txt"), encoding="utf-8") as f:
        for line in f:
            w, _, c = line.rstrip("\n").partition(" ")
            if w in wanted:
                counts[w] = int(c)
    return counts


def generated(d, lang):
    near = neighbours(lang)
    alphabet = "".join(S.ROWS[lang])
    targets = [w for w in G.common_words(d, TARGETS) if w.isalpha() and w.islower()]
    # slip -> (best target score, target)
    best = {}
    for w in targets:
        lp = log_p(d, d.find(w))
        for s, wt in slips(w, near, alphabet).items():
            if wt < 1.0 or not is_typo(d, s):
                continue
            if len(w) > HARD_MAX_LEN and s[0] == w[0]:
                continue
            sc = lp + math.log(wt)
            if s not in best or sc > best[s][0]:
                best[s] = (sc, w)
    for w in targets[:DOUBLE_TOP]:
        if not 3 <= len(w) <= HARD_MAX_LEN:
            continue
        lp = log_p(d, d.find(w)) + math.log(DOUBLE_WEIGHT)
        for i in range(len(w)):
            for j in range(i + 1, len(w)):
                for a in near.get(S.base_of(w[i]), ()):
                    for b in near.get(S.base_of(w[j]), ()):
                        s = w[:i] + a + w[i + 1:j] + b + w[j + 1:]
                        if is_typo(d, s) and (s not in best or lp > best[s][0]):
                            best[s] = (lp, w)
    # Strongest other explanation of each slip, over a much larger vocabulary.
    rival = {}
    for w in G.common_words(d, COMPETITORS):
        w = w.lower()
        node = d.find(w)
        if not w.isalpha() or node is None:
            continue
        lp = log_p(d, node)
        for s, wt in slips(w, near, alphabet).items():
            if s in best and best[s][1] != w:
                sc = lp + math.log(wt)
                if sc > rival.get(s, -1e9):
                    rival[s] = sc
    counts = token_counts(lang, set(best) | set(t for _, t in best.values()))
    out = {}
    for s, (sc, w) in best.items():
        if sc - rival.get(s, -1e9) < math.log(RATIO):
            continue
        if counts.get(s, 0) > REAL_TOKEN * max(counts.get(w, 1), 1):
            continue
        out[s] = w
    return out


def listed(d, lang, holdout):
    if lang == "en":
        raw = T.read_birkbeck("wikipedia.dat")
    else:
        raw = T.read_es_wiki()
    out = {}
    for typo, target in raw:
        t, w = typo.lower(), target.lower()
        if t == w or len(t) < MIN_LEN or not T.WORD_RX.fullmatch(t) or " " in w:
            continue
        if holdout and holdout_half(t):
            continue
        node = d.find(w)
        if node is None or d.flags[node] & 4 or not is_typo(d, t):
            continue
        if T.edit_distance(t, w) > 3:
            continue
        out.setdefault(t, d.word(node))
    return out


def own(d, lang):
    out = {}
    for typed, target, _ in T.REAL_TYPOS[lang]:
        t, w = typed.lower(), target.lower()
        if " " in w or not is_typo(d, t):
            continue
        node = d.find(w)
        if node is not None:
            out[t] = d.word(node)
    return out


def main():
    lang = sys.argv[1]
    holdout = "--holdout" in sys.argv
    out_path = os.path.join(T.ROOT, "app", "src", "main", "assets", "dict", f"{lang}_typos.txt")
    if "--out" in sys.argv:
        out_path = sys.argv[sys.argv.index("--out") + 1]
    d = Bdic(os.path.join(T.ROOT, "app", "src", "main", "assets", "dict", f"{lang}.bdic"))
    modern = modern_words(lang)
    gen = generated(d, lang)
    lst = listed(d, lang, holdout)
    mine = own(d, lang)
    merged = {}
    for src in (gen, lst, mine):  # later sources win
        merged.update(src)
    merged = {t: w for t, w in merged.items() if t not in modern or t in mine}
    if len(merged) > MAX_ENTRIES:
        # Own typos first, then the public lists, then generated slips of the commonest words.
        keep = sorted(merged, key=lambda t: (t not in mine, t not in lst, -d.freq[d.find(merged[t].lower())]))
        keep = keep[:MAX_ENTRIES]
        merged = {t: merged[t] for t in keep}
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(f"# Bori typo list ({lang}), built by tools/build_typos.py; typo<TAB>word\n")
        for t in sorted(merged):
            f.write(f"{t}\t{merged[t]}\n")
    print(f"{lang}: generated {len(gen)}, lists {len(lst)}, own {len(mine)} -> {len(merged)} entries "
          f"({os.path.getsize(out_path) // 1024} KB) {out_path}")


if __name__ == "__main__":
    main()
