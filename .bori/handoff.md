# Handoff

Detailed per-session notes: `.bori/session-log.md` (read the latest entry first).

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

## Next step
000000000. User: Build + Install; check the new app icon.
00000000. User: try each emoji style + 🇵🇷 in Flags (not tested yet).
0000000. User: Build + Install; try each emoji style and the 🇵🇷 flag.
000000. User: Build + Install; set trail colour/thickness and swipe.
0000. User: Build + Install; tap 😊, the panel should be keyboard-sized and scroll.
00. User: Build + Install; type wednes, necc, "points or", rje/rhse, asmr; then Copy typo log
    after a few days of typing (new "edit" lines) and paste it.
000. User: Build + Install; test predictions ("what are you", "voy a") and Copy diagnostics ngram line.
0. User: Build + Install; test swipe + corrections in the Google search box and Messages.
1. User: Build + Install APK (the 2026-11-01 11:21 build had Sessions 6-9; 9b emoji and 10
   are unbuilt); paste any errors to fix. Then test emoji panel, rhe/teh/adn/qeu, foldable,
   agentic, rizz, copacetic, tachycardia (tap + swipe), wepa, keyboard speed and memory.
2. Test rhe→the, almost tjere→there, ot→it, "and" stays "and", number row + its switch,
   long-press o → 9 (number row off), vibration slider; on the Fold: split + prompt when
   unfolded, normal on the cover screen.
3. Paste Swipe Trainer "Copy data" (goes to tools/.cache/raw/swipes/ for glide_bench set R).
4. Stage 3: theme engine.

## Last Bori build
Result: OK
Time: 2026-11-05 11:53
debug build, app-debug.apk
