# Stage 3 plan: theme engine ("the most fun keyboard")
STATUS: APPROVED (user, 2026-11-11, Session 22). Parent plan: `.bori/plan.md` Stage 3.

## Goal and user answers
Everything Samsung Keys Cafe offers, plus what SwiftKey, Gboard and the LED/RGB keyboard apps
do: rainbow/LED effects, seasonal/holiday/weather themes, typewriter and iPhone-style sounds,
a Nothing-style dot-matrix look, raised or flat keys, crazy and normal fonts, press effects,
background effects and a design-your-own editor. All art, sounds and names are ours (or
OFL/Apache/CC with ATTRIBUTION entries). No Samsung, Apple or Nothing assets.
1. Keep the phase order. 2. Password fields: no press effects, sounds or ambient effects
(vibration stays). 3. Bundle free fonts (~1–1.5 MB). 4. No screenshots, so research instead.

## Research summary
- Keys Cafe: per-key colours, colour effects (RGB while typing), tap motion effects with a
  colour sphere, custom touch sounds, Gallery photo backgrounds, separate night colours,
  "apply effects only" over the current theme, share/export/backup. Key resizing → Stage 4;
  games and Typo Analyze are out of scope.
- SwiftKey: Gallery / Yours / Custom tabs; photo themes (resize, brightness, key borders
  on/off, hints on/off).
- Gboard: solid colours, gradients, landscapes; a key-borders switch on every theme; photo
  with position/zoom/brightness; "My themes".
- LED/RGB apps: rainbow wave, breathing, pulse, ripple on touch with colour/speed/brightness;
  neon, glass/candy glass, 3D, gaming, mechanical styles; sounds such as thocky, clacky,
  creamy, typewriter, water drop, piano.
- Nothing look: Ndot is proprietary, so we use **Doto** (OFL-1.1) → our "Dot Matrix" theme.
- iPhone sound: synthesize our own soft "Crisp tap" (no Apple recordings).
- Weather: Open-Meteo (free, no key, data CC BY 4.0, attribution required).

## Architecture
- `theme/ThemeSpec.kt` (@Serializable, version 1, all fields optional) → `theme/ThemeResolver.kt`
  → `KeyboardTheme` (views read only this) ; `theme/ThemeStore.kt` (assets/themes/*.json +
  filesDir/themes/<id>/theme.json; Prefs.themeId, Prefs.themeRevision).
- Colours: `#hex` or Material You tokens `@accent1_600`, with `|` fallbacks.
- Later: background drawn once in InputRootView; `view/EffectsView.kt` particle/lighting
  overlay (0 fps when idle, ambient ≤30 fps, follows "remove animations"); `ime/SoundPacks.kt`
  (SoundPool) with packs from `tools/build_sounds.py`; fonts from `tools/build_fonts.py`
  (needs `pip install fonttools`).

## Phases (each one is a separate Bori build + phone test)
- [x] 3.1 Foundation: ThemeSpec/Resolver/Store, assets/themes/classic.json, live re-apply on
      theme id / mode / revision / config change. No visual change. (Session 22, awaiting build)
- [x] 3.2 Gallery + ~20 presets (Session 23, awaiting build; 21 themes, gradients, outlines, glow) (Gallery/Mine tabs, live previews, key-borders switch on all):
      Classic, Material You, Solid light/dark, High contrast ×2, Dot Matrix, Neon LED,
      Mechanical, Candy Glass, Glass, Borinquen, Coquí Night, Pastel, Sunset/Ocean/Aurora,
      Retro Typewriter, Galaxy, Gamer RGB.
      Session 38: every preset except Snow, Charcoal, Pure Black and High Contrast ×2 has day +
      night colours; Coquí Night → "Coquí" (id coqui_night) with a day look + El Yunque scene.
- [x] 3.3 Key styling + fonts (Session 24, awaiting build; background opacity moved to 3.4): flat / raised 3D / sunken / outline / floating; rounded, pill,
      circle, square, squircle; key/background opacity; label size, hints, case. Fonts:
      Inter, Nunito, Atkinson Hyperlegible, JetBrains Mono, Doto, Press Start 2P, Bungee,
      Monoton, Orbitron, Pacifico, Permanent Marker, Special Elite, Creepster, Comic Neue,
      + the phone's font.
- [x] 3.4 (Session 29, built OK 19:32; also 3 scene presets: Mountains, Beach, City Lights) Backgrounds: photo (photo picker; zoom, brightness, blur, auto text colour),
      gradients, procedural scenes (mountains, beach, skyline, dot grid).
- [x] 3.5 (Session 30, built OK) Press effects + LED lighting: ripple, pop, sparkle, bubbles, hearts, confetti,
      fireworks, emoji burst, light wave, finger glow; rainbow wave, breathing, reactive keys,
      colour cycle, row gradient; colour/speed/brightness; "apply effects only".
- [x] 3.6 (Session 31, built OK) Sound packs: Crisp tap, Typewriter (bell on Enter), Thocky, Clacky, Creamy, Bubble,
      Water drop, Wood block, 8-bit, Piano mode; volume; follows silent mode.
- [x] 3.7 (Session 32, built OK 19:51) Design-your-own editor: live preview, tap a key to paint it, HSV/hex picker,
      night colours, contrast warning < 4.5:1, undo, duplicate/rename/delete.
- [x] 3.8 (Session 36, built OK 10:34) Share (.boritheme zip) + import + Surprise me.
- [x] 3.9 (Session 41, awaiting build) Ambient background effects: rain, snow, leaves, sun rays, clouds, lightning,
      starfield, aurora, fireflies, confetti, hearts, fireworks.
- [ ] 3.10 Living themes: time of day (offline sun times), seasons, holidays (offline, incl.
      Three Kings, PR Constitution Day, Navidad through San Sebastián), weather (opt-in, off
      by default; coarse location or typed city; coordinates rounded to 0.1°; hourly at most;
      Open-Meteo credit).
~14–17 sessions. Then the autocorrect tidy-up, then Stage 4.

## Rules
No allocations in onDraw; bitmaps sized to the keyboard; effects capped and idle at 0 fps;
themes never contain typed text; panels follow the theme; contrast check on generated and
edited themes; licences recorded in ATTRIBUTION.md.
