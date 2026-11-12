"""Downloads the keyboard fonts from github.com/google/fonts, pins variable fonts to one
weight, subsets them to the characters a Latin keyboard shows, and writes
app/src/main/assets/fonts/<id>.ttf plus licenses/<id>.txt (OFL / Apache need the licence
shipped with the font). Needs fonttools: pip install fonttools (or put its wheel on PYTHONPATH).
Usage: python3 tools/build_fonts.py   (run from the project root)
"""
import os, sys, urllib.request, urllib.parse
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

RAW = "https://raw.githubusercontent.com/google/fonts/main/"
CACHE = os.path.join("tools", ".cache", "fonts")
OUT = os.path.join("app", "src", "main", "assets", "fonts")

# id, repo dir, file, licence file, variable axes to pin (None = static font)
FONTS = [
    ("inter", "ofl/inter", "Inter[opsz,wght].ttf", "OFL.txt", {"wght": 450, "opsz": 18}),
    ("nunito", "ofl/nunito", "Nunito[wght].ttf", "OFL.txt", {"wght": 600}),
    ("atkinson", "ofl/atkinsonhyperlegible", "AtkinsonHyperlegible-Regular.ttf", "OFL.txt", None),
    ("jetbrains_mono", "ofl/jetbrainsmono", "JetBrainsMono[wght].ttf", "OFL.txt", {"wght": 500}),
    ("doto", "ofl/doto", "Doto[ROND,wght].ttf", "OFL.txt", {"wght": 800, "ROND": 100}),
    ("press_start", "ofl/pressstart2p", "PressStart2P-Regular.ttf", "OFL.txt", None),
    ("bungee", "ofl/bungee", "Bungee-Regular.ttf", "OFL.txt", None),
    ("monoton", "ofl/monoton", "Monoton-Regular.ttf", "OFL.txt", None),
    ("orbitron", "ofl/orbitron", "Orbitron[wght].ttf", "OFL.txt", {"wght": 600}),
    ("pacifico", "ofl/pacifico", "Pacifico-Regular.ttf", "OFL.txt", None),
    ("permanent_marker", "apache/permanentmarker", "PermanentMarker-Regular.ttf", "LICENSE.txt", None),
    ("special_elite", "apache/specialelite", "SpecialElite-Regular.ttf", "LICENSE.txt", None),
    ("creepster", "ofl/creepster", "Creepster-Regular.ttf", "OFL.txt", None),
    ("comic_neue", "ofl/comicneue", "ComicNeue-Bold.ttf", "OFL.txt", None),
]

# Basic Latin, Latin-1, Latin Extended-A, punctuation, euro, trademark.
UNICODES = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x2010, 0x2028)) +
            list(range(0x2030, 0x205F)) + [0x20AC, 0x2122])


def fetch(path, dest):
    if not os.path.exists(dest):
        url = RAW + urllib.parse.quote(path)
        print("download", url)
        with urllib.request.urlopen(url, timeout=60) as r, open(dest + ".tmp", "wb") as f:
            f.write(r.read())
        os.replace(dest + ".tmp", dest)
    return dest


def main():
    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(os.path.join(OUT, "licenses"), exist_ok=True)
    total = 0
    for fid, d, name, lic, axes in FONTS:
        src = fetch(f"{d}/{name}", os.path.join(CACHE, name))
        fetch(f"{d}/{lic}", os.path.join(CACHE, fid + "-" + lic))
        font = TTFont(src)
        if axes:
            have = {a.axisTag for a in font["fvar"].axes}
            font = instancer.instantiateVariableFont(font, {k: v for k, v in axes.items() if k in have})
        opts = subset.Options()
        opts.layout_features = ["kern", "liga", "calt", "ccmp", "locl", "mark", "mkmk"]
        opts.name_IDs = ["*"]  # keep copyright / licence names
        opts.notdef_outline = True
        opts.hinting = False
        s = subset.Subsetter(opts)
        s.populate(unicodes=UNICODES)
        s.subset(font)
        dest = os.path.join(OUT, fid + ".ttf")
        font.save(dest)
        with open(os.path.join(CACHE, fid + "-" + lic), "rb") as a, \
                open(os.path.join(OUT, "licenses", fid + ".txt"), "wb") as b:
            b.write(a.read())
        size = os.path.getsize(dest)
        total += size
        print(f"{fid:18s} {size // 1024:4d} KB  glyphs {len(font.getGlyphOrder())}")
    print(f"total {total // 1024} KB")


if __name__ == "__main__":
    sys.exit(main())
