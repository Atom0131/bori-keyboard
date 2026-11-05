#!/usr/bin/env python3
"""Builds the emoji style packs in app/src/main/assets/emoji/<style>.bep.

Only the emoji listed in view/EmojiData.kt are fetched. Each one is scaled to fit SIZE x SIZE
and stored as WebP. Sources (all free licences, see ATTRIBUTION.md):
  twemoji   jdecked/twemoji        CC BY 4.0
  fluent    microsoft/fluentui-emoji (3D PNGs)   MIT
  openmoji  hfg-gmuend/openmoji    CC BY-SA 4.0
  noto      googlefonts/noto-emoji (2D PNGs, region-flags for flags)   Apache 2.0
  blobmoji  C1710/blobmoji (old Google "blob" style, SVGs)   Apache 2.0
  emojione2 joypixels/emojione v2.2.7 (classic 2016 cartoon PNGs)   CC BY 4.0
  fxemoji   mozilla/fxemoji (Firefox OS cartoon set, SVGs)   CC BY 4.0
SVGs are rasterised with rsvg-convert (librsvg). Emoji a set lacks are left out of its pack;
the app draws those with the phone's font.

Overrides: tools/emoji_overrides/<emoji key>.png replaces that emoji in every pack and is also
written to custom.bep (used even with the phone's own emoji font). Made by
tools/make_emoji_overrides.py: Puerto Rico flag with the light-blue (1895) triangle.

Downloads are cached in tools/.cache/emoji/<style>/ (raw PNGs, treated as data only).

.bep format (little endian):
  "BEP1", u32 count, then count x (u8 keyLen, key UTF-8 [emoji without U+FE0F],
  u32 offset, u32 length), then the WebP blobs. Offsets are from the start of the file.

Usage: python3 -I tools/build_emoji_packs.py [style ...]   (env LIMIT=n for a quick test)
Needs Pillow with WebP.
"""
import io
import json
import os
import re
import struct
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_KT = os.path.join(ROOT, "app/src/main/java/com/bori/keyboard/view/EmojiData.kt")
CACHE = os.path.join(ROOT, "tools/.cache/emoji")
OUT = os.path.join(ROOT, "app/src/main/assets/emoji")
SIZE = 72
QUALITY = 82
STYLES = ["twemoji", "fluent", "openmoji", "noto", "blobmoji", "emojione2", "fxemoji"]
OVERRIDES = os.path.join(ROOT, "tools/emoji_overrides")

RAW = "https://raw.githubusercontent.com/"


def emoji_list():
    src = open(DATA_KT, encoding="utf-8").read()
    seen, out = set(), []
    for s in re.findall(r'"([^"]*)"', src):
        for t in s.split(" "):
            if t and not t.isascii() and t not in seen:
                seen.add(t)
                out.append(t)
    limit = int(os.environ.get("LIMIT", "0"))
    return out[:limit] if limit else out


def key(e):
    return e.replace("\ufe0f", "")


def cps(e):
    return [ord(c) for c in e]


class FetchError(Exception):
    pass


def fetch(url):
    """Bytes, or None for a 404. Network errors are retried, then raise FetchError."""
    import time
    import urllib.error
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bori-emoji-build"}), timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
        except Exception:
            pass
        time.sleep(2 * (attempt + 1))
    raise FetchError(url)


# ---- per-style candidate URLs -------------------------------------------------------------

def twemoji_urls(e):
    full = [c for c in cps(e)]
    stripped = [c for c in full if c != 0xFE0F]
    first = full if 0x200D in full else stripped
    names = []
    for seq in (first, stripped, full):
        n = "-".join("%x" % c for c in seq)
        if n not in names:
            names.append(n)
    return [RAW + "jdecked/twemoji/main/assets/72x72/%s.png" % n for n in names]


def openmoji_urls(e):
    full = cps(e)
    stripped = [c for c in full if c != 0xFE0F]
    names = []
    for seq in (stripped, full):
        n = "-".join("%04X" % c for c in seq)
        if n not in names:
            names.append(n)
    return [RAW + "hfg-gmuend/openmoji/master/color/72x72/%s.png" % n for n in names]


