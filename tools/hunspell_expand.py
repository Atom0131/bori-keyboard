"""Minimal Hunspell .dic/.aff expander (our own implementation, stdlib only).

Supports what the en_US and es dictionaries we import use: PFX/SFX rules with strip/add/
condition, cross products, one level of continuation suffixes ("ción/S"), FLAG UTF-8 and
the default single-character flags, and NOSUGGEST / NEEDAFFIX / ONLYINCOMPOUND flags.
Compounding is ignored (only English number words like "1st" use it).
"""
import re
import sys
from dataclasses import dataclass, field


@dataclass
class Rule:
    strip: str
    add: str
    cond: re.Pattern
    cont: str  # continuation flags carried by the affixed form


@dataclass
class Affix:
    kind: str  # "PFX" or "SFX"
    cross: bool
    rules: list = field(default_factory=list)


def _split_flags(s: str, mode: str):
    if not s:
        return []
    if mode == "long":
        return [s[i:i + 2] for i in range(0, len(s), 2)]
    if mode == "num":
        return s.split(",")
    return list(s)  # single chars (ASCII default or UTF-8)


def load_aff(path):
    affixes = {}
    meta = {"flag": "char", "nosuggest": None, "needaffix": None, "onlyincompound": None}
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    for line in lines:
        parts = line.split()
        if not parts or parts[0].startswith("#"):
            continue
        key = parts[0]
        if key == "FLAG" and len(parts) > 1:
            meta["flag"] = {"long": "long", "num": "num"}.get(parts[1], "char")
        elif key == "NOSUGGEST":
            meta["nosuggest"] = parts[1]
        elif key == "NEEDAFFIX":
            meta["needaffix"] = parts[1]
        elif key == "ONLYINCOMPOUND":
            meta["onlyincompound"] = parts[1]
        elif key in ("PFX", "SFX") and len(parts) >= 4:
            flag = parts[1]
            if parts[2] in ("Y", "N") and len(parts) == 4 and parts[3].isdigit():
                affixes[flag] = Affix(key, parts[2] == "Y")
                continue
            if len(parts) < 5 or flag not in affixes:
                continue
            strip = "" if parts[2] == "0" else parts[2]
            add, _, cont = parts[3].partition("/")
            add = "" if add == "0" else add
            cond = parts[4] if len(parts) > 4 else "."
            pat = ("^" + cond) if key == "PFX" else (cond + "$")
            try:
                rx = re.compile(pat)
            except re.error:
                continue
            affixes[flag].rules.append(Rule(strip, add, rx, cont))
    return affixes, meta


def _apply(word, aff: Affix):
    for r in aff.rules:
        if aff.kind == "SFX":
            if not r.cond.search(word) or (r.strip and not word.endswith(r.strip)):
                continue
            base = word[: len(word) - len(r.strip)] if r.strip else word
            yield base + r.add, r
        else:
            if not r.cond.search(word) or (r.strip and not word.startswith(r.strip)):
                continue
            base = word[len(r.strip):] if r.strip else word
            yield r.add + base, r


def expand(dic_path, aff_path):
    """Yields (surface_form, nosuggest) for every word the dictionary accepts."""
    affixes, meta = load_aff(aff_path)
    mode = meta["flag"]
    with open(dic_path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()[1:]
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("\t"):
            continue
        # Morphological fields (po:, st:) follow after whitespace.
        entry = line.split("\t")[0].split(" ")[0]
        word, _, flagstr = entry.partition("/")
        if not word:
            continue
        flags = _split_flags(flagstr, mode)
        if meta["onlyincompound"] in flags:
            continue
        nosug = meta["nosuggest"] in flags
        if meta["needaffix"] not in flags:
            yield word, nosug
        sfx_forms = []
        for fl in flags:
            a = affixes.get(fl)
            if not a or a.kind != "SFX":
                continue
            for form, rule in _apply(word, a):
                sfx_forms.append((form, a.cross))
                yield form, nosug
                # One level of continuation suffixes.
                for cf in _split_flags(rule.cont, mode):
                    ca = affixes.get(cf)
                    if ca and ca.kind == "SFX":
                        for form2, _ in _apply(form, ca):
                            yield form2, nosug
        for fl in flags:
            a = affixes.get(fl)
            if not a or a.kind != "PFX":
                continue
            for form, _ in _apply(word, a):
                yield form, nosug
            if a.cross:
                for sform, scross in sfx_forms:
                    if scross:
                        for form, _ in _apply(sform, a):
                            yield form, nosug


if __name__ == "__main__":
    n = 0
    for w, ns in expand(sys.argv[1], sys.argv[2]):
        n += 1
        if len(sys.argv) > 3:
            print(w + ("\t!" if ns else ""))
    print(n, file=sys.stderr)
