"""Reference model of the app's spell corrector + ranker (predict/SpellCorrector.kt and
predict/Suggester.kt), used to tune constants against real typos without a phone.
Keep the constants in sync with the Kotlin code.

Usage: python3 -I tools/suggest_sim.py en [typo[:prev] ...]   (no words = built-in test set)
"""
import math
import os
import sys
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from verify_dict import Bdic  # noqa: E402

ROWS = {"en": ["qwertyuiop", "asdfghjkl", "zxcvbnm"], "es": ["qwertyuiop", "asdfghjklñ", "zxcvbnm"]}

# --- constants mirrored in Kotlin ---
SUB_ACCENT = 0.15
SUB_FIRST_EXTRA = 0.15
SUB_LAST_DISCOUNT = 0.15     # neighbour key on the last letter of a 5+ letter word
INS_APOSTROPHE = 0.1
INS_OTHER = 1.0
INS_DOUBLE = 0.5
DEL_FIRST_EXTRA = 0.3
DEL_APOSTROPHE = 0.3
DEL_DOUBLE = 0.5
DEL_OTHER = 0.9
TRANSPOSE = 0.65
COST_WEIGHT = 4.5
COMPLETION_PENALTY = 1.5
COMPLETION_PER_CHAR = 0.1
BIGRAM_WEIGHT = 0.65
BIGRAM_MISSING = 0.65  # unigram factor when the previous word has no stored pair
UNIGRAM_OFFSET = 4.0
# Real-word fixes ("ans" -> "and"): a valid word is replaced when a cheap edit beats it by a margin.
REALFIX_MAX_COST = 1.0
REALFIX_RARE_FREQ = 170      # below this frequency a valid word counts as a rare look-alike
REALFIX_MARGIN_RARE = 1.0
REALFIX_MARGIN_COMMON = 2.5  # common pairs (its/it's) also need previous-word bigram support
# Missed-space splits ("troublwithe" -> "trouble with the").
SPLIT_PENALTY = 2.5          # per extra word
SPLIT_PIECE_MAX_COST = 0.7   # each piece must be an exact word or a cheap fix
SPACE_KEYS = "bnvcm"         # letters next to the spacebar, read as a missed space
SPACE_KEY_COST = 0.3
SPLIT_MIN_FREQ = 140         # each piece must be at least this common
SPLIT_SHORT_MIN_FREQ = 170   # two-letter pieces must be at least this common ("er", "en" aren't)
SPLIT_MARGIN = 0.0           # split must beat the best single-word reading by this much
SPLIT_EXACT_ENTRIES = True   # False: never split dictionary words typed exactly (cost F set 2 items in es)
LANG = "en"  # set by suggest()
CAPITAL_PIECE_MIN_FREQ = 256  # other capitalised words allowed as split pieces only this common (never)
CAPITAL_PIECES = {"en": {"i", "i'm", "i'll", "i've", "i'd"}, "es": set()}  # capitalised split pieces allowed
# A completion as autocorrect ("wednes" -> Wednesday): long unfinished word, exact prefix, and the
# completion beats the best fix by a margin while that fix is costly.
AUTO_COMPLETE_MIN_LENGTH = 5
AUTO_COMPLETE_MARGIN = 1.0
AUTO_COMPLETE_FIX_COST = 0.9
REALFIX_SHORT_COMMON_FREQ = 170  # two-letter words this common are never real-word fixed (or/of)
AUTO_COST_2 = 1.0            # autocorrect cost limit for 1-2 letter words (Normal)
SINGLE_LETTER_WORDS = {"en": {"a", "i"}, "es": {"a", "y", "o", "e", "u"}}


MAX_COST_MID = 2.5           # search limit for 8-10 letter words
MAX_COST_LONG = 3.0         # search limit for 11+ letter words


# Typo list (tools/build_typos.py -> assets/dict/<lang>_typos.txt): a listed non-word is offered
# as its word with this cost and score bonus. TYPO_FILE overrides the path (for --holdout lists).
USE_TYPO_LIST = True
TYPO_FILE = None
TYPO_LIST_COST = 0.3
TYPO_LIST_BONUS = 0.5
# Rare words (dictionary flag 32: from Wiktionary with no corpus frequency) are only weakly
# valid: typed exactly they stay unless a fix outscores them plus this bonus. A listed typo that
# happens to be a rare word is treated as a typo.
F_RARE = 32
F_WEAK = 64   # added from Wiktionary with some frequency: weakly valid too, but swipeable
RARE_TYPED_BONUS = 3.0
# User words: ln P of a learned word the dictionary lacks, before USER_WORD_BOOST * ln(1 + count).
LEARNED_BASE = -11.0
USER_WORD_BOOST = 0.6
LEARN_AFTER = 2
_typo_cache = {}


