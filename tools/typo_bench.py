"""Typing-correction benchmark for the reference model in suggest_sim.py.

Sets:
  A  real typos from the user's own messages (hand-labelled)
  B  sloppy taps simulated over Tatoeba sentences: gaussian touch error around each key
     (neighbour-key hits), dropped/doubled/swapped letters, skipped apostrophes and accents,
     missed spaces and space typed as b/n/v
  C  the same sentences typed perfectly, to count correct words that get changed
  D  public misspelling lists, no touch points or context. en: Birkbeck corpora
     (wikipedia.dat, aspell.dat, missp.dat, holbrook-missp.dat) and Norvig's spell test sets; es: Spanish Wikipedia's
     "Corrector ortográfico/Listado" (CC BY-SA). Files in tools/.cache/raw/typos/, see
     tools/README.md. Only typos that aren't dictionary words, for common target words.
  E  like B but a fast, sloppy typist: wider touch error, more dropped/swapped/doubled letters
  F  phone-thumb typist: the first tap of each word is the least accurate (rhe, tjere, Ger)
  L  learned words: slips the user typed often enough to be learned (rhe) must still be
     fixed; names they confirmed must stay, and names only typed (5 times) should stay
  V  vocabulary: rare dictionary words (flag 32, Wiktionary-only) and uncommon words typed
     right must stay; the same words with one sloppy-typist slip should be fixed
  U  the user's own typo log (Settings → "Keep a typo log", Copy typo log), pasted into
     tools/.cache/raw/typos/my_typos.jsonl. Picks and edits (the word committed after an undo,
     when it still looks like a fix) are typos to fix; keeps (undone and then committed
     unchanged) are words to leave. Bare undos are skipped: often edits.

Usage: python3 -I tools/typo_bench.py en [N]            (N words per set, default 600)
       D1_HALF=1 TYPO_FILE=<list built with build_typos.py --holdout> ...   D1 on unseen items only
       CFGS='[{"SPLIT_PENALTY": 3.0}]' python3 -I tools/typo_bench.py en   (sweep)
Note: the bigrams were built from Tatoeba too, so absolute numbers are a little optimistic;
use this for comparing settings.
"""
import bz2
import json
import os
import random
import re
import sys
import unicodedata
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import suggest_sim as S  # noqa: E402
from verify_dict import Bdic  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "tools", ".cache", "raw")
WORD_RX = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)*")

# Set A: (typed, intended, previous word or None)
REAL_TYPOS = {
    "en": [
        ("instslled", "installed", "it"), ("accurste", "accurate", "super"), ("mlde", "mode", "plan"),
        ("tbis", "this", None), ("dtsge", "stage", "to"), ("staged", "stage", "next"),
        ("mutli", "multi", "a"), ("ans", "and", "a"), ("ans", "and", "cats"),
        ("troublwithe", "trouble with the", "has"), ("goolets", "good let's", "doing"),
        ("cant", "can't", "I"), ("wont", "won't", "I"), ("im", "I'm", None), ("dont", "don't", "I"),
        ("doesnt", "doesn't", "it"), ("thats", "that's", None), ("youre", "you're", None),
        ("didnt", "didn't", "I"), ("isnt", "isn't", "it"), ("ive", "I've", None),
        ("teh", "the", "of"), ("thw", "the", "in"), ("realy", "really", "I"),
        ("wierd", "weird", "so"), ("becuase", "because", None), ("thebdog", "the dog", "saw"),
        ("itsa", "it's a", None), ("ofthe", "of the", "one"), ("andthe", "and the", "me"),
        # From the user's chat messages (2026-10-27).
        ("rhe", "the", "at"), ("rhe", "the", "how"), ("rhe", "the", "see"), ("rhe", "the", None),
        ("tjere", "there", "almost"), ("ger", "get", "and"), ("suild", "build", "ok"),
        ("tunjng", "tuning", "more"), ("ans", "and", "is"),
        # From the typo log (2026-11-01).
        ("rje", "the", None), ("everythin", "everything", "love"), ("eveyr", "every", "keyboard"),
        ("necc", "necessary", "not"),
        # From the typo log (2026-11-02): fixes that were wrong, and words that must stay.
        ("wednes", "wednesday", "for"), ("opne", "open", None), ("thebapp", "the app", None),
        ("or", "or", "points"), ("w", "w", None), ("asmr", "asmr", None),
        ("anime", "anime", "favorite"), ("tjanksn", "thanks", "cool"),
    ],
    "es": [
        ("tambien", "también", "yo"), ("aqui", "aquí", "estoy"), ("manana", "mañana", "hasta"),
        ("cancion", "canción", "una"), ("graicas", "gracias", None), ("nesesito", "necesito", None),
        ("hcaer", "hacer", "que"), ("qeu", "que", "creo"), ("deque", "de que", "seguro"),
        ("estoi", "estoy", None), ("porfavor", "por favor", None), ("despues", "después", None),
    ],
}

