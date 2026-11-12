# Generates the built-in theme presets in app/src/main/assets/themes (except 00-classic.json)
# and checks text contrast (WCAG 4.5:1). Usage: python3 tools/build_themes.py app/src/main/assets/themes
import json, os, sys
OUT = sys.argv[1]

def C(bg, key, fn, pressed, accent, on_accent, text, hint, *, text_fn=None, strip=None,
      prev_bg=None, prev_text=None, grad=None, border=None, border_grad=None):
    c = dict(background=bg, keyLetter=key, keyFunction=fn, keyPressed=pressed, accent=accent,
             onAccent=on_accent, text=text, textFunction=text_fn or text, hint=hint,
             previewBackground=prev_bg or pressed, previewText=prev_text or text,
             stripText=strip or text)
    if grad: c["backgroundGradient"] = grad
    if border: c["keyBorder"] = border
    if border_grad: c["keyBorderGradient"] = border_grad
    return c

def K(radius=8, gapH=5, gapV=8, borderWidth=0, glow=0, **style):
    """style: look, shape, font, labelScale, labelCase, hints, opacity (see ThemeSpec.KeyStyle)."""
    k = dict(radius=radius, gapH=gapH, gapV=gapV)
    if borderWidth: k["borderWidth"] = borderWidth
    if glow: k["glow"] = glow
    k.update(style)
    return k

def V(colors, keys=None, backdrop=None):
    v = dict(colors=colors, keys=keys or K())
    if backdrop: v["backdrop"] = backdrop
    return v

def SCENE(scene): return dict(type="scene", scene=scene)

T = []
def theme(n, id, name, cat, light, dark=None):
    t = dict(version=1, id=id, name=name, author="Bori", category=cat, light=light)
    if dark: t["dark"] = dark
    T.append((n, t))

# ---- Simple
theme(1, "material_you", "Material You", "Simple",
  V(C("@neutral2_100|#ECE6F0", "@neutral1_10|#FFFBFE", "@accent2_100|#E8DEF8", "@accent2_200|#CCC2DC",
      "@accent1_600|#6750A4", "#FFFFFF", "@neutral1_900|#1D1B20", "@neutral2_500|#79747E",
      prev_bg="@neutral1_10|#FFFBFE"), K(radius=10, shape="squircle")),
  V(C("@neutral1_900|#141218", "@neutral2_800|#36343B", "@accent2_800|#4A4458", "@accent2_600|#625B71",
      "@accent1_200|#D0BCFF", "@accent1_800|#381E72", "@neutral1_50|#E6E0E9", "@neutral2_300|#938F99",
      prev_bg="@neutral2_700|#49454F"), K(radius=10, shape="squircle")))
theme(2, "solid_light", "Snow", "Simple",
  V(C("#FFFFFF", "#F3F4F6", "#E2E5EA", "#C8CDD5", "#2563EB", "#FFFFFF", "#111827", "#6B7280",
      prev_bg="#FFFFFF"), K(radius=6, font="inter", look="floating")))
theme(3, "solid_dark", "Charcoal", "Simple",
  V(C("#1F2124", "#2E3135", "#26292C", "#4A4F55", "#5EA0FF", "#0B1220", "#F1F3F5", "#9AA0A6",
      prev_bg="#4A4F55"), K(radius=6, font="inter")))
theme(4, "amoled", "Pure Black", "Simple",
  V(C("#000000", "#151515", "#0B0B0B", "#333333", "#FFFFFF", "#000000", "#FFFFFF", "#8A8A8A",
      prev_bg="#2A2A2A"), K(radius=8)))
theme(5, "pastel", "Pastel", "Simple",
  V(C("#FDF3EE", "#FFFFFF", "#F7DCE6", "#EFC7D6", "#7656C9", "#FFFFFF", "#4E4766", "#9A8FB0",
      prev_bg="#FFFFFF"), K(radius=14, font="nunito", shape="pill")),
  V(C("#2A2433", "#3A3247", "#4A3550", "#5C4A70", "#C9B6FF", "#2A2433", "#F4EEFF", "#B3A6C8",
      prev_bg="#5C4A70"), K(radius=14, font="nunito", shape="pill")))
theme(6, "high_contrast_light", "High Contrast Light", "Simple",
  V(C("#FFFFFF", "#FFFFFF", "#FFFFFF", "#FFE600", "#000000", "#FFE600", "#000000", "#333333",
      prev_bg="#FFE600", border="#000000"), K(radius=6, borderWidth=2, font="atkinson", labelScale=1.1)))
theme(7, "high_contrast_dark", "High Contrast Dark", "Simple",
  V(C("#000000", "#000000", "#000000", "#4D4300", "#FFE600", "#000000", "#FFFFFF", "#E0E0E0",
      prev_bg="#000000", prev_text="#FFE600", border="#FFFFFF"), K(radius=6, borderWidth=2, font="atkinson", labelScale=1.1)))