def typo_list(lang):
    path = TYPO_FILE or os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                     "app", "src", "main", "assets", "dict", f"{lang}_typos.txt")
    if path not in _typo_cache:
        out = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    if line.startswith("#"):
                        continue
                    t, _, w = line.rstrip("\n").partition("\t")
                    if w:
                        out[t] = w
        _typo_cache[path] = out
    return _typo_cache[path]


def max_cost(n):
    if n >= 11:
        return MAX_COST_LONG
    if n >= 8:
        return MAX_COST_MID
    return 1.0 if n <= 2 else 1.3 if n <= 4 else 2.0


AUTO_COST_LONG = 2.0         # words of AUTO_LONG_FROM+ letters
AUTO_LONG_FROM = 7


AUTO_COST_MID = 2.3          # 8-10 letters
AUTO_COST_VLONG = 2.7        # 11+ letters


def auto_cost(n):  # Normal aggressiveness
    if n >= 11:
        return AUTO_COST_VLONG
    if n >= 8:
        return AUTO_COST_MID
    if n >= AUTO_LONG_FROM:
        return AUTO_COST_LONG
    return AUTO_COST_2 if n <= 2 else 1.0 if n <= 3 else 1.3 if n <= 4 else 1.6


def key_centers(lang):
    c = {}
    for r, row in enumerate(ROWS[lang]):
        off = (10 - len(row)) / 2 + (1.5 if r == 2 else 0)
        for i, ch in enumerate(row):
            c[ch] = (off + i + 0.5, r + 0.5)
    return c


def base_of(ch):
    if "a" <= ch <= "z":
        return ch
    d = unicodedata.normalize("NFD", ch)
    return d[0].lower() if d else ch


def sub_cost(typed, i, c, centers, taps=None):
    t = typed[i]
    if c == t:
        return 0.0
    extra = SUB_FIRST_EXTRA if i == 0 else 0.0
    if base_of(c) == base_of(t):
        return SUB_ACCENT + extra
    tc = taps[i] if taps is not None and taps[i] is not None else centers.get(t)
    cc = centers.get(base_of(c))
    if tc is None or cc is None:
        return 1.0 + extra
    d = math.hypot(tc[0] - cc[0], tc[1] - cc[1])
    near = min(1.1, max(0.45, 0.35 + 0.4 * d))
    # The last tap of a long word is often rushed (backgrounda -> backgrounds).
    if i == len(typed) - 1 and len(typed) >= 5 and d <= 1.2:
        near -= SUB_LAST_DISCOUNT
    return near + extra


def ins_cost(c, pchar):
    if c == "'":
        return INS_APOSTROPHE
    return INS_DOUBLE if c == pchar else INS_OTHER


def del_cost(typed, i):
    if typed[i] == "'":
        return DEL_APOSTROPHE
    if i > 0 and typed[i] == typed[i - 1]:
        return DEL_DOUBLE
    return DEL_OTHER + (DEL_FIRST_EXTRA if i == 0 else 0.0)


def search(d, typed, centers, taps=None, limit=None):
    m = len(typed)
    limit = max_cost(m) if limit is None else limit
    prefix_limit = min(limit, 1.0)
    max_depth = m + 3
    row0 = [0.0] * (m + 1)
    for i in range(1, m + 1):
        row0[i] = row0[i - 1] + del_cost(typed, i - 1)
    matches, prefixes = {}, {}

    def visit(node, depth, prev_row, prev2_row, pchar):
        f, cnt = d.first[node], d.count[node]
        for child in range(f, f + cnt) if cnt else ():
            c = chr(d.chars[child])
            row = [0.0] * (m + 1)
            row[0] = prev_row[0] + ins_cost(c, pchar)
            for i in range(1, m + 1):
                v = min(prev_row[i - 1] + sub_cost(typed, i - 1, c, centers, taps),
                        prev_row[i] + ins_cost(c, pchar),
                        row[i - 1] + del_cost(typed, i - 1))
                if i >= 2 and prev2_row is not None and typed[i - 1] == pchar and typed[i - 2] == c:
                    v = min(v, prev2_row[i - 2] + TRANSPOSE)
                row[i] = v
            if min(row) > limit:
                continue
            full = row[m]
            if full <= limit and d.freq[child]:
                matches[child] = min(full, matches.get(child, 9e9))
            if depth + 1 in (m - 1, m) and full <= prefix_limit:
                prefixes[child] = min(full, prefixes.get(child, 9e9))
            if depth + 1 < max_depth:
                visit(child, depth + 1, row, prev_row, c)

    visit(0, 0, row0, None, None)
    return matches, prefixes


