"""Builds Bori Keyboard dictionaries (.bdic) from open data.

Inputs (downloaded into tools/.cache/raw, see tools/README.md):
  <lang>_freq.txt          FrequencyWords (OpenSubtitles 2018), CC-BY-SA-4.0: "word count"
  <lang>_index.dic/.aff    Hunspell dictionaries (wooorm/dictionaries) used as the valid-word
                           filter and for capitalisation (en: SCOWL; es: RLA-ES, MPL/LGPL/GPL)
  <tatoeba>_sentences.tsv.bz2  Tatoeba sentences, CC-BY 2.0 FR, for next-word bigrams
  tools/wordlists/<lang>_modern.txt  hand-curated slang, texting, AI/tech words and names the
                           2018 subtitles miss (own work, see tools/wordlists/README.md)
  wordfreq large_<lang>    word frequencies from Wikipedia, Reddit, Twitter, news, books, web
                           (CC BY-SA 4.0), read by wordfreq_data.py: raises words the subtitles
                           undercount (foldable, multimodal, tachycardia). Never adds words.
  wiktionary/<lang>_words.tsv  every Wiktionary word and inflection (CC BY-SA), from
                           wiktionary.py; misspellings and obsolete words nobody uses are left out

Usage: python3 -I tools/build_dict.py en|es  -> app/src/main/assets/dict/<lang>.bdic

Format (little-endian, version 1). Structure-of-arrays so the app can bulk-load it:
  "BDIC" u16 version u16 reserved u32 nodeCount f32 unigramLogRange u32 wordCount
  u16 char[n] | i32 parent[n] | i32 firstChild[n] | u8 childCount[n] | u8 freq[n]
  | u8 maxFreq[n] | u8 flags[n] | pad4
  u32 displayCount, then per entry: u32 node, u16 byteLen, utf8 bytes | pad4
  u32 m | u32 prevNode[m] | u32 prevStart[m+1] | u32 nextNode[k] | u8 nextCost[k] | pad4
Node 0 is the root; as a bigram "prev" it means sentence start. Trie keys are lowercase.
freq: 0 = not a word, else 1..255 on a log scale spanning unigramLogRange nats.
flags: 1 capitalise first letter, 2 all caps, 4 never suggest, 8 custom display form,
16 keep as typed (never "fixed" into another real word; everyday words from the word lists),
32 rare (no corpus frequency at all, mostly Wiktionary-only words): only weakly valid, so a
likelier fix still wins and a listed typo is still fixed (Suggester.RARE_TYPED_BONUS); never
swiped. 64 weak: added from Wiktionary with a wordfreq count; weakly valid like 32, swipeable.
nextCost: round(-ln P(next|prev) * 16), clamped to 0..255.
"""
import bz2
import gzip
import math
import os
import re
import struct
import sys
import unicodedata
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wordfreq_data  # noqa: E402
from hunspell_expand import expand  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "tools", ".cache", "raw")
# BDIC_OUT=<dir> writes a trial build elsewhere (for benchmarking before replacing the asset).
OUT = os.environ.get("BDIC_OUT") or os.path.join(ROOT, "app", "src", "main", "assets", "dict")

CONFIG = {
    "en": {"tatoeba": "eng", "min_count": 3, "add_hunspell_only": True, "max_words": 160_000},
    "es": {"tatoeba": "spa", "min_count": 3, "add_hunspell_only": False, "max_words": 200_000},
}
# Wiktionary parts of speech that aren't typed as words.
WIKT_SKIP_POS = {"character", "symbol", "punct", "prefix", "suffix", "infix", "interfix", "affix",
                 "circumfix", "phrase", "proverb", "romanization", "prep_phrase", "contraction_part"}