# ---- Glow & LED
theme(10, "dot_matrix", "Dot Matrix", "Glow & LED",
  V(C("#000000", "#111111", "#0A0A0A", "#2B2B2B", "#D71921", "#FFFFFF", "#F2F2F2", "#7A7A7A",
      prev_bg="#1E1E1E", border="#2A2A2A"), K(radius=22, gapH=6, gapV=9, borderWidth=1, font="doto", shape="circle", labelScale=1.1)),
  None)
theme(11, "neon_led", "Neon LED", "Glow & LED",
  V(C("#05010F", "#0B0720", "#0B0720", "#1D1450", "#FF2BD6", "#05010F", "#CFFBFF", "#6FD7E6",
      prev_bg="#1D1450", border="#00E5FF"), K(radius=10, gapH=7, gapV=10, borderWidth=1.5, glow=6, font="monoton", labelCase="upper", labelScale=0.95)))
theme(12, "gamer_rgb", "Gamer RGB", "Glow & LED",
  V(C("#050505", "#121212", "#0B0B0B", "#2A2A2A", "#00FF88", "#001A0E", "#F5F5F5", "#9E9E9E",
      prev_bg="#2A2A2A",
      border_grad=["#FF0040", "#FF8A00", "#FFE600", "#00FF66", "#00C8FF", "#5B3BFF", "#FF00E1"]),
    K(radius=6, gapH=6, gapV=9, borderWidth=1.5, glow=4, font="orbitron")))
theme(13, "mechanical", "Mechanical", "Glow & LED",
  V(C("#2B2B2B", "#EAE4D6", "#A39E94", "#CFC7B6", "#C2410C", "#FFFFFF", "#2F2B26", "#6E675C",
      text_fn="#1E1B17", prev_bg="#EAE4D6", border="#1A1A1A", strip="#EAE4D6"),
    K(radius=4, gapH=4, gapV=6, borderWidth=1, font="jetbrains_mono", look="raised")))

# ---- Glass
theme(20, "glass", "Glass", "Glass",
  V(C("#24467F", "#33FFFFFF", "#22FFFFFF", "#66FFFFFF", "#7FDBFF", "#0B2547", "#FFFFFF", "#CFE3FF",
      prev_bg="#3A5E9A", grad=["#1E3C72", "#2A5298"], border="#55FFFFFF"),
    K(radius=12, borderWidth=1, font="inter")))
theme(21, "candy_glass", "Candy Glass", "Glass",
  V(C("#E6C3F2", "#80FFFFFF", "#4DFFFFFF", "#B3FFFFFF", "#D61F69", "#FFFFFF", "#4A1E5E", "#8A5A9E",
      prev_bg="#FFFFFF", grad=["#FFD1E8", "#C9B6FF"], border="#B3FFFFFF"),
    K(radius=16, borderWidth=1, font="nunito", shape="pill")))

# ---- Scenes
theme(30, "sunset", "Sunset", "Scenes",
  V(C("#6A2C5A", "#1FFFFFFF", "#1F000000", "#59FFFFFF", "#FFD166", "#3A1C3F", "#FFFFFF", "#FFE3D3",
      prev_bg="#7A3A5E", grad=["#2E1A47", "#6A2C5A", "#A3405A"]), K(radius=10, font="nunito")))
theme(31, "ocean", "Ocean", "Scenes",
  V(C("#203A43", "#26FFFFFF", "#1AFFFFFF", "#4DFFFFFF", "#4DD0E1", "#062A30", "#FFFFFF", "#B6E3EA",
      prev_bg="#2C5364", grad=["#0F2027", "#203A43", "#2C5364"]), K(radius=10, font="inter")))
theme(32, "aurora", "Aurora", "Scenes",
  V(C("#123247", "#17FFFFFF", "#14FFFFFF", "#4DFFFFFF", "#7CF3C0", "#06261A", "#E8FFF7", "#9FD9C4",
      prev_bg="#1B4A5A", grad=["#0B1026", "#123247", "#155450", "#1A6A5A"], border="#33A8FFE0"),
    K(radius=10, borderWidth=1, font="inter")))
theme(33, "galaxy", "Galaxy", "Scenes",
  V(C("#2A0845", "#26FFFFFF", "#1AFFFFFF", "#59FFFFFF", "#D0106A", "#FFFFFF", "#FFFFFF", "#D2C2F0",
      prev_bg="#4A2A7A", grad=["#0B0221", "#2A0845", "#6441A5"], border="#33FFFFFF"),
    K(radius=12, borderWidth=1, font="orbitron")))
theme(34, "retro_typewriter", "Retro Typewriter", "Scenes",
  V(C("#3B2F2A", "#F2EBDD", "#C9BFAE", "#D8CDB8", "#8B1E1E", "#F2EBDD", "#2A211C", "#7A6A5C",
      prev_bg="#F2EBDD", border="#1A1410", strip="#F2EBDD"),
    K(radius=22, gapH=7, gapV=9, borderWidth=2, font="special_elite", shape="circle", look="raised")))