def top_descendants(d, node, k, min_depth, depth):
    import heapq
    heap = [(-d.maxf[node], 0, node, depth)]
    out = []
    while heap and len(out) < k:
        neg, is_word, n, dep = heapq.heappop(heap)
        if is_word:
            out.append((n, dep))
            continue
        if d.freq[n] and not (d.flags[n] & 4) and dep > min_depth:
            heapq.heappush(heap, (-d.freq[n], 1, n, dep))
        f, cnt = d.first[n], d.count[n]
        for ch in range(f, f + cnt) if cnt else ():
            if d.maxf[ch]:
                heapq.heappush(heap, (-d.maxf[ch], 0, ch, dep + 1))
    return out


def depth_of(d, node):
    n, k = node, 0
    while n > 0:
        n, k = d.parent[n], k + 1
    return k


def lm(d, node, prev):
    pu = math.exp((d.freq[node] / 255 - 1) * d.log_range - UNIGRAM_OFFSET)
    if prev is None or prev < 0:
        return math.log(pu)
    pb = None
    import bisect
    i = bisect.bisect_left(d.prev, prev)
    if i < len(d.prev) and d.prev[i] == prev:
        for j in range(d.start[i], d.start[i + 1]):
            if d.next[j] == node:
                pb = math.exp(-d.cost[j] / 16)
    if pb is None:
        # The stored pairs are pruned, so a missing pair is weak evidence against a word.
        return math.log(BIGRAM_MISSING * pu)
    return math.log(BIGRAM_WEIGHT * pb + (1 - BIGRAM_WEIGHT) * pu)


def prev_node_of(d, prev_word):
    if prev_word is None:
        return 0  # sentence start
    n = d.find(prev_word.lower())
    return n if n is not None else -1


def has_bigram(d, prev, node):
    import bisect
    if prev is None or prev < 0:
        return False
    i = bisect.bisect_left(d.prev, prev)
    return i < len(d.prev) and d.prev[i] == prev and node in d.next[d.start[i]:d.start[i + 1]]


