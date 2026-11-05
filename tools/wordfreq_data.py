"""Reads wordfreq's "large" word-frequency lists (CC BY-SA 4.0 data by Robyn Speer et al.,
https://github.com/rspeer/wordfreq): counts combined from Wikipedia, Reddit, Twitter,
subtitles, news, books and web text, so modern words get their real commonness.

The files are tools/.cache/raw/wordfreq/wordfreq/data/large_<lang>.msgpack.gz, taken out of the
wordfreq wheel on PyPI (data only; the package itself isn't installed or run). Format
("cBpack"): a msgpack list [header, bin0, bin1, ...]; the words in bin i have frequency
10^(-i/100) (i is in centibels).

Usage: python3 -I tools/wordfreq_data.py en [word ...]   prints each word's Zipf frequency
"""
import gzip
import os
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "tools", ".cache", "raw", "wordfreq", "wordfreq", "data")


def _unpack(b, i):
    """Minimal msgpack decoder: the types cBpack uses (lists, maps, strings, numbers)."""
    t = b[i]
    if t <= 0x7F:
        return t, i + 1
    if 0x80 <= t <= 0x8F:
        return _map(b, i + 1, t & 0x0F)
    if 0x90 <= t <= 0x9F:
        return _list(b, i + 1, t & 0x0F)
    if 0xA0 <= t <= 0xBF:
        n = t & 0x1F
        return b[i + 1:i + 1 + n].decode("utf-8"), i + 1 + n
    if t == 0xC0:
        return None, i + 1
    if t in (0xC2, 0xC3):
        return t == 0xC3, i + 1
    if t in (0xD9, 0xDA, 0xDB):
        size = {0xD9: 1, 0xDA: 2, 0xDB: 4}[t]
        n = int.from_bytes(b[i + 1:i + 1 + size], "big")
        s = i + 1 + size
        return b[s:s + n].decode("utf-8"), s + n
    if t in (0xCC, 0xCD, 0xCE, 0xCF):
        size = {0xCC: 1, 0xCD: 2, 0xCE: 4, 0xCF: 8}[t]
        return int.from_bytes(b[i + 1:i + 1 + size], "big"), i + 1 + size
    if t == 0xCA:
        return struct.unpack(">f", b[i + 1:i + 5])[0], i + 5
    if t == 0xCB:
        return struct.unpack(">d", b[i + 1:i + 9])[0], i + 9
    if t in (0xDC, 0xDD):
        size = 2 if t == 0xDC else 4
        return _list(b, i + 1 + size, int.from_bytes(b[i + 1:i + 1 + size], "big"))
    if t in (0xDE, 0xDF):
        size = 2 if t == 0xDE else 4
        return _map(b, i + 1 + size, int.from_bytes(b[i + 1:i + 1 + size], "big"))
    raise ValueError(f"msgpack type 0x{t:02x} at {i}")


def _list(b, i, n):
    out = []
    for _ in range(n):
        v, i = _unpack(b, i)
        out.append(v)
    return out, i


def _map(b, i, n):
    out = {}
    for _ in range(n):
        k, i = _unpack(b, i)
        v, i = _unpack(b, i)
        out[k] = v
    return out, i


def load(lang):
    """{word: frequency (share of all tokens, 0..1)}; empty if the file is missing."""
    path = os.path.join(DATA, f"large_{lang}.msgpack.gz")
    if not os.path.exists(path):
        return {}
    with gzip.open(path, "rb") as f:
        data, _ = _unpack(f.read(), 0)
    out = {}
    for cb, words in enumerate(data[1:]):
        fr = 10 ** (-cb / 100)
        for w in words:
            out.setdefault(w, fr)
    return out


def zipf(fr):
    """Zipf scale: log10 of occurrences per billion words (7 = 'the', 1 = once per 100M words)."""
    import math
    return math.log10(fr * 1e9) if fr > 0 else 0.0


def main():
    lang = sys.argv[1]
    f = load(lang)
    print(f"{lang}: {len(f)} words")
    for w in sys.argv[2:]:
        print(f"  {w:16} zipf {zipf(f.get(w, 0)):.2f}")


if __name__ == "__main__":
    main()
