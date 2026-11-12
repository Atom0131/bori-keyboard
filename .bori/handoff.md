# Handoff

Detailed per-session notes: `.bori/session-log.md` (read the latest entry first).
Settings UI rules: `.bori/settings-style.md`.

## Goal
Bori Keyboard (com.bori.keyboard): offline open-source keyboard, see .bori/plan.md.

## Stage 0 (done, built + installed)
Tap keyboard, IME service, layouts (en/es/symbols/number/phone), setup wizard. AppCompat is
declared explicitly in app/build.gradle.kts (Bori links Theme.AppCompat once Compose pulls
newer AndroidX from Maven).

## Stage 1 (Claude Code, code written, NOT yet built)
- tools/: hunspell_expand.py (own affix expander), build_dict.py (→ assets/dict/en.bdic,
  es.bdic, ~5 MB each: trie + freq + bigrams), verify_dict.py, suggest_sim.py (Python model
  of the corrector used to tune constants), README.md. Raw data in tools/.cache (git-ignored).
- dict/BinaryDictionary.kt (reader), dict/Dictionaries.kt (async loader, 2-language cache).
- predict/: SpellCorrector (weighted Damerau-Levenshtein trie search, touch-point proximity,
  cheap accents/apostrophes/double letters), Suggester (LM = bigram+unigram+user boosts,
  autocorrect decision by Aggressiveness), Composer, KeyProximity.
- learn/UserStore.kt: SQLite (plain SQLiteOpenHelper, not Room: no KSP needed) for learned
  words/pairs, personal dictionary, blocked words. Learn after 2 uses.
- ime/TypingSession.kt: composing text, autocorrect + backspace undo, punctuation after
  auto-space, double-space period, cursor-move detection (expected-cursor tracking), resume
  composing after backspace, learning, incognito.
- view/SuggestionStripView.kt: 3 slots, long-press → block word prompt, incognito icon.
- KeyboardView.Listener.onKey now passes touch x/y.
- Settings: suggestions, next-word, autocorrect level, block offensive, incognito,
  "Your dictionary" screen (add/remove/unblock/forget).
- ATTRIBUTION.md: data licenses (dictionaries are CC BY-SA 4.0).
- Simulated results (tools/suggest_sim.py): teh→the, dont→don't, im→I'm, recieve→receive,
  adress→address, cancion→canción, espana→España, tambien→también, manana→mañana.

## Stage 2 (Claude Code, code written, NOT yet built)
Glide typing, spacebar cursor slide, delete-word slide, upward key flicks, swipe trail,
cancel gestures on resize (fold/rotate), Swipe Test Lab. Files: swipe/GlideDecoder.kt,
swipe/SyntheticSwipe.kt, view/KeyboardView.kt (gesture modes), view/KeyOverlayView.kt
(trail), ime/TypingSession.kt (onGlide/moveCursor/deleteSlide), settings/SwipeTestLab.kt,
tools/glide_sim.py. Details in .bori/session-log.md (Session 3).

## Glide tuning (Session 4, not yet built)
Endpoint penalty, wider letter radius, geometry weight 2.0, key size from centre spacing,
realistic Test Lab swipes. See session-log Session 4.

## Session 5 (correction engine + Swipe Trainer, not yet built)
Real-word fixes (ans→and), missed-space splits (ofthe→"of the"), typo benchmark
(tools/typo_bench.py), per-user swipe offset learning, Swipe Trainer with copyable swipe
recordings. The first session was interrupted; a second one checked it and fixed a trainer bug.
See session-log Session 5.

## Session 6 (corrections, number row, vibration, typo log; not yet built)
and→ans learning fix (UserStore DB v3, `kept` table), first-letter typo cost 0.15 (rhe→the),
softer missing-bigram factor (almost tjere→there), split/long-word tuning, vibration strength
slider, opt-in typo log, number row (default on) with fixed keyboard height, long-press popup
keeps the default (e.g. 9 on "o") under the finger. Bigger benchmarks: tools/typo_bench.py
sets A–F, D1/D2, U; tools/glide_bench.py. See session-log Session 6.

## Session 7 (split keyboard for foldables; not yet built)
Split layout on by default on big screens (smallest width >= 600 dp: unfolded Fold, tablets),
cover screen stays normal; onboarding Step 3 + Look & feel switch + one-time "split the keys?"
prompt in the suggestion bar; sliders for space between halves and space at the edges
(live update). Settings reorganised into category pages; theme mode (system/light/dark/dark at night). YouTube invisible search text: fixed + confirmed (FILTER fields now compose). OPEN BUG: keyboard won't open with two apps side by side; Settings → Copy
diagnostics (ime/DiagLog.kt) added to find out why. See session-log Session 7.