def suggest(d, lang, typed, prev_word=None, taps=None, level=1.0, user=None):
    """Returns (valid, auto, top). auto is the text space would commit instead of typed
    (a word, or several words for a missed-space split), or None. user: {word: (count, kept)}
    for words the user has typed (kept = times a fix of it was undone or it was picked as typed)."""
    global LANG
    LANG = lang
    centers = key_centers(lang)
    typed = typed.lower()
    prev = prev_node_of(d, prev_word)
    matches, prefixes = search(d, typed, centers, taps)
    cands = {}
    for node, cost in matches.items():
        if d.flags[node] & 4:
            continue
        cands[node] = (lm(d, node, prev) - COST_WEIGHT * cost, cost, "exact" if cost == 0 else "corr")
    m = len(typed)
    for pnode, pcost in sorted(prefixes.items(), key=lambda kv: kv[1])[:12]:
        for node, dep in top_descendants(d, pnode, 3, m, depth_of(d, pnode)):
            if node in cands:
                continue
            extra = dep - m
            s = lm(d, node, prev) - COST_WEIGHT * pcost - COMPLETION_PENALTY - COMPLETION_PER_CHAR * extra
            cands[node] = (s, pcost, "comp")
    typed_node = d.find(typed)
    rare_typed = typed_node is not None and d.flags[typed_node] & (F_RARE | F_WEAK)
    if rare_typed and USE_TYPO_LIST and typed in typo_list(lang):
        cands.pop(typed_node, None)
        typed_node = None
    if (typed_node is None or rare_typed) and USE_TYPO_LIST:
        fix = typo_list(lang).get(typed)
        node = d.find(fix.lower()) if fix else None
        if node is not None and not d.flags[node] & 4:
            sc = lm(d, node, prev) - COST_WEIGHT * TYPO_LIST_COST + TYPO_LIST_BONUS
            if node not in cands or sc > cands[node][0]:
                cands[node] = (sc, TYPO_LIST_COST, "corr")
    ranked = sorted(cands.items(), key=lambda kv: -kv[1][0])
    valid = typed_node is not None and not rare_typed
    # A word the user only ever typed (never confirmed) still loses to a likelier fix: slips
    # typed twice ("rhe") were learned too. Confirmed words are never fixed.
    learned_score = None
    if rare_typed and typed_node is not None:
        learned_score = lm(d, typed_node, prev) + RARE_TYPED_BONUS
    if not valid and user and typed in user:
        count, kept = user[typed]
        if kept >= 1:
            return True, None, [(d.word(n), round(sc, 2), k) for n, (sc, _, k) in ranked[:5]]
        if count >= LEARN_AFTER:
            learned_score = max(learned_score or -1e9, LEARNED_BASE + USER_WORD_BOOST * math.log(1 + count))
    auto = None
    best = next(((n, sc, c) for n, (sc, c, k) in ranked if k != "comp" and n != typed_node), None)
    if valid:
        # Real-word fixes only swap near keys, swap letters or add an apostrophe: never drop or
        # add letters, so plurals and other real forms ("reasons", "seep") are left alone.
        letters = len(typed.replace("'", ""))
        same = next(((n, sc, c) for n, (sc, c, k) in ranked if k != "comp" and n != typed_node
                     and len(d.word(n).replace("'", "")) == letters
                     and d.word(n).count("'") >= typed.count("'")), None)
        # Everyday word-list words (flag 16: bae, nerd, ate) are kept as typed.
        auto = None if d.flags[typed_node] & 16 else real_word_fix(d, typed, typed_node, prev, same, level)
    else:
        # Only the best-ranked correction may be applied; never fall back to a worse one.
        top = ranked[0] if ranked else None
        if (top is not None and top[1][2] == "comp" and m >= AUTO_COMPLETE_MIN_LENGTH and level >= 1.0
                and top[1][1] == 0 and (learned_score is None or top[1][0] > learned_score)
                and (best is None or (top[1][0] - best[1] >= AUTO_COMPLETE_MARGIN and best[2] >= AUTO_COMPLETE_FIX_COST))):
            auto = d.word(top[0])
        elif best is not None and (learned_score is None or best[1] > learned_score):
            node, score, cost = best
            w = d.word(node).lower()
            # "hel" -> "he": only trailing letters dropped; the user was probably mid-word.
            if cost <= auto_cost(m) * level and not (m <= 4 and typed.startswith(w)):
                auto = d.word(node)
        # A dictionary word typed exactly ("asmr" = ASMR) is never split ("as Mr").
        split = best_split(d, lang, typed, prev, centers, taps) if typed_node is None or SPLIT_EXACT_ENTRIES else None
        if split is not None:
            words, sscore = split
            single = max(best[1] if best is not None else -1e9,
                         learned_score if learned_score is not None else -1e9)
            if sscore > single + SPLIT_MARGIN:
                auto = " ".join(words)
    return valid, auto, [(d.word(n), round(s, 2), k) for n, (s, _, k) in ranked[:5]]


def real_word_fix(d, typed, typed_node, prev, best, level):
    """A valid word that is probably a slip ("ans" -> "and", "I cant" -> "I can't")."""
    if best is None or level <= 0:
        return None
    node, score, cost = best
    letters = len(typed.replace("'", ""))
    if letters == 2 and d.freq[typed_node] >= REALFIX_SHORT_COMMON_FREQ:
        return None
    if cost > REALFIX_MAX_COST or d.word(node).lower() == typed:
        return None
    typed_score = lm(d, typed_node, prev)
    rare = d.freq[typed_node] < REALFIX_RARE_FREQ
    margin = (REALFIX_MARGIN_RARE if rare else REALFIX_MARGIN_COMMON) / level
    if not rare and not has_bigram(d, prev, node):
        return None
    return d.word(node) if score - typed_score >= margin else None


_piece_cache = {}