TATOEBA = {"en": "eng", "es": "spa"}
SPACE_SLIP = "bnv"


def strip_accent(ch):
    d = unicodedata.normalize("NFD", ch)
    return d[0] if d and d[0] != ch and ch != "ñ" else ch


def load_sentences(d, lang, n_words, rng):
    """Held-out-ish sample: every 37th sentence whose words are all in the dictionary."""
    out, words = [], 0
    path = os.path.join(RAW, f"{TATOEBA[lang]}_sentences.tsv.bz2")
    with bz2.open(path, "rt", encoding="utf-8", errors="replace") as f:
        for i, line in enumerate(f):
            if i % 37 != int(os.environ.get("OFFSET", "0")):
                continue
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            toks = [t.lower() for t in WORD_RX.findall(parts[2].replace("’", "'"))]
            if len(toks) < 3 or any(d.find(t) is None for t in toks):
                continue
            out.append(toks)
            words += len(toks)
            if words >= n_words:
                break
    return out


class Typist:
    """Simulated sloppy thumb typist."""

    def __init__(self, lang, rng, sigma_x=0.28, sigma_y=0.22, slips=(0.015, 0.025, 0.03), first=1.0):
        self.centers = S.key_centers(lang)
        self.rng = rng
        self.sx, self.sy = sigma_x, sigma_y
        self.slips = slips  # cumulative chance of a dropped / swapped / doubled letter
        self.first = first  # touch-error scale of a word's first tap (the thumb is still moving)

    def nearest(self, x, y):
        return min(self.centers, key=lambda k: (self.centers[k][0] - x) ** 2 + (self.centers[k][1] - y) ** 2)

    def type_word(self, word):
        """Returns (typed, taps)."""
        rng = self.rng
        chars = []
        for ch in word:
            if ch == "'" and rng.random() < 0.6:
                continue  # apostrophes are often skipped
            if ch not in self.centers and ch != "'":
                base = strip_accent(ch)
                ch = base if rng.random() < 0.7 else ch  # accents often skipped
            chars.append(ch)
        r = rng.random()
        p_drop, p_swap, p_double = self.slips
        if len(chars) > 3 and r < p_drop:
            del chars[rng.randrange(1, len(chars))]
        elif len(chars) > 3 and r < p_swap:
            i = rng.randrange(1, len(chars) - 1)
            chars[i], chars[i + 1] = chars[i + 1], chars[i]
        elif len(chars) > 2 and r < p_double:
            i = rng.randrange(1, len(chars))
            chars.insert(i, chars[i - 1])
        typed, taps = [], []
        for i, ch in enumerate(chars):
            c = self.centers.get(ch)
            if c is None:
                typed.append(ch)
                taps.append(None)
                continue
            k = self.first if i == 0 else 1.0
            x, y = c[0] + rng.gauss(0, self.sx * k), c[1] + rng.gauss(0, self.sy * k)
            typed.append(self.nearest(x, y))
            taps.append((x, y))
        return "".join(typed), taps


def build_noisy(d, lang, sentences, rng, typist=None):
    """Items: (typed, taps, target, prev_word)."""
    t = typist or Typist(lang, rng)
    items = []
    for toks in sentences:
        i = 0
        while i < len(toks):
            prev = toks[i - 1] if i else None
            w = toks[i]
            typed, taps = t.type_word(w)
            target = w
            r = rng.random()
            if i + 1 < len(toks) and r < 0.05:
                # Missed space (or a letter next to the spacebar instead of it).
                w2 = toks[i + 1]
                t2, taps2 = t.type_word(w2)
                if r < 0.015:
                    slip = rng.choice(SPACE_SLIP)
                    typed, taps = typed + slip + t2, taps + [t.centers[slip]] + taps2
                else:
                    typed, taps = typed + t2, taps + taps2
                target = w + " " + w2
                i += 1
            items.append((typed, taps, target, prev))
            i += 1
    return items


