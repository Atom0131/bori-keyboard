"""Extracts a compact word table from Wiktionary (CC BY-SA), via kaikki.org's wiktextract data.

Streams the multi-gigabyte JSONL from stdin, so it never has to be stored:

  curl -sL https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz \
    | gunzip | python3 -I tools/wiktionary.py en > tools/.cache/raw/wiktionary/en_words.tsv
  curl -sL https://kaikki.org/dictionary/Spanish/kaikki.org-dictionary-Spanish.jsonl \
    | python3 -I tools/wiktionary.py es > tools/.cache/raw/wiktionary/es_words.tsv

Output, one line per word and part of speech:  word<TAB>pos<TAB>tags<TAB>forms
  tags  comma-separated labels shared by every sense (misspelling, obsolete, archaic, dated,
        slang, rare, ...) plus "some:<tag>" for labels on only some senses, and topics
        (medicine, computing, ...) as "topic:<name>"
  forms comma-separated inflections (plurals, verb forms), minus obsolete/misspelt ones
build_dict.py reads this table (see WIKT_* there).
"""
import json
import sys

# Labels whose presence on every sense says how the word is used.
USAGE = {
    "misspelling", "obsolete", "archaic", "dated", "slang", "rare", "nonstandard", "proscribed",
    "colloquial", "informal", "vulgar", "derogatory", "offensive", "abbreviation", "initialism",
    "acronym", "historical", "humorous", "Internet", "regional", "dialectal", "eye-dialect",
    "pronunciation-spelling", "alt-of", "form-of", "uncommon",
}
SKIP_FORM_TAGS = {"table-tags", "inflection-template", "class", "romanization", "obsolete", "archaic",
                  "misspelling", "dated", "rare", "nonstandard", "error-unknown-tag"}


def clean(s):
    return s.replace("\t", " ").replace("\n", " ").replace(",", " ").strip()


def main():
    lang = sys.argv[1]
    out = sys.stdout
    for line in sys.stdin:
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if e.get("lang_code") != lang:
            continue
        word = e.get("word", "")
        if not word or " " in word or len(word) > 40:
            continue
        senses = e.get("senses") or []
        per_sense = []
        topics = set()
        for s in senses:
            tags = set(s.get("tags") or [])
            if s.get("alt_of"):
                tags.add("alt-of")
            if s.get("form_of"):
                tags.add("form-of")
            per_sense.append(tags & USAGE)
            topics.update(s.get("topics") or [])
        if per_sense:
            shared = set.intersection(*per_sense)
            some = set.union(*per_sense) - shared
        else:
            shared, some = set(), set()
        tags = sorted(shared) + sorted("some:" + t for t in some) + sorted("topic:" + clean(t) for t in topics)
        forms = []
        for f in e.get("forms") or []:
            form = f.get("form", "")
            ftags = set(f.get("tags") or [])
            if not form or " " in form or form == word or ftags & SKIP_FORM_TAGS:
                continue
            if form not in forms:
                forms.append(clean(form))
        out.write(f"{word}\t{e.get('pos', '')}\t{','.join(tags)}\t{','.join(forms)}\n")


if __name__ == "__main__":
    main()