def best_piece(d, lang, sub, prev, centers, taps):
    """Best word for a fragment of a merged word: (node, cost) or None."""
    key = (id(d), sub, prev)
    if key in _piece_cache:
        return _piece_cache[key]
    out = None
    def usable(node):
        # Common, suggestible, ordinary words only: no "GI", "PE", "Ur" abbreviations.
        return (d.freq[node] >= (SPLIT_SHORT_MIN_FREQ if len(d.word(node)) == 2 else SPLIT_MIN_FREQ)
                and not (d.flags[node] & (2 | 4 | 8))
                and (not d.flags[node] & 1 or d.word(node).lower() in CAPITAL_PIECES[lang]
                     or d.freq[node] >= CAPITAL_PIECE_MIN_FREQ)
                and (len(sub) > 1 or d.word(node).lower() in SINGLE_LETTER_WORDS[lang]))
    n = d.find(sub)
    if n is not None and usable(n):
        out = (n, 0.0)
    elif len(sub) >= 3:
        matches, _ = search(d, sub, centers, taps, limit=SPLIT_PIECE_MAX_COST)
        best = None
        for node, cost in matches.items():
            if not usable(node):
                continue
            sc = lm(d, node, prev) - COST_WEIGHT * cost
            if best is None or sc > best[0]:
                best = (sc, node, cost)
        if best:
            out = (best[1], best[2])
    _piece_cache[key] = out
    return out


def best_split(d, lang, typed, prev, centers, taps):
    """Missed space: the best way to read typed as 2 (or 3) words, with its score."""
    m = len(typed)
    if m < 4:
        return None
    best = None

    def piece_taps(lo, hi):
        return taps[lo:hi] if taps is not None else None

    cuts = []
    for i in range(1, m):
        cuts.append((i, i, 0.0))                      # missing space between i-1 and i
        if typed[i] in SPACE_KEYS and 0 < i < m - 1:
            cuts.append((i, i + 1, SPACE_KEY_COST))   # letter next to space typed instead of it
    for lo_end, hi_start, extra in cuts:
        left, right = typed[:lo_end], typed[hi_start:]
        p1 = best_piece(d, lang, left, prev, centers, piece_taps(0, lo_end))
        if p1 is None:
            continue
        n1, c1 = p1
        options = []
        p2 = best_piece(d, lang, right, n1, centers, piece_taps(hi_start, m))
        if p2 is not None:
            options.append(([n1, p2[0]], c1 + p2[1] + extra))
        if m >= 8 and len(right) >= 3:
            sub = best_split(d, lang, right, n1, centers, piece_taps(hi_start, m))
            if sub is not None:
                options.append((None, sub, c1 + extra))
        for opt in options:
            if opt[0] is None:
                words2, sc2 = opt[1]
                score = lm(d, n1, prev) - COST_WEIGHT * opt[2] + sc2 - SPLIT_PENALTY
                words = [d.word(n1)] + words2
            else:
                (a, b), cost = opt
                score = lm(d, a, prev) + lm(d, b, a) - COST_WEIGHT * cost - SPLIT_PENALTY
                words = [d.word(a), d.word(b)]
            if best is None or score > best[1]:
                best = (words, score)
    return best


TESTS = {
    "en": ["teh", "recieve", "dont", "im", "hellp", "thx", "becuase", "wierd", "tomorow", "definately",
           "goin", "realy", "happyness", "thw", "yuo", "adress", "beleive", "untill", "wich", "i",
           "spain", "tv", "hel", "comp", "goo:a", "to:want"],
    "es": ["cancion", "espana", "tambien", "aqui", "porque", "qe", "hola", "graicas", "manana", "dias:buenos",
           "esta", "como", "mas", "estoi", "nesesito", "hcaer"],
}


def main():
    lang = sys.argv[1]
    # BDIC=<path with {lang}>: compare another dictionary (e.g. tools/.cache/baseline/{lang}.bdic).
    d = Bdic(os.environ.get("BDIC", "").replace("{lang}", lang) or os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "dict", f"{lang}.bdic"))
    import time
    for arg in sys.argv[2:] or TESTS[lang]:
        w, _, prev = arg.partition(":")
        t = time.time()
        valid, auto, top = suggest(d, lang, w, prev or None)
        ms = (time.time() - t) * 1000
        print(f"{arg:14} valid={valid!s:5} auto={auto if auto is not None else '-':18} {ms:6.0f}ms  {top[:3]}")


if __name__ == "__main__":
    main()
