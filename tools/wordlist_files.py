"""The hand-curated word lists of a language: tools/wordlists/<lang>_*.txt, except the phrase
lists (<lang>_phrases.txt, read by ngram_count.py). Sorted, so later files win display forms."""
import glob
import os

WORDLISTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wordlists")


def word_files(lang):
    return [p for p in sorted(glob.glob(os.path.join(WORDLISTS, f"{lang}_*.txt")))
            if not p.endswith("_phrases.txt")]


def phrase_file(lang):
    return os.path.join(WORDLISTS, f"{lang}_phrases.txt")