# Drawn scenes (theme/Backdrop.kt Scenes). The contrast check can't see the drawing, so the
# gradients below are the scene's main colours (sky, then what sits behind the keys); at run time
# ThemeResolver also switches unreadable text over pictures to black or white.
theme(35, "mountain_dawn", "Mountains", "Scenes",
  V(C("#9BC7E8", "#D9FFFFFF", "#B3E6EEF5", "#FFFFFFFF", "#3E5677", "#FFFFFF", "#1C2A3A", "#4F6378",
      prev_bg="#FFFFFF", strip="#1C2A3A", grad=["#9BC7E8", "#F0E2D2"]),
    K(radius=10, font="nunito"), SCENE("mountains")),
  V(C("#0B1430", "#B31E2745", "#99141B33", "#CC3A4A78", "#9FB7FF", "#0B1430", "#EEF2FF", "#AAB6D6",
      prev_bg="#3A4A78", grad=["#0B1430", "#2A3558", "#141B33"]),
    K(radius=10, font="nunito"), SCENE("mountains")))
theme(36, "beach_day", "Beach", "Scenes",
  V(C("#78C4EE", "#D9FFFFFF", "#B3FFF4DC", "#FFFFFFFF", "#B5501E", "#FFFFFF", "#123247", "#4B6A80",
      prev_bg="#FFFFFF", strip="#0B1E2C", grad=["#78C4EE", "#2596C8", "#F2D7A0"]),
    K(radius=14, font="nunito", shape="pill"), SCENE("beach")),
  V(C("#0C122E", "#B3163A58", "#99102A44", "#CC2E5A80", "#FFC37A", "#0C122E", "#F0F4FF", "#A9BCD6",
      prev_bg="#2E5A80", grad=["#0C122E", "#16284E", "#3A3A44"]),
    K(radius=14, font="nunito", shape="pill"), SCENE("beach")))
theme(37, "city_night", "City Lights", "Scenes",
  V(C("#FFB37C", "#C7FFFFFF", "#A6FFE9DA", "#F2FFFFFF", "#6A4C93", "#FFFFFF", "#2A1838", "#6B5580",
      prev_bg="#FFFFFF", strip="#2A1838", grad=["#FFB37C", "#D99A92"]),
    K(radius=8, font="inter"), SCENE("skyline")),
  V(C("#0A0F2C", "#B31C1E40", "#99101028", "#CC3A3C70", "#FFD678", "#0A0F2C", "#F4F0FF", "#B5AED6",
      prev_bg="#3A3C70", grad=["#0A0F2C", "#1C1E40", "#101028"]),
    K(radius=8, font="inter"), SCENE("skyline")))

# ---- Puerto Rico
theme(40, "borinquen", "Borinquen", "Puerto Rico",
  V(C("#E6F2FB", "#FFFFFF", "#BFDDF3", "#9CC9EC", "#E4002B", "#FFFFFF", "#0D2B55", "#4F7FA8",
      prev_bg="#FFFFFF"), K(radius=10, font="nunito", look="raised")),
  V(C("#0D2B55", "#173E72", "#11325F", "#2A5590", "#E4002B", "#FFFFFF", "#FFFFFF", "#9FC4E8",
      prev_bg="#2A5590"), K(radius=10, font="nunito", look="raised")))
theme(41, "coqui_night", "Coquí Night", "Puerto Rico",
  V(C("#0E2418", "#163322", "#11291B", "#25503A", "#9BE15D", "#0B1F12", "#DDF3E3", "#7FA98E",
      prev_bg="#25503A", grad=["#06140D", "#0E2418", "#173A26"]), K(radius=12, font="nunito")))

os.makedirs(OUT, exist_ok=True)
for n, t in T:
    with open(os.path.join(OUT, f"{n:02d}-{t['id']}.json"), "w") as f:
        json.dump(t, f, indent=2, ensure_ascii=False); f.write("\n")

# ---- contrast check (hex fallbacks only)
def rgba(s):
    s = s.split("|")[-1].lstrip("#")
    if len(s) == 6: s = "FF" + s
    v = int(s, 16); return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) / 255)
def over(fg, bg):
    a = fg[3]; return tuple(fg[i] * a + bg[i] * (1 - a) for i in range(3)) + (1,)
def lum(c):
    def ch(x):
        x /= 255; return x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4
    return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2])
def ratio(a, b):
    la, lb = lum(a), lum(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
bad = 0
for n, t in T:
    for vn in ("light", "dark"):
        v = t.get(vn)
        if not v: continue
        c = v["colors"]
        bgs = [rgba(x) for x in c.get("backgroundGradient", [c["background"]])]
        worst = 99
        for bg in bgs:
            for fg, k in (("text", "keyLetter"), ("textFunction", "keyFunction"), ("onAccent", "accent"),
                          ("stripText", None), ("previewText", "previewBackground")):
                back = over(rgba(c[k]), bg) if k else bg
                r = ratio(over(rgba(c[fg]), back), back)
                if r < 4.5: print(f"LOW {t['id']}/{vn} {fg} on {k or 'background'}: {r:.2f}"); bad += 1
                worst = min(worst, r)
        print(f"{t['id']:22s} {vn:5s} worst {worst:.1f}")
print("problems:", bad)