# Wiktionary entries kept even without any corpus frequency: slang and informal words of every
# era, and words with a subject label (medicine, computing, chemistry, education...). Other
# entries need wordfreq to have seen them. WIKT_ALL=1 keeps every entry (about 1M English
# words, 36 MB, 4x slower search; not shipped).
WIKT_USAGE_KEEP = {"slang", "informal", "colloquial", "Internet", "dated", "humorous", "vulgar",
                   "derogatory", "offensive", "abbreviation", "initialism", "acronym"}
# Wiktionary-added words that wordfreq has seen get flag 64 (weak): weakly valid like rare
# words, but swipeable. WIKT_WEAK=0 makes them ordinary words (trial builds).
WIKT_WEAK = os.environ.get("WIKT_WEAK", "1") == "1"
# Obsolete/archaic-only words are kept only when people still write them this often (Zipf).
WIKT_OLD_MIN_ZIPF = 2.0
# wordfreq share of tokens -> subtitle-scale count multiplier. At 1.0 web-common words crowded
# out chat words on swipes (glide_bench M -6.7, G4 -2.7 on the same words); 0.3 kept M and G1.
WF_SCALE = float(os.environ.get("WF_SCALE", "0.3"))
SINGLE_LETTERS = {"en": {"a", "i"}, "es": {"a", "y", "o", "e", "u"}}
# Wiktionary words must be typeable on the layout (letters, long-press accents, apostrophe).
TYPEABLE_RX = re.compile(r"[a-zà-öø-ÿœæ]+(?:'[a-zà-öø-ÿœæ]+)*")
SLANG_RANK = 2500        # unknown-to-Hunspell words this common are kept (ok, gonna, ...)
BIGRAM_MIN = 2
BIGRAM_TOP = 32
BIGRAM_TOP_START = 64

# Tatoeba's example sentences are dominated by these names; keep them out of predictions.
TATOEBA_NAMES = {"tom", "mary", "john", "ken", "maría", "jack", "jim"}
# Forced display forms (Hunspell also lists lowercase "i" as a letter name).
DISPLAY_OVERRIDE = {"en": {"i": "I"}}

F_CAP, F_ALLCAPS, F_NOSUGGEST, F_CUSTOM, F_KEEP, F_RARE, F_WEAK = 1, 2, 4, 8, 16, 32, 64
# Word-list main entries this common are deliberate vocabulary (bae, nerd, ate, u): real-word
# fixes leave them alone. Rarer entries and variants stay correctable (yall -> y'all).
KEEP_MAX_TIER = 2
WORDLISTS = os.path.join(ROOT, "tools", "wordlists")
# Corpus counts for the word-list tiers: about rank 5k, 14k, 30k and 70k in the subtitles.
TIER_COUNTS = {1: 7000, 2: 1500, 3: 400, 4: 100}
WORD_RX = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)*")


def display_flags(display):
    key = display.lower()
    if display == key:
        return 0
    if display == key[:1].upper() + key[1:]:
        return F_CAP
    if display == key.upper():
        return F_ALLCAPS
    return F_CUSTOM


def tatoeba_tokens(tatoeba):
    """Yields lists of lowercase word tokens, one per clause."""
    path = os.path.join(RAW, f"{tatoeba}_sentences.tsv.bz2")
    with bz2.open(path, "rt", encoding="utf-8", errors="replace") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            text = parts[2].replace("’", "'")
            # Clause punctuation restarts the context, as if a new sentence began.
            for clause in re.split(r"[.!?;:¿¡\"()]+", text):
                toks = [t.lower() for t in WORD_RX.findall(clause)]
                if toks:
                    yield toks


