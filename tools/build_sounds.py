# Synthesizes Bori's key-sound packs (our own sounds, no recordings) into
# app/src/main/assets/sounds/<pack>/{key,space,enter,delete}.wav (22.05 kHz, 16-bit mono).
# Piano mode: piano_c4/c5/c6.wav, pitched per key at run time (ime/SoundPacks.kt).
# Pure Python (no numpy). Usage: python3 tools/build_sounds.py app/src/main/assets/sounds
import math, os, random, struct, sys, wave

SR = 22050
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/sounds"
random.seed(7)

def n(sec): return int(sec * SR)
def silence(sec): return [0.0] * n(sec)

def env(length, attack=0.001, decay=0.05):
    a = max(1, n(attack))
    return [(i / a) if i < a else math.exp(-(i - a) / (decay * SR)) for i in range(length)]

def noise(length): return [random.uniform(-1, 1) for _ in range(length)]

def mix(*parts):
    out = [0.0] * max(len(p) for p in parts)
    for p in parts:
        for i, v in enumerate(p): out[i] += v
    return out

def gain(x, g): return [v * g for v in x]
def mul(x, e): return [a * b for a, b in zip(x, e)]
def delay(x, sec): return silence(sec) + x

def lowpass(x, fc):
    a = 1 - math.exp(-2 * math.pi * fc / SR); y = 0.0; out = []
    for v in x:
        y += a * (v - y); out.append(y)
    return out

def highpass(x, fc):
    lp = lowpass(x, fc); return [a - b for a, b in zip(x, lp)]

def bandpass(x, f, q):
    w = 2 * math.pi * f / SR; al = math.sin(w) / (2 * q); c = math.cos(w)
    b0, b2, a0, a1, a2 = al, -al, 1 + al, -2 * c, 1 - al
    x1 = x2 = y1 = y2 = 0.0; out = []
    for v in x:
        y = (b0 * v + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, v, y1, y; out.append(y)
    return out

def tone(sec, f0, f1=None, shape="sine", attack=0.001, decay=0.05):
    length = n(sec); f1 = f0 if f1 is None else f1; ph = 0.0; out = []
    e = env(length, attack, decay)
    for i in range(length):
        t = i / max(1, length - 1)
        f = f0 * (f1 / f0) ** t
        ph += 2 * math.pi * f / SR
        s = math.sin(ph) if shape == "sine" else (1.0 if math.sin(ph) >= 0 else -1.0) * 0.6
        out.append(s * e[i])
    return out

def hit(sec, f, q, decay, attack=0.0005):
    length = n(sec); return mul(bandpass(noise(length), f, q), env(length, attack, decay))

def click(sec, cutoff, decay):
    length = n(sec); return mul(highpass(noise(length), cutoff), env(length, 0.0003, decay))

def soft(sec, cutoff, decay, attack=0.002):
    length = n(sec); return mul(lowpass(noise(length), cutoff), env(length, attack, decay))

def piano(f, sec=1.1):
    length = n(sec); out = [0.0] * length
    for h in range(1, 8):
        fh = f * h * (1 + 0.0004 * h * h)          # slight stretch, like real strings
        if fh > SR / 2.2: break
        amp = 1 / h ** 1.4; dec = 0.9 / (1 + 0.6 * (h - 1))
        ph = random.random() * 6.28
        for i in range(length):
            out[i] += amp * math.sin(ph + 2 * math.pi * fh * i / SR) * math.exp(-i / (dec * SR))
    a = n(0.004)
    for i in range(a): out[i] *= i / a
    return mix(out, gain(click(0.02, 1500, 0.004), 0.08))

def write(path, x, level=0.8):
    # Even loudness across packs: scale to a target RMS, but never past the peak limit.
    peak = max(1e-6, max(abs(v) for v in x))
    rms = max(1e-6, (sum(v * v for v in x) / len(x)) ** 0.5)
    peak = max(peak / level, rms / 0.17) * level
    fade = n(0.006)
    for i in range(min(fade, len(x))): x[-1 - i] *= i / fade
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v / peak * level)) * 32767)) for v in x))

