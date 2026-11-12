# Changelog

All notable changes to Bori Keyboard. Detailed engineering notes for every session are in
`.bori/session-log.md`.

## 0.2.0 — 2026-11-12

The biggest update since the first release: a full theme engine, a redesigned Settings app,
fit-to-your-thumbs calibration, much smarter prediction with today's vocabulary, and lots of
fixes. Everything still runs **offline** and stays **on your phone**.

### 🎨 Themes — "the most fun keyboard"
- **Theme gallery** with live mini-keyboard previews, Gallery / Mine tabs and a Key borders
  switch. 24 built-in themes in Simple, Glow & LED, Glass, Scenes and Puerto Rico groups.
- **Day and night for every theme.** All presets except the deliberately fixed ones (Snow,
  Charcoal, Pure Black, High Contrast Light/Dark) now have their own day and night colours,
  following Settings → Theme → Dark mode (system, always light/dark, or dark at night). Gallery
  cards that change at night show "☀ ☾".
- **Coquí** (was "Coquí Night"): leafy greens by day and the night look after dark, both with a
  new hand-drawn **El Yunque** rainforest scene: misty peaks and ferns with the sun, or a moon,
  stars and fireflies at night.
- **Key styles:** flat, raised, sunken, outline and floating keys; rounded, square, pill, circle
  and squircle shapes; letter size, case, hints and see-through keys.
- **14 bundled fonts** (Inter, Nunito, Atkinson Hyperlegible, JetBrains Mono, Doto, and more).
- **Backgrounds:** solid, gradients, drawn scenes (Mountains, Beach, City, El Yunque, Dot grid)
  or your own photo, with automatic readable text over pictures.
- **Design your own:** a full theme editor. Tap a part of the preview to colour it, choose day
  and night colours, gradients, key look, outline and glow. It warns you when text gets hard to read.
- **Surprise me:** a random, always-readable theme on every tap, with Undo.
- **Share themes** as `.boritheme` files (Quick Share, Drive, chats) and add shared ones from
  Settings or the share sheet. Imports are size- and safety-checked.

### ✨ Effects, lighting and sound
- **Press effects:** ripple, pop, sparkle, bubbles, hearts, confetti, fireworks, emoji burst,
  light wave, finger glow.
- **LED lighting** like an RGB keyboard: rainbow wave, breathing, light up on press, colour cycle
  and row rainbow, on keys, letters or outlines.
  - **Razer-style continuous rainbow:** one smooth gradient flows through every key instead of a
    block of colour per key, with **Rainbow size** and **Direction** (left/right/diagonal).
  - **Runs at your screen's full refresh rate** (120 fps on 120 Hz phones), timed to the
    display's vsync. Fixes the choppiness: the old timer drifted, and its clock lost precision
    after a few days of phone uptime.
- **Ambient background effects (new):** rain, snow, falling leaves, sun rays, clouds,
  thunderstorm, starry night, aurora, fireflies, confetti, floating hearts, fireworks, with
  Amount and Speed. They draw behind the keys and adapt to light themes.
- **10 sound packs:** crisp tap, typewriter with bell, thocky, clacky, creamy, bubble, water
  drop, wood block, 8-bit, piano, plus a volume control. Synthesised for Bori, no recordings
  copied.
- Effects, lighting and sounds switch off in password fields and when the phone's animations are
  off.

### 🧠 Smarter typing and prediction
- **Today's vocabulary, English and Spanish:**
  - About 1,400 new English entries: AI (ChatGPT, Claude, Gemini, Copilot, prompt,
    hallucinate…), phones (eSIM, hotspot, Quick Share, AirDrop, sideload…), brands and apps
    (Zelle, Venmo, Cash App, DoorDash, Temu, Shein, TikTok…) and today's slang (rizz, delulu,
    mid, cooked, aura, crash out…).
  - About 550 new Spanish entries, **Puerto Rico first** (wepa, janguear, bregar, corillo,
    zafacón, guagua, chavos, revolú…), then Caribbean, Mexican, South American, Spain and
    Spanglish slang, plus tech words.