TYPOS = os.path.join(RAW, "typos")
LIST_MIN_FREQ = 60  # target words at least this common (dictionary frequency 0-255)


def edit_distance(a, b):
    row = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, row[0] = row[0], i
        for j, cb in enumerate(b, 1):
            prev, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, prev + (ca != cb))
    return row[-1]


def read_birkbeck(name):
    """Birkbeck format: "$correct" lines, each followed by its misspellings."""
    pairs, target = [], None
    path = os.path.join(TYPOS, name)
    if not os.path.exists(path):
        return pairs
    with open(path, encoding="latin-1") as f:
        for line in f:
            w = line.strip().split(" ")[0]
            if not w:
                continue
            if w.startswith("$"):
                target = w[1:]
            elif target:
                pairs.append((w, target))
    return pairs


def read_norvig(name):
    """Norvig's spell test sets: "right: wrong1 wrong2" lines."""
    pairs = []
    path = os.path.join(TYPOS, name)
    if not os.path.exists(path):
        return pairs
    with open(path, encoding="utf-8") as f:
        for line in f:
            if ":" not in line:
                continue
            right, wrongs = line.split(":", 1)
            pairs += [(w, right.strip()) for w in wrongs.split()]
    return pairs


def read_es_wiki():
    """ " typo|param|suggestion (note), other" lines; keeps single, unexplained suggestions."""
    pairs = []
    path = os.path.join(TYPOS, "es_list.json")
    if not os.path.exists(path):
        return pairs
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.startswith(" ") or line.count("|") != 2:
                continue
            typo, _, sugg = line.strip().split("|")
            if "," in sugg or "(" in sugg or " " in sugg.strip():
                continue
            pairs.append((typo.strip(), sugg.strip()))
    return pairs


def load_lists(d, lang, n, rng, hard=False):
    """Set D items: (typed, taps=None, target, prev=None). en: D1 = adult typos
    (wikipedia.dat, Norvig's sets); D2 (hard=True) = spelling errors (aspell, missp, holbrook; partly
    children's)."""
    if lang == "en":
        if hard:
            raw = read_birkbeck("aspell.dat") + read_birkbeck("missp.dat") + read_birkbeck("holbrook-missp.dat")
        else:
            raw = (read_birkbeck("wikipedia.dat") + read_norvig("norvig-spell-testset1.txt")
                   + read_norvig("norvig-spell-testset2.txt"))
    else:
        raw = [] if hard else read_es_wiki()
    seen, items = set(), []
    for typo, target in raw:
        t, w = typo.lower(), target.lower()
        if t == w or (t, w) in seen or not WORD_RX.fullmatch(t) or len(t) < 3:
            continue
        seen.add((t, w))
        node = d.find(w)
        if node is None or d.freq[node] < LIST_MIN_FREQ or d.find(t) is not None:
            continue
        if edit_distance(t, w) > 3:
            continue
        # D1_HALF=1: only the half build_typos.py --holdout leaves out of the typo list.
        if os.environ.get("D1_HALF") == "1" and zlib.crc32(t.encode()) % 2 != 0:
            continue
        items.append((t, None, w, None))
    rng.shuffle(items)
    return items[:n]