PACKS = {
    "crisp": dict(
        key=lambda: mix(hit(0.06, 3500, 2, 0.012), gain(tone(0.05, 2200, decay=0.008), 0.25)),
        space=lambda: hit(0.08, 2000, 1.5, 0.02),
        enter=lambda: mix(hit(0.08, 2800, 2, 0.02), gain(tone(0.08, 1500, decay=0.02), 0.3)),
        delete=lambda: hit(0.05, 4200, 2, 0.01)),
    "typewriter": dict(
        key=lambda: mix(click(0.05, 3000, 0.004), gain(hit(0.08, 1200, 4, 0.02), 0.7), gain(tone(0.08, 140, 100, decay=0.02), 0.5)),
        space=lambda: mix(click(0.06, 2500, 0.005), gain(tone(0.1, 110, 80, decay=0.03), 0.8)),
        enter=lambda: mix(gain(soft(0.35, 900, 0.12, attack=0.02), 0.5),
                          delay(mix(tone(1.0, 2093, decay=0.35), gain(tone(1.0, 3140, decay=0.25), 0.5)), 0.12)),
        delete=lambda: mix(click(0.05, 2500, 0.004), gain(hit(0.08, 900, 4, 0.02), 0.7))),
    "thocky": dict(
        key=lambda: mix(tone(0.1, 190, 120, decay=0.03), gain(soft(0.08, 900, 0.02), 0.6)),
        space=lambda: mix(tone(0.12, 140, 90, decay=0.04), gain(soft(0.1, 700, 0.03), 0.6)),
        enter=lambda: mix(tone(0.12, 160, 100, decay=0.035), gain(soft(0.1, 800, 0.03), 0.6)),
        delete=lambda: mix(tone(0.1, 210, 140, decay=0.025), gain(soft(0.08, 1000, 0.02), 0.6))),
    "clacky": dict(
        key=lambda: mix(click(0.04, 4000, 0.004), gain(hit(0.06, 3800, 6, 0.015), 0.6), gain(tone(0.05, 260, decay=0.01), 0.3)),
        space=lambda: mix(click(0.05, 3000, 0.006), gain(hit(0.07, 2500, 5, 0.02), 0.6)),
        enter=lambda: mix(click(0.05, 3500, 0.006), gain(hit(0.08, 3000, 6, 0.025), 0.7)),
        delete=lambda: mix(click(0.04, 4500, 0.004), gain(hit(0.05, 4200, 6, 0.012), 0.6))),
    "creamy": dict(
        key=lambda: mix(soft(0.1, 600, 0.035), gain(tone(0.09, 280, 240, attack=0.002, decay=0.03), 0.6)),
        space=lambda: mix(soft(0.12, 450, 0.045), gain(tone(0.1, 200, 170, attack=0.002, decay=0.04), 0.6)),
        enter=lambda: mix(soft(0.12, 500, 0.045), gain(tone(0.1, 240, 200, attack=0.002, decay=0.04), 0.6)),
        delete=lambda: mix(soft(0.09, 700, 0.03), gain(tone(0.08, 320, 270, attack=0.002, decay=0.025), 0.6))),
    "bubble": dict(
        key=lambda: tone(0.09, 380, 900, attack=0.004, decay=0.03),
        space=lambda: tone(0.12, 260, 620, attack=0.004, decay=0.04),
        enter=lambda: mix(tone(0.09, 380, 900, attack=0.004, decay=0.03), delay(tone(0.1, 500, 1200, attack=0.004, decay=0.03), 0.07)),
        delete=lambda: tone(0.09, 850, 380, attack=0.004, decay=0.03)),
    "water": dict(
        key=lambda: mix(tone(0.05, 1400, 600, decay=0.015), gain(delay(tone(0.15, 620, decay=0.05), 0.02), 0.4)),
        space=lambda: mix(tone(0.06, 1000, 420, decay=0.02), gain(delay(tone(0.18, 440, decay=0.06), 0.025), 0.4)),
        enter=lambda: mix(tone(0.06, 1700, 700, decay=0.02), gain(delay(tone(0.25, 720, decay=0.08), 0.025), 0.5)),
        delete=lambda: mix(tone(0.05, 1200, 520, decay=0.015), gain(delay(tone(0.12, 540, decay=0.04), 0.02), 0.4))),
    "wood": dict(
        key=lambda: mix(hit(0.1, 900, 12, 0.03), gain(hit(0.1, 1450, 12, 0.02), 0.6)),
        space=lambda: mix(hit(0.12, 650, 12, 0.04), gain(hit(0.12, 1050, 12, 0.03), 0.6)),
        enter=lambda: mix(hit(0.12, 1200, 12, 0.035), gain(hit(0.12, 1900, 12, 0.025), 0.6)),
        delete=lambda: mix(hit(0.1, 780, 12, 0.03), gain(hit(0.1, 1250, 12, 0.02), 0.6))),
    "8bit": dict(
        key=lambda: tone(0.04, 880, shape="square", decay=0.03),
        space=lambda: tone(0.05, 440, shape="square", decay=0.04),
        enter=lambda: tone(0.04, 523, shape="square", decay=0.1) + tone(0.04, 659, shape="square", decay=0.1) + tone(0.06, 784, shape="square", decay=0.06),
        delete=lambda: tone(0.035, 660, shape="square", decay=0.1) + tone(0.04, 440, shape="square", decay=0.05)),
}

for pack, sounds in PACKS.items():
    for name, make in sounds.items():
        write(os.path.join(OUT, pack, name + ".wav"), make())
for name, f in (("piano_c4", 261.63), ("piano_c5", 523.25), ("piano_c6", 1046.5)):
    write(os.path.join(OUT, "piano", name + ".wav"), piano(f), level=0.7)
total = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(OUT) for f in fs)
print("packs:", len(PACKS) + 1, "total bytes:", total)