- **Everyday texting phrases** teach the keyboard when to suggest those words. "turn off" →
  Bluetooth, "send me a" → screenshot, "he has so much" → rizz, "wepa ya" → llegué, "te paso
  por" → Zelle / ATH. In those test contexts the new words now show up 19 of 25 times in English
  (was 1) and 11 of 15 in Spanish (was 1).
- **Abbreviations are kept as typed:** ETA, ASAP, RSVP, TBD, EOD, WFH, OOO, PTO, DIY, and 145
  acronyms such as TV, USA, NFL, NBA, FBI, IRS, DMV, DNA, ADHD, GPA, VPN, SUV, BBQ, LGBTQ,
  EST/PST. "eta" is no longer "corrected" to "eat", and all of them can be swiped.
- **Conversation memory for predictions:** words you just used in a chat (names, topics) come
  back sooner. Kept in memory only, never used in incognito or password fields, and never
  affects autocorrect.
- **Remember emails and usernames** (like Gboard and Samsung Keyboard). In email and sign-in
  fields the bar suggests the addresses and usernames you use, plus "@gmail.com"-style endings
  after "@". Stored only on your phone and never backed up. Passwords are never saved. Manage
  them in Settings → Clipboard & privacy → Emails & usernames.
- Correction fix from real typing: "hub" vs "huh" (your own learned words no longer push a
  real-word fix).
- Quality checks for this release: autocorrect never changed a correctly typed word (0% in the
  English and Spanish test sets). Everyday typo fixing is unchanged. Modern-word typos are fixed
  more often (English 69% → 73%, Spanish 73% → 80%), and modern words swipe better (English
  top-3 67% → 73%).

### 👍 Fit to your hand (built for foldables)
- **Keyboard height per way of holding:** folded or unfolded, upright or sideways, each
  remembered.
- **Touch heat map:** learns where your taps land on each key.
- **Fit to my thumbs:** rest your thumbs and type a practice sentence; Bori sets the height
  (and the split on big screens) and seeds your heat map. Works in all four postures.
- **Move keys to fit my aim:** near a key border, the key you usually mean wins.

### 📋 Clipboard and GIFs
- **Bigger clipboard:** text and pictures kept for 1 hour, 1 day, 1 week (default) or forever,
  up to 200 clips, with search, pins, and Undo after Clear.
- **GIF search** (opt-in, KLIPY, with your own key). This is the only feature that uses the
  internet.

### ⚙️ Settings, redesigned
- A new **Settings app**: a start page with seven groups that show their current status, short
  pages with details one level down, and a "Try the keyboard here" bar on every page.
- Settings keeps its page when you fold or unfold the phone.

### 🐞 Fixes
- The suggestion bar (with ⚙ and 📋) was invisible on solid-colour themes.
- On the Fit to my thumbs page, the practice keys covered the text when the phone was sideways,
  and the text was left-aligned when unfolded.
- Choppy LED lighting and background effects on 120 Hz screens.
- Sign-in fields labelled for a username or email no longer autocorrect or auto-capitalise.

### Known gaps / not yet tested on a phone
- Theme sharing between two phones, and Surprise me edge cases.
- Each ambient effect on every theme type, and long-run battery use at 120 fps.
- Email and username suggestions on pages that don't label their fields (some Chrome sites);
  you can add them by hand.

### Credits and licences
Bori Keyboard is GPL-3.0-or-later. Dictionary and phrase data: Hunspell/wooorm dictionaries,
Wiktionary (via kaikki.org), wordfreq, FrequencyWords/OpenSubtitles (OPUS) and Tatoeba. The new
word lists and texting phrases are Bori's own work. Fonts are OFL/Apache. Details are in
`ATTRIBUTION.md`.

## 0.1.0 — 2026-11-05
First public version: an offline keyboard with English and Spanish dictionaries (about 500k
words each), spell correction and autocorrect with undo, sentence-aware next-word prediction
(4-word context), learning and a personal dictionary, typo-log-driven correction fixes, glide typing with a Swipe Trainer, spacebar cursor and delete slides, a split
keyboard for foldables, number row, emoji panel with styles (including a light-blue 🇵🇷 flag),
clipboard, swipe trail options, typo log and diagnostics, and the celeste/red coquí app icon.
