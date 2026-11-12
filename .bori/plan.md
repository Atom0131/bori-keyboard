# Plan: Bori Keyboard
STATUS: APPROVED

## Goal
Open-source, fully offline Android keyboard (package com.bori.keyboard) replacing Samsung
Keyboard: Gboard-quality glide typing, strong spell check + prediction, full theme engine,
foldable-aware. English + Spanish. All our own code (other keyboards are inspiration only);
open word-list data imported with attribution. No NDK (Bori can't build C++). INTERNET only for
opt-in GIF search (KLIPY, user's own key, off by default; user decision 2026-11-11) and
opt-in weather for living themes (Open-Meteo, off by default; Stage 3.10).

## Architecture (single :app module, Kotlin)
- Keyboard: custom Canvas View + raw MotionEvents. Settings app: Compose + Material 3.
- Packages (com.bori.keyboard): ime/ layout/ touch/ swipe/ dict/ predict/ learn/ theme/
  clipboard/ modes/ voice/ settings/. Python dictionary tooling in tools/.
- Glide decoder: own pure-Kotlin geometric decoder (shape + location channels, trie pruning,
  LM rescoring). Neural decoder is optional later (onnxruntime-android AAR).
- License: GPL-3.0-or-later.

## Checklist
- [x] Stage 0 (built + installed OK): skeleton, IME service, method.xml (en_US, es_ES), setup wizard, tap keyboard
      (shift/caps, symbols, enter actions, globe, input-type layouts, preview, haptics)
- [x] Stage 1 (built + installed OK): dictionaries (en, es), suggestion strip, spell correction, autocorrect + undo,
      n-grams, learning (Room), user dict, blacklist, incognito
- [x] Stage 2 (built + working; tuning round 1 + Swipe Trainer + offset learning built OK; Session 6 correction tuning + number row + vibration slider + typo log written, awaiting build): glide typing, spacebar cursor, delete slide, key flicks, fold-aware geometry,
      Swipe Test Lab
- [x] Sentence-aware prediction (4-gram + learned phrases), Session 14, awaiting build
- [ ] Correction/prediction roadmap (Session 15; research + estimates in session-log Session 15):
  - [x] A: fixes from the typo log (written, awaiting build)
  - [ ] B: fit edit costs + split/autocorrect constants to data (tools/fit_costs.py; ~4-5 h unattended total)
  - [ ] C: per-user tap offsets (reuse swipe offset learning for taps)
  - [ ] D: more n-gram data (60M subtitle lines; small gain expected), user pair/triple decay
  - [ ] E: neural next-word LSTM trained on Colab (~1-2 h/lang on a T4), Kotlin inference
  - [ ] F: neural glide decoder (optional)
- [x] Clipboard upgrade + GIF search (Session 21, awaiting build)
- [ ] Stage 3: theme engine (in progress), Keys Cafe/SwiftKey/Gboard/LED-app features and more.
      Phases 3.1–3.10 in .bori/theme-plan.md (APPROVED). 3.1 + 3.2 built OK (user using a theme); 3.3 (key styles + 14 fonts) built OK 17:09; 3.4 backgrounds built OK; 3.5 effects + LED built OK; 3.6 sound packs built OK; 3.7 editor written (Session 32).
      Then a tidy-up pass on the autocorrect code.
- [x] Keyboard height per way of holding (folded/unfolded x upright/sideways), Session 25, built OK 19:12
- [ ] Fit-to-thumbs (user idea 2026-11-12, agreed; see session-log Session 25):
  - [x] Touch heat map (Session 26, built OK 19:12): taps per key per posture, Settings view,
        corrector aims at the learned shift (roadmap C for the corrector; key hit boxes not moved yet)
  - [x] Calibration (Session 27 + 28 fix, built, user: "better"): thumbs → height + split per posture,
        practice sentence → heat map seed; "Move keys to fit my aim" (tap targets from the map)
- [ ] Stage 4: clipboard w/ expiry, one-handed/split/floating, voice delegation, emoji panel
- [ ] Stage 5: F-Droid release (LICENSE, ATTRIBUTION, fastlane, gradle wrapper)
- [ ] Stage 6 (later): neural decoder, neural next-word, more languages

## Build order (user, 2026-11-12): one phase per Bori build, wait for the result each time
0. Build Sessions 25 + 26 (height per posture, heat map), fix errors.
1. Thumb calibration (CalibrationScreen: thumb rest → height + split, typed sentence → heat
   map seed, repeat folded/unfolded) + "Move keys to fit my aim" (tap targets from TouchMap).
2–8. Themes 3.4 → 3.10 (.bori/theme-plan.md). 3.4–3.7 done.
   Inserted (user, 2026-11-12): settings redesign. Done + approved: Sessions 33–34 (start page,
   every sub-page; rules in .bori/settings-style.md). Left: two-pane when unfolded + search.
   Then continue with 3.8.
9. Autocorrect tidy-up (no behaviour change; typo_bench/glide_bench unchanged).
10. Stage 4 (one-handed/floating, voice key, clipboard/emoji gaps), one build each.
11. Stage 5 (F-Droid prep; commit/push only when asked).
Offered after 9: roadmap B (needs ~4-5 h background run, ask first), E (Colab), F.

## Out of scope (v1)
3+ simultaneous languages, non-Latin swipe, handwriting, cloud sync, Translate.

## Roles
- Bori agent: quick edits, offline work
- Claude Code: big changes and planning
- Antigravity: Gemini-side work, scripts

<!-- Shared by every agent in this project. Set STATUS: APPROVED once you agree with it.
     Agents write what they did to .bori/handoff.md and hold .bori/agent.lock while working. -->