def looks_like_fix(typed, chosen):
    """Rough check that chosen could be what typed was meant to be (an "edit" log line)."""
    if " " in chosen:
        chosen = chosen.replace(" ", "")
    if abs(len(typed) - len(chosen)) > max(2, len(typed) // 3):
        return False
    common = sum(min(typed.count(c), chosen.count(c)) for c in set(typed))
    return common >= max(1, (len(typed) * 6) // 10)


def load_user_log(lang):
    """Set U items from the phone's typo log, touch offsets replayed on our key centres."""
    path = os.path.join(TYPOS, "my_typos.jsonl")
    if not os.path.exists(path):
        return []
    centers = S.key_centers(lang)
    items = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if r.get("lang") != lang:
                continue
            # An undo may just be the start of an edit; only "keep" (the undone word was then
            # committed unchanged) says the typing was meant.
            if r["k"] not in ("pick", "keep", "edit"):
                continue
            typed = r["typed"].lower()
            # An edit can also be a different word typed after deleting the whole thing: only
            # keep it when it still looks like a fix of what was typed.
            if r["k"] == "edit" and not looks_like_fix(typed, r["chosen"].lower()):
                continue
            target = r["typed"] if r["k"] == "keep" else r["chosen"]
            taps = None
            if r.get("taps") and len(r["taps"]) == len(typed):
                taps = []
                for ch, off in zip(typed, r["taps"]):
                    c = centers.get(ch)
                    taps.append((c[0] + off[0], c[1] + off[1]) if c and off else None)
            items.append((typed, taps, target, r.get("prev")))
    return items


def load_modern(d, lang):
    """Every spelling in tools/wordlists/<lang>_modern.txt that made it into the dictionary,
    as one-word sentences: typed as-is they must stay, and with typos they should come back."""
    from wordlist_files import word_files
    out = []
    for path in word_files(lang):
      with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.split("#", 1)[0].split()
            if len(p) < 3:
                continue
            for t in [p[0]] + p[3:]:
                w = t.lstrip("=").lower()
                if d.find(w) is not None and [w] not in out:
                    out.append([w])
    return out


def evaluate(d, lang, items):
    fixed = changed_ok = broken = noisy = clean = in_bar = bad_split = 0
    for typed, taps, target, prev in items:
        _, auto, top = S.suggest(d, lang, typed, prev, taps)
        out = auto if auto is not None else typed
        ok = out.lower() == target.lower()
        bad_split += " " in out and " " not in target
        if typed.lower() == target.lower():
            clean += 1
            if not ok:
                broken += 1
        else:
            noisy += 1
            fixed += ok
            in_bar += ok or target.lower() in [w.lower() for w, _, _ in top[:3]]
    return {
        "noisy": noisy, "fixed": fixed / max(noisy, 1), "in_bar": in_bar / max(noisy, 1),
        "clean": clean, "false_change": broken / max(clean, 1), "bad_split": bad_split,
    }


# Set L: names and coinages a user might type that no dictionary has.
NAMES = {
    "en": ["jaylen", "kaito", "brienne", "zeph", "taliyah", "oakley", "bori", "nyla", "dashawn", "kenji",
           "anaya", "rhiannon", "saoirse", "xochitl", "yeet'd", "glowup", "vibey", "skrrt", "lowkeyy", "jhene"],
    "es": ["yamilet", "xiomara", "jhonny", "borinquen", "nayeli", "yadiel", "coquí", "jibarito", "wepa", "mamey"],
}


def run_learned(d, lang):
    """(slips fixed although learned, confirmed names kept, typed-only names kept), as shares."""
    slips = [(t, w, p) for t, w, p in REAL_TYPOS[lang] if d.find(t.lower()) is None and " " not in w]
    fixed = sum((S.suggest(d, lang, t, p, user={t.lower(): (12, 0)})[1] or t).lower() == w.lower()
                for t, w, p in slips)
    names = [n for n in NAMES[lang] if d.find(n) is None]
    kept = sum(S.suggest(d, lang, n, None, user={n: (3, 1)})[1] is None for n in names)
    typed_only = sum(S.suggest(d, lang, n, None, user={n: (5, 0)})[1] is None for n in names)
    return fixed / max(len(slips), 1), kept / max(len(names), 1), typed_only / max(len(names), 1)


def load_vocab(d, lang, n, rng):
    """Set V: (rare words, uncommon words), n each, lowercase single words of 4+ letters."""
    rare, uncommon = [], []
    for node in range(1, d.n):
        f = d.freq[node]
        if not f or d.flags[node] & 4 or d.flags[node] & 8:
            continue
        if d.flags[node] & 32:
            rare.append(node)
        elif f < 110:
            uncommon.append(node)
    out = []
    for group in (rare, uncommon):
        rng.shuffle(group)
        words = []
        for node in group:
            w = d.word(node)
            if w.islower() and w.isalpha() and len(w) >= 4:
                words.append([w])
            if len(words) >= n:
                break
        out.append(words)
    return out


def run_real(d, lang):
    ok = 0
    lines = []
    for typed, target, prev in REAL_TYPOS[lang]:
        _, auto, top = S.suggest(d, lang, typed, prev)
        out = auto if auto is not None else typed
        good = out.lower() == target.lower()
        ok += good
        lines.append(f"  {'✓' if good else '✗'} {typed:16} → {out:18} (want {target})")
    return ok / len(REAL_TYPOS[lang]), lines


def main():
    lang = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 600
    # BDIC=<path with {lang}> benchmarks another dictionary build (e.g. the previous one).
    d = Bdic(os.environ.get("BDIC", os.path.join(ROOT, "app", "src", "main", "assets", "dict", "{lang}.bdic")).format(lang=lang))
    S.TYPO_FILE = os.environ.get("TYPO_FILE") or None
    rng = random.Random(42)
    sentences = load_sentences(d, lang, n, rng)
    noisy = build_noisy(d, lang, sentences, random.Random(1))
    rng_fast = random.Random(2)
    fast = build_noisy(d, lang, sentences, rng_fast,
                       Typist(lang, rng_fast, 0.38, 0.3, (0.04, 0.07, 0.09)))
    rng_thumb = random.Random(5)
    thumb = build_noisy(d, lang, sentences, rng_thumb, Typist(lang, rng_thumb, 0.3, 0.24, first=1.6))
    lists = load_lists(d, lang, n, random.Random(3))
    hard = load_lists(d, lang, n, random.Random(4), hard=True)
    mine = load_user_log(lang)
    modern = load_modern(d, lang)
    modern_clean = [(w[0], None, w[0], None) for w in modern]
    modern_noisy = build_noisy(d, lang, modern, random.Random(6))
    vocab = load_vocab(d, lang, int(os.environ.get("VOCAB_N", "150")), random.Random(7))
    vocab_noisy = [build_noisy(d, lang, g, random.Random(8)) for g in vocab]
    clean = [(" ".join([w]), None, w, (toks[i - 1] if i else None))
             for toks in sentences for i, w in enumerate(toks)]
    cfgs = json.loads(os.environ.get("CFGS", "[{}]"))
    for cfg in cfgs:
        for k, v in cfg.items():
            setattr(S, k, v)
        S._piece_cache.clear()
        real, lines = run_real(d, lang)
        rb = evaluate(d, lang, noisy)
        rc = evaluate(d, lang, clean)
        rd = evaluate(d, lang, lists) if lists else None
        rh = evaluate(d, lang, hard) if hard else None
        re_ = evaluate(d, lang, fast)
        rf = evaluate(d, lang, thumb)
        ru = evaluate(d, lang, mine) if mine else None
        rm = evaluate(d, lang, modern_clean) if modern else None
        rmn = evaluate(d, lang, modern_noisy) if modern else None
        rl = run_learned(d, lang)
        rv = [evaluate(d, lang, [(w[0], None, w[0], None) for w in g]) for g in vocab]
        rvn = [evaluate(d, lang, g) if g else None for g in vocab_noisy]
        print(f"{cfg}  A(real)={real:.0%}  B: typos {rb['noisy']} fixed {rb['fixed']:.1%} in-bar {rb['in_bar']:.1%}"
              f" broke-correct {rb['false_change']:.1%}  C: false-change {rc['false_change']:.2%} of {rc['clean']}"
              + (f"  D1(typo lists): {rd['noisy']} fixed {rd['fixed']:.1%} in-bar {rd['in_bar']:.1%}"
                 f" bad-split {rd['bad_split']}" if rd else "  D1: no data")
              + (f"  D2(hard spelling): fixed {rh['fixed']:.1%} in-bar {rh['in_bar']:.1%} bad-split {rh['bad_split']}"
                 if rh else "")
              + f"  E(fast): {re_['noisy']} fixed {re_['fixed']:.1%} in-bar {re_['in_bar']:.1%}"
              f" broke-correct {re_['false_change']:.1%}"
              f"  F(thumb): {rf['noisy']} fixed {rf['fixed']:.1%} in-bar {rf['in_bar']:.1%}"
              f" broke-correct {rf['false_change']:.1%}"
              + (f"  U(mine): typos {ru['noisy']} fixed {ru['fixed']:.1%} in-bar {ru['in_bar']:.1%}"
                 f" kept-words broken {ru['false_change']:.1%}" if ru else "")
              + (f"  M(modern words): {rm['clean']} typed right, changed {rm['false_change']:.1%};"
                 f" {rmn['noisy']} typos fixed {rmn['fixed']:.1%} in-bar {rmn['in_bar']:.1%}" if rm else "")
              + f"  L(learned): slips fixed {rl[0]:.0%}, confirmed kept {rl[1]:.0%}, typed-only kept {rl[2]:.0%}"
              + "".join(f"  V({name}): {v['clean']} typed right, changed {v['false_change']:.1%};"
                        f" {vn['noisy']} typos fixed {vn['fixed']:.1%} in-bar {vn['in_bar']:.1%}"
                        for name, v, vn in zip(("rare", "uncommon"), rv, rvn) if vn),
              flush=True)
        if len(cfgs) == 1:
            print("\n".join(lines))


if __name__ == "__main__":
    main()