def noto_urls(e):
    c = [x for x in cps(e) if x != 0xFE0F]
    base = RAW + "googlefonts/noto-emoji/main/"
    if len(c) == 2 and all(0x1F1E6 <= x <= 0x1F1FF for x in c):
        cc = "".join(chr(x - 0x1F1E6 + ord("A")) for x in c)
        return [base + "third_party/region-flags/png/%s.png" % cc]
    if c and c[0] == 0x1F3F4 and len(c) > 2 and c[-1] == 0xE007F:
        tag = "".join(chr(x - 0xE0000) for x in c[1:-1]).upper()  # e.g. GBENG
        return [base + "third_party/region-flags/png/%s-%s.png" % (tag[:2], tag[2:])]
    return [base + "2D/png/72/emoji_u%s.png" % "_".join("%04x" % x for x in c)]


_fluent_map = None


def fluent_map():
    """Emoji key -> raw URL of its 3D PNG, from the repo tree and each metadata.json."""
    global _fluent_map
    if _fluent_map is not None:
        return _fluent_map
    cache = os.path.join(CACHE, "fluent_map.json")
    if os.path.exists(cache):
        _fluent_map = json.load(open(cache))
        return _fluent_map
    tree_file = os.path.join(CACHE, "fluent_tree.json")
    if not os.path.exists(tree_file):
        data = fetch("https://api.github.com/repos/microsoft/fluentui-emoji/git/trees/main?recursive=1")
        open(tree_file, "wb").write(data)
    paths = [x["path"] for x in json.load(open(tree_file))["tree"]]
    pngs = {}
    for p in paths:
        if p.endswith(".png") and "/3D/" in p:
            folder = p.split("/")[1]
            # Prefer the Default skin tone variant.
            if folder not in pngs or "/Default/" in p:
                pngs[folder] = p
    folders = [p.split("/")[1] for p in paths if p.endswith("/metadata.json") and p.count("/") == 2]

    def meta(folder):
        d = fetch(RAW + "microsoft/fluentui-emoji/main/" + urllib.parse.quote("assets/%s/metadata.json" % folder))
        return folder, json.loads(d) if d else None

    out = {}
    with ThreadPoolExecutor(8) as ex:
        for folder, m in ex.map(meta, folders):
            if not m or folder not in pngs:
                continue
            glyph = "".join(chr(int(h, 16)) for h in m["unicode"].split())
            out[key(glyph)] = RAW + "microsoft/fluentui-emoji/main/" + urllib.parse.quote(pngs[folder])
    json.dump(out, open(cache, "w"))
    _fluent_map = out
    return out


def fluent_urls(e):
    u = fluent_map().get(key(e))
    return [u] if u else []


def blobmoji_urls(e):
    # Same names as Noto (it is a Noto fork), but SVG.
    return [u.replace("googlefonts/noto-emoji/main/2D/png/72/", "C1710/blobmoji/main/svg/")
             .replace("googlefonts/noto-emoji/main/third_party/region-flags/png/", "C1710/blobmoji/main/third_party/region-flags/svg/")
             .replace(".png", ".svg") for u in noto_urls(e)]


def emojione2_urls(e):
    full = cps(e)
    stripped = [c for c in full if c != 0xFE0F]
    names = []
    for seq in (stripped, full):
        n = "-".join("%x" % c for c in seq)
        if n not in names:
            names.append(n)
    for seq in (stripped, full):
        n = "-".join("%04x" % c for c in seq)
        if n not in names:
            names.append(n)
    return [RAW + "joypixels/emojione/v2.2.7/assets/png/%s.png" % n for n in names]


_fx_map = None


def fxemoji_urls(e):
    """FxEmoji files are u<HEX>-<name>.svg, single code points only."""
    global _fx_map
    if _fx_map is None:
        tree_file = os.path.join(CACHE, "fx_tree.json")
        if not os.path.exists(tree_file):
            open(tree_file, "wb").write(fetch(
                "https://api.github.com/repos/mozilla/fxemoji/git/trees/4456d13f49ee680445f98bae5ed0277f35c1b105"))
        _fx_map = {}
        for x in json.load(open(tree_file))["tree"]:
            m = re.match(r"u([0-9A-F]{4,5})-[^.]*\.svg$", x["path"])
            if m:
                _fx_map[chr(int(m.group(1), 16))] = RAW + "mozilla/fxemoji/gh-pages/svgs/FirefoxEmoji/" + x["path"]
    u = _fx_map.get(key(e))
    return [u] if u else []


