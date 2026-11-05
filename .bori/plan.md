# Plan: Bori Keyboard
STATUS: APPROVED

## Goal
Open-source, fully offline Android keyboard (package com.bori.keyboard) replacing Samsung
Keyboard: Gboard-quality glide typing, strong spell check + prediction, full theme engine,
foldable-aware. English + Spanish. All our own code (other keyboards are inspiration only);
open word-list data imported with attribution. No NDK (Bori can't build C++), no INTERNET.

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
- [ ] Stage 3: theme engine (ThemeSpec JSON, built-ins, Material You, editor, import/export)
- [ ] Stage 4: clipboard w/ expiry, one-handed/split/floating, voice delegation, emoji panel
- [ ] Stage 5: F-Droid release (LICENSE, ATTRIBUTION, fastlane, gradle wrapper)
- [ ] Stage 6 (later): neural decoder, neural next-word, more languages

## Out of scope (v1)
3+ simultaneous languages, non-Latin swipe, handwriting, cloud sync, GIF/Translate.

## Roles
- Bori agent: quick edits, offline work
- Claude Code: big changes and planning
- Antigravity: Gemini-side work, scripts

<!-- Shared by every agent in this project. Set STATUS: APPROVED once you agree with it.
     Agents write what they did to .bori/handoff.md and hold .bori/agent.lock while working. -->