def contraction_counts(tatoeba, subtitle_counts):
    """OpenSubtitles splits "don't" into "don" + "t", so contraction counts come from
    Tatoeba, scaled to the subtitle corpus by the median ratio over common words."""
    tat = Counter()
    for toks in tatoeba_tokens(tatoeba):
        tat.update(toks)
    common = [w for w, _ in tat.most_common(3000) if "'" not in w and w in subtitle_counts]
    ratios = sorted(subtitle_counts[w] / tat[w] for w in common)
    scale = ratios[len(ratios) // 2] if ratios else 1.0
    return {w: int(c * scale) for w, c in tat.items() if "'" in w and c >= 3}


def load_words(lang, cfg):
    forms = {}
    by_lower = defaultdict(list)
    for w, nosug in expand(os.path.join(RAW, f"{lang}_index.dic"), os.path.join(RAW, f"{lang}_index.aff")):
        w = w.replace("’", "'")
        if not WORD_RX.fullmatch(w):
            continue
        if w not in forms:
            by_lower[w.lower()].append(w)
        forms[w] = forms.get(w, False) or nosug

    def best_display(key):
        cands = by_lower.get(key)
        if not cands:
            return None
        if key in cands:
            return key
        for c in cands:
            if c == key[:1].upper() + key[1:]:
                return c
        return sorted(cands)[0]

    def strip_accents(w):
        return "".join(ch for ch in unicodedata.normalize("NFD", w) if unicodedata.category(ch) != "Mn")

    # Accent-less spellings of real words ("tambien", "aqui") must stay invalid so they
    # get corrected, even though they are common in subtitles.
    accentless = {strip_accents(k) for k in by_lower if strip_accents(k) != k}

    counts = {}
    rank = 0
    with open(os.path.join(RAW, f"{lang}_freq.txt"), encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if len(p) != 2:
                continue
            w, c = p[0].replace("’", "'"), int(p[1])
            if c < cfg["min_count"]:
                break
            rank += 1
            if not WORD_RX.fullmatch(w) or w.lower() in counts:
                continue
            key = w.lower()
            disp = best_display(key)
            if disp is None:
                if key in accentless or rank > SLANG_RANK or len(key) < 2 and key not in ("a", "y", "o", "e", "u"):
                    continue
                disp = key
            counts[key] = (disp, c)

    raw_counts = {k: c for k, (_, c) in counts.items()}
    for key, c in contraction_counts(cfg["tatoeba"], raw_counts).items():
        disp = best_display(key)
        if disp is None:
            continue
        old = counts.get(key, (disp, 0))[1]
        counts[key] = (disp, max(old, c))
        head = key.split("'")[0]
        if head in counts:
            hd, hc = counts[head]
            counts[head] = (hd, max(hc - c, hc // 50, cfg["min_count"]))
    overrides = DISPLAY_OVERRIDE.get(lang, {})
    for key in list(counts):
        head = key.split("'")[0]
        if head in overrides:
            disp, c = counts[key]
            counts[key] = (overrides[head] + key[len(head):], c)

    words = {}  # key -> (display, count or 0, nosuggest)
    for key, (disp, c) in counts.items():
        words[key] = (disp, c, forms.get(disp, False))
    if cfg["add_hunspell_only"]:
        for key in by_lower:
            if key not in words:
                d = best_display(key)
                words[key] = (d, 0, forms.get(d, False))
    if len(words) > cfg["max_words"]:
        keep = sorted(words.items(), key=lambda kv: -kv[1][1])[: cfg["max_words"]]
        words = dict(keep)
    return words


def merge_wordlist(lang, words):
    """Adds tools/wordlists/<lang>_*.txt (modern, ai, phone, brands, slang...; not the phrase
    list). Words only ever go up in frequency; a capitalised entry sets the display form, a
    lowercase one keeps an existing display (e.g. a name)."""
    from wordlist_files import word_files
    added, skipped, keep, listed = 0, [], set(), set()

    def put(token, tier, own_display):
        nonlocal added
        token = token.replace("’", "'")
        # "=word": show exactly this spelling, even over a capitalised name ("Ur" -> "ur").
        exact = token.startswith("=")
        token = token.lstrip("=")
        if not WORD_RX.fullmatch(token):
            skipped.append(token)  # hyphens, digits, stray apostrophes: not typeable as one word
            return
        key = token.lower()
        listed.add(key)
        c = TIER_COUNTS[tier]
        old = words.get(key)
        if old is None:
            added += 1
            words[key] = (token, c, False)
            return
        disp = token if exact or (own_display and token != key) else old[0]
        words[key] = (disp, max(old[1], c), old[2])

    for path in word_files(lang):
      with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.split("#", 1)[0].split()
            if len(p) < 3:
                continue
            main, tier = p[0], int(p[1])
            put(main, tier, own_display=True)
            if tier <= KEEP_MAX_TIER and WORD_RX.fullmatch(main.lstrip("=")):
                keep.add(main.lstrip("=").lower())
            for v in p[3:]:
                if v.lstrip("=").lower() != main.lstrip("=").lower():
                    put(v, min(4, tier + 1), own_display=False)
    return added, skipped, keep, listed


def subtitle_total(lang):
    total = 0
    with open(os.path.join(RAW, f"{lang}_freq.txt"), encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if len(p) == 2:
                total += int(p[1])
    return total


def strip_accents(w):
    return "".join(ch for ch in unicodedata.normalize("NFD", w) if unicodedata.category(ch) != "Mn")


def raise_by_wordfreq(lang, words, wf, total):
    """Gives words the subtitles never counted their wordfreq share of the corpus. Counted words
    keep their counts: raising those too (WF_RAISE=all) cost es D1 -0.8 and swipes of modern words."""
    raised = 0
    only_unknown = os.environ.get("WF_RAISE", "unknown") == "unknown"
    for key, (disp, c, nosug) in list(words.items()):
        if only_unknown and c > 0:
            continue
        wc = int(wf.get(key, 0) * total * WF_SCALE)
        if wc > c:
            words[key] = (disp, wc, nosug)
            raised += 1
    return raised


def merge_wiktionary(lang, words, wf, total):
    """Adds every Wiktionary word and inflection the dictionary lacks. Words wordfreq has seen
    get that count; the rest get 0 (flag rare)."""
    path = os.path.join(RAW, "wiktionary", f"{lang}_words.tsv")
    if not os.path.exists(path):
        return 0, 0
    displays = defaultdict(set)
    keep_all = os.environ.get("WIKT_ALL") == "1"
    with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) != 4:
                continue
            word, pos, tags, forms = p
            shared = {t for t in tags.split(",") if t and ":" not in t}
            if "misspelling" in shared or pos in WIKT_SKIP_POS:
                continue
            old = bool(shared & {"obsolete", "archaic"})
            labels = {t.split(":", 1)[-1] for t in tags.split(",") if t}
            subject = any(t.startswith("topic:") for t in tags.split(","))
            known = wordfreq_data.zipf(wf.get(word.lower(), 0)) > 0
            if not (keep_all or known or subject or labels & WIKT_USAGE_KEEP):
                continue
            for w in [word] + [x for x in forms.split(",") if x]:
                w = w.replace("’", "'")
                if not WORD_RX.fullmatch(w):
                    continue
                key = w.lower()
                if not TYPEABLE_RX.fullmatch(key):
                    continue
                if len(key) < 2 and key not in SINGLE_LETTERS[lang]:
                    continue
                if old and wordfreq_data.zipf(wf.get(key, 0)) < WIKT_OLD_MIN_ZIPF:
                    continue
                displays[key].add(w)
    # Accent-less spellings of real words ("tambien") must stay invalid so they get corrected.
    accentless = {strip_accents(k) for k in list(words) + list(displays) if strip_accents(k) != k}
    added = rare = 0
    weak = set()
    for key, ds in displays.items():
        if key in words or key in accentless:
            continue
        disp = key if key in ds else sorted(ds)[0]
        c = int(wf.get(key, 0) * total * WF_SCALE)
        words[key] = (disp, c, False)
        if c > 0 and WIKT_WEAK:
            weak.add(key)
        added += 1
        rare += c <= 0
    return added, rare, weak


class Node:
    __slots__ = ("ch", "children", "freq", "flags", "display", "idx", "parent", "maxf")

    def __init__(self, ch, parent):
        self.ch, self.parent = ch, parent
        self.children = {}
        self.freq = 0
        self.flags = 0
        self.display = None
        self.idx = -1
        self.maxf = 0


def build_trie(words, cmin, cmax, keep=frozenset(), weak=frozenset()):
    log_range = math.log(cmax / cmin)
    root = Node("\0", None)
    for key, (disp, c, nosug) in words.items():
        n = root
        for ch in key:
            nxt = n.children.get(ch)
            if nxt is None:
                nxt = n.children[ch] = Node(ch, n)
            n = nxt
        f = 1 if c <= 0 else 1 + round(254 * math.log(max(c, cmin) / cmin) / log_range)
        n.freq = max(1, min(255, f))
        n.flags = display_flags(disp) | (F_NOSUGGEST if nosug else 0) | (F_KEEP if key in keep else 0)
        if c <= 0 and key not in keep:
            n.flags |= F_RARE
        elif key in weak:
            n.flags |= F_WEAK
        if n.flags & F_CUSTOM:
            n.display = disp
    # BFS order: children of a node are contiguous.
    order = [root]
    root.idx = 0
    i = 0
    while i < len(order):
        n = order[i]
        for ch in sorted(n.children):
            c = n.children[ch]
            c.idx = len(order)
            order.append(c)
        i += 1

    for n in reversed(order):
        m = n.freq if not (n.flags & F_NOSUGGEST) else 0
        for c in n.children.values():
            m = max(m, c.maxf)
        n.maxf = m
    return root, order, log_range


def find(root, key):
    n = root
    for ch in key:
        n = n.children.get(ch)
        if n is None:
            return None
    return n if n.freq else None


def build_bigrams_from_counts(root, path_ent, path_ctx):
    """Bigrams from tools/ngram_count.py (Tatoeba + OpenSubtitles): "prev next<TAB>count"."""
    cache = {}

    def node_of(tok):
        if tok not in cache:
            n = 0 if tok == "<s>" else find(root, tok)
            cache[tok] = n if n == 0 else (n.idx if n is not None else None)
        return cache[tok]

    totals = {}
    with gzip.open(path_ctx, "rt", encoding="utf-8") as f:
        for line in f:
            ctx, tot, _ = line.rstrip("\n").split("\t")
            p = node_of(ctx)
            if p is not None:
                totals[p] = totals.get(p, 0) + int(tot)
    grouped = defaultdict(list)
    with gzip.open(path_ent, "rt", encoding="utf-8") as f:
        for line in f:
            key, c = line.rstrip("\n").split("\t")
            c = int(c)
            if c < BIGRAM_MIN:
                continue
            prev, nxt = key.split(" ")
            p, nx = node_of(prev), node_of(nxt)
            if p is None or not nx:
                continue
            grouped[p].append((c, nx))
    table = []
    for p in sorted(grouped):
        lst = sorted(grouped[p], reverse=True)[: BIGRAM_TOP_START if p == 0 else BIGRAM_TOP]
        tot = totals[p]
        table.append((p, [(nx, min(255, round(-math.log(c / tot) * 16))) for c, nx in lst]))
    return table


def build_bigrams(root, tatoeba, lang=None):
    # Tatoeba + subtitles when tools/ngram_count.py has run (BIGRAMS=tatoeba keeps the old way).
    ngrams = os.path.join(ROOT, "tools", ".cache", "ngrams")
    ent = os.path.join(ngrams, f"{lang}.o2.ent.gz")
    if lang and os.path.exists(ent) and os.environ.get("BIGRAMS") != "tatoeba":
        print(f"{lang}: bigrams from {ent}")
        return build_bigrams_from_counts(root, ent, os.path.join(ngrams, f"{lang}.o2.ctx.gz"))
    pairs = Counter()
    prev_tot = Counter()
    cache = {}

    def node_of(tok):
        if tok not in cache:
            cache[tok] = find(root, tok)
        return cache[tok]

    for toks in tatoeba_tokens(tatoeba):
        prev = 0
        for tok in toks:
            n = node_of(tok)
            if n is None:
                prev = None
                continue
            if prev is not None:
                prev_tot[prev] += 1
                if tok not in TATOEBA_NAMES:
                    pairs[(prev, n.idx)] += 1
            prev = n.idx
    grouped = defaultdict(list)
    for (p, nx), c in pairs.items():
        if c >= BIGRAM_MIN:
            grouped[p].append((c, nx))
    table = []
    for p in sorted(grouped):
        lst = sorted(grouped[p], reverse=True)[: BIGRAM_TOP_START if p == 0 else BIGRAM_TOP]
        tot = prev_tot[p]
        table.append((p, [(nx, min(255, round(-math.log(c / tot) * 16))) for c, nx in lst]))
    return table


def pad4(buf):
    while len(buf) % 4:
        buf.append(0)


def write(lang, order, log_range, word_count, bigrams):
    n = len(order)
    out = bytearray()
    out += b"BDIC" + struct.pack("<HHIfI", 1, 0, n, log_range, word_count)
    out += struct.pack(f"<{n}H", *(ord(x.ch) if x.idx else 0 for x in order))
    out += struct.pack(f"<{n}i", *(x.parent.idx if x.parent else -1 for x in order))
    first = []
    for x in order:
        if x.children:
            first.append(min(c.idx for c in x.children.values()))
        else:
            first.append(-1)
    out += struct.pack(f"<{n}i", *first)
    out += bytes(len(x.children) for x in order)
    out += bytes(x.freq for x in order)
    out += bytes(x.maxf for x in order)
    out += bytes(x.flags for x in order)
    pad4(out)
    disp = [x for x in order if x.display]
    out += struct.pack("<I", len(disp))
    for x in disp:
        b = x.display.encode("utf-8")
        out += struct.pack("<IH", x.idx, len(b)) + b
    pad4(out)
    out += struct.pack("<I", len(bigrams))
    out += struct.pack(f"<{len(bigrams)}I", *(p for p, _ in bigrams))
    starts = [0]
    for _, lst in bigrams:
        starts.append(starts[-1] + len(lst))
    out += struct.pack(f"<{len(starts)}I", *starts)
    flat = [e for _, lst in bigrams for e in lst]
    out += struct.pack(f"<{len(flat)}I", *(nx for nx, _ in flat))
    out += bytes(cost for _, cost in flat)
    pad4(out)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"{lang}.bdic")
    with open(path, "wb") as f:
        f.write(out)
    return path, len(out), len(flat)


def main():
    lang = sys.argv[1]
    cfg = CONFIG[lang]
    words = load_words(lang, cfg)
    wf = wordfreq_data.load(lang)
    total = subtitle_total(lang)
    wadded, wrare, weak = merge_wiktionary(lang, words, wf, total)
    print(f"{lang}: Wiktionary added {wadded} words ({wrare} with no frequency: rare)")
    print(f"{lang}: wordfreq raised {raise_by_wordfreq(lang, words, wf, total)} words")
    added, skipped, keep, listed = merge_wordlist(lang, words)
    print(f"{lang}: word list added {added} new words, {len(keep)} kept as typed; skipped {sorted(set(skipped))}")
    counted = [c for _, c, _ in words.values() if c > 0]
    root, order, log_range = build_trie(words, cfg["min_count"], max(counted), keep, weak - listed)
    bigrams = build_bigrams(root, cfg["tatoeba"], lang)
    path, size, pairs = write(lang, order, log_range, len(words), bigrams)
    print(f"{lang}: {len(words)} words, {len(order)} nodes, {pairs} bigrams, {size / 1e6:.1f} MB -> {path}")


if __name__ == "__main__":
    main()