URLS = {"twemoji": twemoji_urls, "fluent": fluent_urls, "openmoji": openmoji_urls, "noto": noto_urls,
        "blobmoji": blobmoji_urls, "emojione2": emojione2_urls, "fxemoji": fxemoji_urls}

# ---- build ---------------------------------------------------------------------------------


def raw_png(style, e):
    d = os.path.join(CACHE, style)
    os.makedirs(d, exist_ok=True)
    name = "-".join("%x" % c for c in cps(key(e)))
    path = os.path.join(d, name + ".png")
    miss = path + ".missing"
    if os.path.exists(path):
        return open(path, "rb").read()
    if os.path.exists(miss):
        return None
    for url in URLS[style](e):
        try:
            data = fetch(url)
        except FetchError:
            print("  network error, try again later:", url, flush=True)
            return None  # no .missing marker, so a re-run retries it
        if data and url.endswith(".svg"):
            data = svg_to_png(data)
        if data and data[:8] == b"\x89PNG\r\n\x1a\n":
            open(path, "wb").write(data)
            return data
    open(miss, "w").close()
    return None


def svg_to_png(svg):
    import subprocess
    r = subprocess.run(["rsvg-convert", "-w", "144", "-h", "144", "-a", "-f", "png"],
                       input=svg, capture_output=True)
    return r.stdout if r.returncode == 0 else None


def to_webp(png):
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    bbox = im.getbbox()  # trim transparent borders so every style fills the cell alike
    if bbox:
        im = im.crop(bbox)
    w, h = im.size
    s = SIZE / max(w, h)
    im = im.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(im, ((SIZE - im.width) // 2, (SIZE - im.height) // 2))
    buf = io.BytesIO()
    canvas.save(buf, "WEBP", quality=QUALITY, alpha_quality=90, method=6)
    return buf.getvalue()


def build(style, emoji):
    # Build name maps once here: done inside the worker threads they raced (half-filled maps).
    if style == "fluent":
        fluent_map()
    elif style == "fxemoji" and emoji:
        fxemoji_urls(emoji[0])
    with ThreadPoolExecutor(8) as ex:
        pngs = list(ex.map(lambda e: raw_png(style, e), emoji))
    overrides = load_overrides()
    pngs = [overrides.get(key(e), png) for e, png in zip(emoji, pngs)]
    entries, missing = [], []
    for e, png in zip(emoji, pngs):
        if png is None:
            missing.append(e)
            continue
        entries.append((key(e).encode("utf-8"), to_webp(png)))
    size = write_pack(style, entries)
    print("%-9s %4d emoji, %4d missing, %.2f MB" % (style, len(entries), len(missing), size / 1e6), flush=True)
    if missing:
        print("  missing:", " ".join(missing[:80]), flush=True)


def load_overrides():
    """tools/emoji_overrides/<hex>-<hex>.png, keyed like the packs (no U+FE0F)."""
    out = {}
    if os.path.isdir(OVERRIDES):
        for f in sorted(os.listdir(OVERRIDES)):
            if f.endswith(".png"):
                k = "".join(chr(int(h, 16)) for h in f[:-4].split("-"))
                out[key(k)] = open(os.path.join(OVERRIDES, f), "rb").read()
    return out


def write_pack(style, entries):
    header = b"BEP1" + struct.pack("<I", len(entries))
    index_len = sum(1 + len(k) + 8 for k, _ in entries)
    offset = len(header) + index_len
    index, blobs = bytearray(), bytearray()
    for k, blob in entries:
        index += struct.pack("<B", len(k)) + k + struct.pack("<II", offset + len(blobs), len(blob))
        blobs += blob
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, style + ".bep"), "wb") as f:
        f.write(header + index + blobs)
    return len(header) + len(index) + len(blobs)


def main():
    emoji = emoji_list()
    print(len(emoji), "emoji in EmojiData.kt", flush=True)
    custom = [(k.encode("utf-8"), to_webp(png)) for k, png in load_overrides().items()]
    write_pack("custom", custom)
    print("custom    %4d override(s)" % len(custom), flush=True)
    for style in sys.argv[1:] or STYLES:
        build(style, emoji)


if __name__ == "__main__":
    main()