## Session 8 (modern words; not yet built)
Hand-curated slang 1920s–2020s, texting, AI/tech words and app names in tools/wordlists/
(+789 en, +192 es words), merged by build_dict.py; .bdic flag 16 "keep as typed" for everyday
list words; typo_bench set M; swipe diagnostics in DiagLog. See session-log Session 8.

## Session 9 (clipboard, tap-to-fix, long words; not yet built)
Gboard-style clipboard (📋 panel with pin/delete + select all/cut/copy/paste, "just copied"
chip, 1 h expiry, never sensitive/incognito); tap a typed word to get fixes; long-word typo
budget and last-letter slip discount (en E 11+ letters 60-75% → 80-88%, C still 0%); swipe
twins (latte/late, llamé/llame) one tap away. New tools/shape_bench.py. See session-log Session 9.

## Session 9b (emoji panel; not yet built)
😊 key in the bottom row (text fields) opens an emoji panel: categories, recents (not in
incognito/passwords), held ⌫ repeats; unsupported emoji hidden. See session-log Session 9b.

## Session 10 (rhe fix, typo list, 500k-word vocabulary; not yet built)
rhe was learned as the user's own word, so it was never fixed: now only confirmed words
(personal, one undo, picked as typed) are protected. 25k-entry typo lists per language
(assets/dict/*_typos.txt, tools/build_typos.py). Dictionaries rebuilt with Wiktionary + wordfreq:
en 508k words, es 689k, 18.7 MB each (flags 32 rare / 64 weak = weakly valid). Shape promotion
for swipes tried and rejected. See session-log Session 10.

## Session 11 (dictionary load fix; built, user confirmed working)
After the Session 10 build swipe + correction were gone: the 18.7 MB dictionary most likely
failed to load (out of memory, swallowed silently). Now streamed into arrays, retried on OOM,
largeHeap on, load result/error in Copy diagnostics. See session-log Session 11.

## Session 12 (no-suggestions flag; user says good to go)
Google search box sets TYPE_TEXT_FLAG_NO_SUGGESTIONS, which turned off swipe, suggestions and
correction. Now ignored by default (new switch "Follow apps that turn suggestions off").
Settings no longer crashes on DeadSystemException. See session-log Session 12.

## Session 13 (tap accuracy; built, user confirmed better)
Taps typed the key under the finger at lift-off with no hysteresis, so thumb roll typed the
neighbour. Now the key where the finger landed counts, with AOSP-style hysteresis. See
session-log Session 13.

## Session 14 (sentence-aware prediction; not yet built)
4-gram next-word model from Tatoeba + OpenSubtitles (new assets/dict/*.ngram, bigger .bdic bigrams),
learned 3-word phrases (DB v4). Held-out top-3: en 19.4→28.8%, es 17.6→26.0%. See session-log
Session 14.

## Session 15 (typo-log fixes + roadmap; not yet built)
Fixes from the user's typo log: learned slips (rhe) no longer used as fixes for other typos,
"or"/"of"-style common 2-letter words never swapped, no "Mr"/name pieces in missed-space splits
(asmr → "as Mr"), long unfinished words complete on space (wednes → Wednesday), typo log now
records "edit" lines (what the user typed after undoing a fix). Roadmap stages A–F with time
estimates in plan.md. See session-log Session 15.

## Session 16 (emoji panel too tall; not yet built)
Opening emoji made the keyboard cover the screen: the key area grew to the emoji grid's full
height. view/InputRootView.kt `KeysFrame` now pins panels to the keyboard's height. See
session-log Session 16.

## Session 17 (swipe trail colour + thickness; not yet built)
Settings → Swipe & gestures: colour swatches (theme, rainbow, 10 colours), thickness 2–16 dp,
live preview. See session-log Session 17.

## Session 18 (emoji styles + light-blue 🇵🇷; not yet built)
Settings → Appearance → Emoji style: Twemoji (default), Fluent, OpenMoji, Noto, Blobs,
EmojiOne 2, Firefox, Phone default. Packs in assets/emoji (+15.5 MB, tools/build_emoji_packs.py).
Puerto Rico flag drawn with the light-blue triangle in every style. See session-log Session 18.

## Session 19 (emoji panel crash; not yet built)
"Tapping 😊 switched to Gboard": it was the 🌐 key next to 😊. 🌐 now only shows where there's
no 😊 (hold space for the keyboard picker). Built; user confirmed it works.
Settings resetting on install: user says fixed now; diagnostics log "settings … firstInstall="
at startup in case it returns (Session 19).
Copy diagnostics now puts the crash first; panel errors are caught and logged; a style that
crashes the keyboard switches to the phone's font automatically. See session-log Session 19.

## Session 20 (app icon + GitHub)
Celeste/red/coquí launcher icon like the other Bori apps (tools/gen_icon.py); project pushed
to GitHub as a public repo (GPL-3.0). See session-log Session 20.

## Session 21 (clipboard upgrade + GIF search; not yet built)
Clipboard keeps text + pictures for 1 h/1 day/1 week (default)/forever, up to 200, search,
Undo after Clear, deleted clips stay deleted. GIF button in the emoji panel → KLIPY search
(opt-in, the user's own key in Settings → GIFs; INTERNET added only for this). See
session-log Session 21.
Also: hub → huh fixed (the user's boosts no longer count toward real-word fixes); typo log
lines have times and copy newest first.

## Session 22 (Stage 3 plan approved + phase 3.1; built OK)
Plan v2 (research on Keys Cafe, SwiftKey, Gboard, LED apps; weather, holidays, fonts,
sounds): .bori/theme-plan.md, APPROVED. Phase 3.1: theme/ThemeSpec.kt, ThemeResolver.kt,
ThemeStore.kt, assets/themes/classic.json; the keyboard re-themes live on theme changes.
Should look exactly as before. See session-log Session 22.

## Session 23 (theme gallery, phase 3.2; built OK 16:49, user using a theme)
Settings → Themes: Gallery/Mine tabs, live mini-keyboard previews, light/dark chips, Key
borders switch. 21 presets (assets/themes/NN-id.json, made by tools/build_themes.py with a
4.5:1 contrast check). Engine: gradient backgrounds, key outlines (incl. rainbow), neon glow.
See session-log Session 23.

## Session 24 (key styles + fonts, phase 3.3; Bori build OK 17:09, phone test pending)
3.2 built OK (16:49) and the user is using a theme. New: theme/KeyPainter.kt draws keys flat /
raised / sunken / outline / floating, shapes rounded / square / pill / circle / squircle; 14
bundled fonts (808 KB, tools/build_fonts.py); Settings → Themes → "Keys & letters" overrides
(style, shape, font, letter case, size, transparency, hints). Presets got matching fonts.
See session-log Session 24.

## Session 25 (height per fold/rotation; not yet built)
Keyboard height is now saved separately for folded/phone vs unfolded/tablet, upright vs
sideways (view/BigScreen.kt `Posture`). Settings → Appearance & layout shows the current one
first (live) and the other three below. Range 60–150%. Heat map + thumb calibration agreed as
the next feature, in plan.md. See session-log Session 25.

## Session 26 (touch heat map; not yet built)
learn/TouchMap.kt records where taps land on each key (per posture), from words kept as typed,
picked, or fixed with up to 2 swapped letters (an undone fix is dropped). The corrector aims at
each key's learned shift after 15 taps. Settings → Corrections → Touch heat map shows dots
per key, with copy/clear. See session-log Session 26.

## Session 27 (P1 thumb calibration + aim; not yet built)
Sessions 25 + 26 built OK (19:12). New: Settings "Fit to my thumbs" (settings/CalibrationScreen.kt):
rest thumbs → height for this posture (+ split edge/gap when unfolded), type a pangram on a
practice keyboard (real KeyGeometry) → heat map seed; fold/unfold and repeat (Settings now keeps
its page across a fold). Opened from onboarding (optional card), Key height, the heat map, and
a one-time strip prompt. "Move keys to fit my aim" (default on): near a letter border the key
with the closer learned aim point wins (KeyboardView.aimedKeyAt). See session-log Session 27.

## Session 28 (calibration too tall; not yet built)
Calibrated keyboard was far too tall. Rows now capped at 8% of the screen (view/BigScreen.kt
KeySize, shared with calibration), thumbs read as the top/middle row line, calibration max 130%,
and a height slider on the practice step. See session-log Session 28.

## Session 29 (P2 = theme 3.4 backgrounds; not yet built)
Calibration fix (Session 28) built; user: "better, let's keep going". New: Settings → Themes →
Background card (Theme's / Plain / Gradient / Scene / Photo) on top of any theme; theme/Backdrop.kt
(BackgroundSpec in ThemeVariant.backdrop + override pref, cached BackdropRenderer, 4 drawn
scenes with night versions); photo via the system photo picker, copied to filesDir/backgrounds;
text over pictures auto-switches to black/white when below 4.5:1. 3 new scene presets.
See session-log Session 29.

## Session 30 (P3 = theme 3.5 press effects + LED lighting; not yet built)
Backgrounds (Session 29) built OK 19:32, user sees the options. New: Themes → "Effects &
lighting": 10 press effects (view/EffectsView.kt, particle pool, idle = no redraws) with theme /
rainbow / 8 colours; LED modes rainbow wave, breathing, light up on press, colour cycle, row
rainbow (view/LedLights.kt) on keys / letters / outlines, speed + brightness. Off in password
fields and with system animations off. See session-log Session 30.

## Session 31 (P4 = theme 3.6 sound packs; not yet built)
Effects + LED (Session 30) built with no errors. New: Settings → Typing → Sound: 10 packs
(Crisp tap, Typewriter with bell, Thocky, Clacky, Creamy, Bubble, Water drop, Wood block, 8-bit,
Piano) + the phone's own; volume; try buttons. Synthesized by tools/build_sounds.py (pure
Python, 350 KB of WAVs). ime/SoundPacks.kt (SoundPool, files copied out once per install).
Follows silent/vibrate mode; off in password fields. See session-log Session 31.

## Session 32 (P5 = theme 3.7 editor; not yet built)
Sound packs (Session 31) built with no errors. New: Themes → "Make your own from …" copies the
selected theme into Mine and opens settings/ThemeEditorScreen.kt (route "edit:<id>"): tap the
preview to pick a part (again for its label), HSV + see-through + hex + quick colours, day/night
colours, gradient background, key style (look, shape, font, roundness, outline, glow,
see-through, spacing), contrast warnings < 4.5:1, undo, rename, duplicate, delete. Saves as you
go and becomes the keyboard's theme. See session-log Session 32.

## Session 33 (settings redesign, build 1 of 2; not yet built)
Theme editor (Session 32) built OK 19:51. User asked for concise, better-organised settings after
researching settings UI. Following the Android settings guide: new settings/SettingsUi.kt
(SettingsPage with top bar + pinned "Try the keyboard here" bar, Group, NavRow, SwitchRow,
ChoiceRow dialog, SliderRow, InfoButton), SettingsIcons.kt (path-data icons, no icon library),
CategoryScreens.kt (Typing, Corrections, Swipe & gestures, Layout & size, Emoji & GIFs,
Clipboard & privacy, Help). Start page = 7 groups with status lines. See session-log Session 33.

## Session 34 (settings: every page below the start page; built OK 20:49, user: "amazing")
Build 1 (Session 33) built; user: "looks great" but category pages too long and listy; apply the
same pattern to every layer. Each page now shows only its main rows; detail moved one level down
behind rows with status lines: Theme hub (Gallery, Dark mode, Key borders, Keys & letters,
Background, Effects & lighting, Design your own → own pages), Typing → Sound & vibration,
Swipe → Swipe trail, Layout → Other ways of holding / Split keyboard, Emoji → GIF search.
Touch heat map, Dictionary, theme editor (colour picker in a dialog), Swipe Trainer and Test Lab
use the grouped style. **Settings style guide: .bori/settings-style.md** (follow it for any
new settings). See session-log Session 34.

## Session 35 (suggestion bar invisible; built OK 10:26, user: "it is back!")
On solid-colour themes the keyboard's background fill painted over the suggestion bar (📋, ⚙,
suggestions) since Session 30 turned off child clipping. Keyboard, bar and search bar now fill
only their own rectangle. See session-log Session 35.

## Session 36 (P6 = theme 3.8 share + Surprise me; built OK 10:34, phone test pending)
Theme → "Surprise me" (random, always ≥ 4.5:1, re-tap replaces, Undo) and a Share group:
"Share <theme>" (.boritheme zip with your Keys & letters + Background baked in) and "Add a
shared theme" (file picker; Bori is also in the share sheet). Editor has Share too. Import is
size/name-checked and always lands in Mine. See session-log Session 36.

## Session 37 (calibration layout; not yet built)
Fit to my thumbs: sideways the practice keys covered the text and slider, and the text was left-
aligned when unfolded. Typing step is now one stacked column (header, instructions, slider,
centred sentence, keys); all text centred. See session-log Session 37.

## Session 38 (day + night themes, Coquí day, El Yunque scene; not yet built)
Every built-in theme except the 5 fixed ones (Snow, Charcoal, Pure Black, High Contrast Light/Dark)
now has day + night colours; "Coquí Night" is now "Coquí" (id unchanged) with a day look and a new
drawn El Yunque scene (Backdrop.kt). Gallery cards show "☀ ☾". See session-log Session 38.

## Session 39 (remember emails + usernames; not yet built)
Email and sign-in fields show saved emails/usernames in the bar (+ "@gmail.com"-style endings after
@). Learned from email fields (shown after 2 uses) and from the field before a password field (at
once). Settings → Clipboard & privacy → Emails & usernames. learn/LoginStore.kt. See session-log
Session 39.

## Session 40 (prediction + modern words; built OK, user: "looking good")
AI/phone/brand/slang word lists (en + es, Puerto Rico first), texting phrase lists counted into
the n-grams (modern words now predicted in context: en 1/25 → 19/25, es 1/15 → 10/15),
conversation cache (predictions only since Session 45). typo_bench: C 0% en + es, B/E/F same.
Round-2 phrases (en 696 / es 399 lines) rebuilt 16:47 (needs a Bori build). How-to: tools/README.md
"Prediction round". See session-log Session 40.

## Session 41 (theme 3.9 ambient effects; not yet built)
Theme → Effects & lighting → Background effect: rain, snow, leaves, sun rays, clouds, storm,
stars, aurora, fireflies, confetti, hearts, fireworks (view/AmbientView.kt). See Session 41.

## Session 42 (smoother LED + background frames; not yet built)
LED lighting and background effects now animate on the display's vsync at 60 fps
(view/FrameTicker.kt) instead of an uneven 33 ms timer. See session-log Session 42.

## Session 43 (Razer-style rainbow, full refresh rate; not yet built)
Rainbow wave / row rainbow are one continuous gradient across the keys (not a colour per key),
animated every vsync (120 fps on the Fold), highest display mode requested while animating; new
Rainbow size + Direction. See session-log Session 43.

## Session 44 (choppy rainbow fixed; built, user: "perfect now")
The LED clock was a Float (moved in 16–32 ms steps); now Double + vsync time; Android 15+
high-frame-rate vote; fps logged to Copy diagnostics. See session-log Session 44.

## Session 45 (English correction check; not yet built)
Old vs new dictionaries give identical fixes for the user's logged typos; conversation cache now
only for predictions. See session-log Session 45.

## Session 46 (ETA/ASAP kept as typed; en dictionary rebuilt)
New tools/wordlists/en_abbrev.txt: 145 abbreviations/acronyms kept as typed (ETA, ASAP, RSVP,
WFH, TV, NFL, FBI, DNA, ADHD, DMV, VPN, SUV, LGBTQ, EST…) + phrases.
See session-log Session 46.

## Next step
0. User: Build + Install (round-2 dictionaries, Session 45 cache change, Session 46 abbreviations). Then: Build + Install; check the rainbow wave is smooth and blended (if not: Copy diagnostics, the "anim led" lines); try Background effect on a dark and a light theme.
   Claude: finish Session 40 pending (benches, round-2 rebuild).
   Also: sign in to something twice and check the bar; Settings → Clipboard &
   privacy → Emails & usernames. Also: Dark mode Always light/Always dark in Themes, Coquí day + night.
   Also: redo Fit to my thumbs sideways + unfolded; try Share → re-import,
   Surprise me ×10 + Undo. Then 3.9 ambient
   effects, 3.10 living themes; settings two-pane + search after the theme phases.
   Then 3.9, 3.10, autocorrect tidy-up, Stage 4, Stage 5 (plan.md "Build order").
1. User: Install the 17:09 build if not done (Session 24, phase 3.3). Settings → Themes → Keys & letters: try each
   key style, shape and font with the test box open; check Dot Matrix, Retro Typewriter,
   Mechanical, Neon LED, Snow. Paste any build errors.
2. Then phase 3.4 (backgrounds: photo, gradients, scenes), see .bori/theme-plan.md.
3. Older checks never confirmed on the phone (do when convenient): clipboard history +
   pictures + Undo, GIF search with a KLIPY key, emoji styles + 🇵🇷, swipe trail settings,
   typo-log fixes (wednes, asmr, rhe), predictions ("what are you", "voy a"), split keyboard
   on the Fold, Swipe Trainer data. Details in the session log entries 6–21.
4. Git: Sessions 6–34 are committed on branch `stage3-themes-settings` and pushed; pull request
   https://github.com/Atom0131/bori-keyboard/pull/1 (not merged yet). pics/ is git-ignored
   (private screenshots).

## Last Bori build
Result: OK
Time: 2026-11-12 17:14
debug build, app-debug.apk
