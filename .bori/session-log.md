# Bori Keyboard — session log

Detailed notes from every working session, newest first. Read the latest entry (and
`.bori/plan.md`, `.bori/handoff.md`) before starting work. Add a new entry at the end of
every session.

---

## Session 46 — 2026-11-12 (Claude Code): ETA / ASAP kept as typed

### Why
User: round-2 build "looking good"; "it needs to learn this like eta and asap, it keeps correcting eta".

### Cause
verify_dict: "eta" freq 102, "asap" 110, no flags. Below REALFIX_RARE_FREQ (170) a valid word counts
as a rare look-alike, so a real-word fix could swap it (eta → eat). TBD, EOD, WFH, DIY, OOO, ICYMI
were flag 64 (weakly valid, never swiped).

### Fix
- tools/wordlists/en_abbrev.txt (new, own list): ETA, ASAP (tier 1), RSVP, TBD, TBA, EOD, EOW, WFH,
  OOO, PTO, OOTO, DIY, FAQ, ATM, ICYMI, aka, BYOB, DOB, SSN, PDF, URL, ETD, ASL, VIP, CEO, HR, PSA,
  EOM, NDA, KPI, ROI (tier 1–3). Tier ≤ 2 = kept as typed; listed words lose the weak flag.
- Left out on purpose: RIP, MIA, COB, ID (capitals would change "rip", "Mia", "cob", "id"/"I'd"),
  and texting shorthand already kept lowercase in en_modern.txt (tbh, imo, nvm, omw, dm, irl,
  fomo, yolo, pov, tldr). Note: a later file in name order wins the display form (en_modern's
  "=fyi" keeps fyi lowercase).
- en_phrases.txt: 26 lines with ETA/ASAP/RSVP/EOD/WFH/OOO/PTO/DIY/ATM/FYI ("what's your ETA",
  "call me ASAP"). English rebuilt (count → dict → ngrams → typos).
- Lesson: never `pkill -f <pattern>` from a Bash call whose own command line contains the
  pattern; it kills the calling shell (exit 144). Kill by pid.

### Round 2: "make sure it knows all the English ones"
- Scan (script was /tmp/scan/abbrev_scan.py, logic recorded here): Wiktionary entries tagged
  abbreviation/initialism/acronym (2–6 letters) × wordfreq Zipf ≥ 3 × our dictionary (freq < 170,
  weak/rare flag, or missing) → 2,607 candidates; only-uppercase forms and not a Hunspell word →
  864. Wiktionary tags are noisy (NOTES, CATS, names), so the final list was picked by hand.
- en_abbrev.txt now 145 entries: places/media (TV, USA, UK, EU, NYC, DC, PR, BBC, CNN, ESPN…),
  sports (NFL, NBA, NHL, MLB, NCAA, UFC, WWE, MVP, QB…), government/money (FBI, CIA, IRS, FDA, CDC,
  NASA, DMV, DUI, EBT, USPS, LLC, CFO, IPO, GDP, APR…), health/school (DNA, HIV, MRI, ICU, CPR,
  ADHD, PTSD, GPA, GED, MBA, PhD…), tech/cars/home (PC, DVD, HTML, VPN, QR, USB, SUV, RV, MPH,
  BMW, HVAC, AC, BBQ…), culture (LGBTQ, BLM, AARP, BTS), time zones (EST, PST, CST, GMT, UTC).
- Excluded (also everyday words): SAT, ACT, ER, AD, LA, UPS, COO, SNAP, WIC, AM, PM, IT, US, STEM,
  RIP, MIA, COB, ID.
- Rebuilt with /tmp/rebuild_dict.sh (build_dict → build_ngrams → build_typos, no recount: the
  counts didn't change) + suggest_sim spot checks.

### Build
Not built yet.

### Next step
User: Build + Install; type "eta", "asap", "what's your" → ETA, and acronyms (nfl, fbi, adhd, dmv).

---

## Session 45 — 2026-11-12 (Claude Code): "English correction felt better earlier"

### Why
User felt English correction got worse after Session 40 and that they make more typos; pasted
typo-log lines (buttergiled, pred, hava, themese, thrmes, tiue, pleae, tokw).

### Findings
- Only "buttergiled" (15:17) is from after the new dictionaries (built 14:18); the rest are older.
- tools/suggest_sim.py on all of them + 20 classic typos: **identical** results with the old
  (tools/.cache/baseline) and new en.bdic. (suggest_sim ignored BDIC before: fixed, `BDIC=` now works.)
- typo_bench en new vs old: B 86.2 = 86.2, C false changes 0% = 0%, E 73.0 = 73.0, F 77.8 vs 78.4,
  D1 89.7 vs 90.3 (1–2 items), M (modern words) better: changed 3.9→2.3%, fixed 69.0→73.1%.
  Set V "rare words changed 2.0→4.7%" is not a regression: V samples random rare words from each
  dictionary separately, so the two runs test different words.
- Real weaknesses in the log (old too): "themes" is rare in the subtitles (freq 91 vs these 196),
  so themese→these and thrmes→there's/three; tokw / tiue are far from the wanted words.
- The one Session 40 change the simulator can't see was the conversation cache, which also fed
  corrections: now **predictions only** (Suggester.cacheBoost in predict(); userBoost no longer
  includes it).
- "More typos" (taps, not fixes) isn't explained by any dictionary change; candidates to check on
  the phone: "Move keys to fit my aim" (learned shifts, Session 27) and typing with LED lighting on
  vs off (120 fps redraws on the main thread).

### Files
predict/Suggester.kt, tools/suggest_sim.py (BDIC env).

### Build
Not built yet. Brace/paren and nested-comment checks pass.

### Next step
User: Build + Install; if taps still feel worse, try a day with Settings → Corrections → Touch heat map →
"Move keys to fit my aim" off (and once with lighting off), and keep the typo log on.
Possible follow-up: let words the user types often (themes) beat common look-alikes sooner
(USER_WORD_BOOST, measured with typo_bench set U).

---

## Session 44 — 2026-11-12 (Claude Code): why the rainbow was still choppy at 120 Hz

### Why
User: still choppy after Session 43; "there's an Android SDK to read the screen frame rate and
adapt to it, why can't the keyboard?"

### Findings (research + code reading)
1. **Float clock (main cause):** LedLights did `now / 1000f` with `now` = uptime ms. As a Float, a
   clock of ~10^8–10^9 ms (days since boot) only changes in 8–64 ms steps, so the wave advanced
   ~30–60 times a second whatever the frame rate. Now all phases are computed in Double and reduced
   mod the period before going to Float (`phase()`, `breath()`).
2. **Draw-time clock:** frames used the clock at draw time, which wobbles a few ms per frame. Now
   animations use the Choreographer vsync time of the frame (FrameTicker.now()/frameTimeMs), so each
   frame steps exactly one refresh period. AmbientView too.
3. **Refresh rate:** an app can read the display's rate (Display.getRefreshRate / getMode) and
   pace itself to vsync (Choreographer) — the keyboard does both now. But the keyboard window isn't
   the focused app, so it can't pick the screen's mode by itself; the system decides from the app in
   front unless a view votes. Android 15+ has View.setRequestedFrameRate (category HIGH): the
   FrameTicker now votes while animating (by reflection, so it compiles on any SDK), on top of the
   Session 43 window preferredDisplayModeId. Votes are withdrawn when the animation stops.
4. **Per-frame garbage:** at 120 fps each frame made ~40 iterators (pressed-key check) and up to
   ~30 upper-case Strings; now one pass into a reused list, and a label cache.
5. **Measurement:** FrameTicker logs every 5 s of animation: measured fps, display Hz, frames that
   took longer than 1.5 vsyncs, whether the high-rate vote is on; the window mode choice is logged
   too. Settings → Help improve Bori → Copy diagnostics shows them ("anim led: …", "refresh: …").

### Files
view/FrameTicker.kt (rewritten), view/LedLights.kt (Double phases), view/KeyboardView.kt,
view/AmbientView.kt, ime/BoriImeService.kt (log).

### Build
Bori build OK; user: "this is perfect now" (smooth on the Galaxy Z Fold 8, 120 Hz screens).
User prefers full-refresh smoothness over battery.

### Next step
(Done.) If it ever regresses: run Rainbow wave ~10 s, then Copy diagnostics and paste the "anim led" and
"refresh" lines if it still isn't smooth (they say whether the screen ran at 120 Hz).

---

## Session 43 — 2026-11-12 (Claude Code): Razer-style continuous rainbow at full refresh rate

### Why
User (Galaxy Z Fold 8, both screens 120 Hz): Session 42's 60 fps still looked jittery; and in
pics/Screenshot_20261112_154449/154454 the rainbow was blocks: one flat colour per key (sampled at
its centre), neighbours ~36° of hue apart, so it didn't blend. Wanted a gradual Razer-like wave,
one even rainbow, at the screen's refresh rate ("battery is the user's prerogative").

### What changed
- **Continuous gradient** (view/LedLights.kt `framePaint`): Rainbow wave, Row rainbow and rainbow
  Breathing draw ONE LinearGradient (13 hue stops, REPEAT) across the keyboard; the wave only
  moves the shader matrix (one rainbow length per 3 s at speed 1). No allocations per frame
  (shader rebuilt only when size/orientation changes). Colour cycle, single-colour breathing and
  Light up on press keep per-key colours.
- theme/KeyPainter.kt: `overlay(c, paint)` fills the face of the key just drawn with the gradient
  (outline-look keys get it on their line), `outline(c, paint, w)` for gradient outlines.
- view/KeyboardView.kt: Keys target → gradient overlay at brightness alpha (not on pressed keys,
  so presses still show); Letters → labels drawn again with the shader; Outlines → gradient stroke.
- **Full refresh rate:** FrameTicker default = every vsync (no cap); AmbientView uses it too
  (FPS constant removed). BoriImeService.highRefresh(): while LED animates (or reactive) or a
  background effect runs, the IME window prefers the display mode with the highest refresh rate
  at the current resolution (preferredDisplayModeId; 0 = system choice when off). View
  setRequestedFrameRate (API 35) not used, to avoid a compile risk on Bori's android.jar.
- **New controls** (Effects & lighting → LED lighting, Prefs led_size / led_direction, live):
  Rainbow size (0.5–3 rainbows across, default "One rainbow") for wave + rows; Direction (Left to
  right / Right to left / Diagonal 35°) for the wave.

### Build
Not built yet. Brace/paren and nested-comment checks pass.

### Not tested on the phone
Wave on Keys / Letters / Outlines on both Fold screens; smoothness at 120 Hz; Rainbow size and
Direction; Row rainbow; key styles raised/outline/floating with the overlay; battery.

### Next step
User: Build + Install; Effects & lighting → Lighting: Rainbow wave. If 120 fps drains too much,
add a "Battery saver (60 fps)" switch (FrameTicker takes a cap).

---

## Session 42 — 2026-11-12 (Claude Code): smoother LED / background animation

### Why
User (after the 3.9 build): "it looks good but the background rgb effect needs to have smoother frames".

### Cause
LED lighting (KeyboardView) asked for its next frame with postInvalidateDelayed(33 ms), and the
ambient view did the same. A fixed timer isn't tied to the display's vsync, so frames landed 33 or
50 ms apart (uneven), which shows as stutter on a moving rainbow.

### Fix
- view/FrameTicker.kt (new): Choreographer frame callback; redraws on vsync, at most one frame
  per minIntervalMs (15 ms → 60 fps on 60 Hz and 120 Hz screens; every 2nd vsync on 120 Hz to
  save battery). Stops as soon as nothing requests a frame; cancelled when the view detaches.
- KeyboardView: LED animation uses it (LED_FRAME_MS removed). AmbientView: FPS 30 → 60 on the
  ticker; kick() cancels it when hidden/off. Motion was already time-based, so speeds don't change.

### Build
Not built yet. Brace/paren and nested-comment checks pass.

### Not tested on the phone
Rainbow wave / colour cycle / breathing smoothness; ambient effects at 60 fps; battery feel.

---

## Session 41 — 2026-11-12 (Claude Code): theme 3.9, ambient background effects

### Why
Session 40's build (themes, logins, new dictionaries) built with no errors. User: "let's keep going
with next phase of custom" → theme phase 3.9 from .bori/theme-plan.md.

### Decisions
- **view/AmbientView.kt** (new): 12 effects + None: Rain, Snow, Falling leaves, Sun rays, Clouds,
  Thunderstorm (rain + random flash and bolt), Starry night (twinkle + shooting star), Aurora (3
  sine bands, mirrored gradient), Fireflies (wander + pulse glow), Confetti (flipping), Floating
  hearts, Fireworks (bursts with gravity and drag). Fixed pool of 200 particles, no allocations
  per frame (except the lightning path seed), at most 30 fps (postInvalidateDelayed), dt capped.
- **Behind the keys:** InputRootView adds it under the column. While an effect runs,
  `syncAmbient()` sets `paintBackground = false` on the keyboard, the suggestion bar and the search
  bar, so their solid fill doesn't hide it; the root's own background still paints the theme.
  It shows in the key gaps, the bar and through see-through keys.
- **Readable on any theme:** the service passes `lightBackground` (theme background luma > 0.6);
  snow, rain, stars, clouds and fireflies switch to a dark ink on light themes.
- **Battery / rules:** stops while the keyboard is hidden (`running`, from onWindowShown /
  onWindowHidden, and set again when a new input view appears while shown), off in password
  fields and with system animations off (same `allowed` as press effects, Stage 3 rule 2).
- **Settings:** Theme → Effects & lighting gets a "Background" group first: Background effect
  (choice dialog) + Amount (20–100%) + Speed (50–200%) under it while on (style guide rule 6).
  Theme hub status line includes it. Prefs ambient_effect / ambient_amount / ambient_speed are
  in EFFECT_KEYS, so the keyboard updates live while the try-bar is open.

### Files changed
view/AmbientView.kt (new), view/InputRootView.kt, view/KeyboardView.kt, view/SuggestionStripView.kt,
view/SearchBarView.kt (paintBackground), ime/BoriImeService.kt (applyEffects, window shown/hidden),
settings/Prefs.kt, settings/EffectsSettings.kt, settings/ThemesScreen.kt (status).

### Build
Not built yet. Brace/paren and nested-comment checks pass.

### Known gaps
- Not in the theme JSON yet (a theme can't come with its own effect); 3.10 living themes can add
  that (rain when it rains).
- With an effect on, panels (clipboard/emoji) still paint their own background over it (fine).

### Not tested on the phone
Each effect on a dark and a light theme, solid / gradient / photo backgrounds; Amount and Speed;
stops when the keyboard hides; nothing in a password field; battery feel after a while.

### Next step
User: Build + Install, Settings → Theme → Effects & lighting → Background effect. Then 3.10.

### How to catch up
1. This entry. 2. view/AmbientView.kt. 3. InputRootView.syncAmbient + BoriImeService.applyEffects.

---

## Session 40 — 2026-11-12 (Claude Code): better prediction + modern vocabulary

### Why
User: get better at text prediction; teach AI terms, phone terms, every slang and every product.
Spot checks (verify_dict) showed many modern words already in the dictionary but with **no
context**: nothing led to them, so they were never predicted. Some were flagged weak (eSIM,
sideload, Temu, Shein: not swipeable), some missing (Zelle, One UI) or wrongly cased.

### User decisions
Spanish: Puerto Rico first, then Caribbean / Mexico / South America / Spain / Spanglish. Rude and
sexual slang: added like any word (not flagged offensive).

### What changed
- **Word lists** (tools/wordlists, own work): en_ai (~130), en_phone (~140), en_brands (~190),
  en_slang (~260, incl. rude), es_pr (~200: slang, food, towns, artists), es_slang (~215),
  es_tech (~135). Rule: capitals only for words that are only ever names (iPhone, WhatsApp);
  brand names that are everyday words (pixel, galaxy, target, zoom, claro, mercado, cursor,
  character, sonnet…) stay lowercase or are left out, or normal text would be capitalised.
  build_dict.py / build_typos.py / typo_bench.py read every `<lang>_*.txt` (tools/wordlist_files.py).
  Result: en +273 new words, 523 kept-as-typed; es +223, 470.
- **Phrase lists** (en_phrases 696 lines, es_phrases 399): everyday texting sentences with the new
  words. tools/ngram_count.py counts each line PHRASE_WEIGHT=100 times next to Tatoeba + subtitles
  (every 10th held out into `<lang>.phrases_holdout.txt`); then build_dict (bigrams), build_ngrams,
  build_typos as before. WORKERS env (2 for es: with 3 it was killed for memory once).
- **Conversation cache** (predict/Suggester.kt + TypingSession): the last 300 dictionary words typed
  in this app get CACHE_BOOST 0.3 × ln(1+uses) (cleared when the app changes; never fed in
  incognito/password fields, since it's fed from learn()). Swept 0.3/0.6/1.0 with
  tools/predict_bench.py (CACHE_BOOST, CACHE_SIZE env): 0.3 best.
- Tried and not shipped: unknown-previous-word fallback to common words (+0.1 top-3 only).

### Results (tools/predict_bench.py, 4000 held-out lines; top-3)
| | before | after |
|---|---|---|
| en subtitles | 28.8% | 28.9% (cache) |
| es subtitles | 26.0% | 26.1% |
| en texting holdout | 25.7% | 26.1% |
| es texting holdout | 22.9% | 25.9% |
Context check (/tmp script, top-3 after the context): en 1/25 → 19/25 ("turn off" → bluetooth,
"send me a" → screenshot, "he has so much" → rizz, "the AI is" → hallucinating, "spill the" → tea);
es 1/15 → 10/15 ("wepa ya" → llegué, "bota eso en el" → zafacón, "te paso por" → zelle/ath,
"qué es la que" → hay, "le pregunté a" → chatgpt). The first build used round 1 of the phrases
(497 en / 281 es); round 2 (+200 en, +120 es aimed at the misses) is written, rebuild pending.

### Build
Bori build OK with the round-1 dictionaries (user: "built with no errors").

### Safety benchmarks (typo_bench 300/set, log tools/.cache/bench_after.log)
| set | en old | en new | es new |
|---|---|---|---|
| B everyday typos fixed | 86.2% | 86.2% | 84.5% |
| C correct words changed | 0.00% | 0.00% | 0.00% |
| D1 typo lists | 90.3% | 89.7% | 97.3% |
| E fast typing | 73.0% | 73.0% | 67.9% |
| F thumb typing | 78.4% | 77.8% | 77.3% |
| M modern words typed right, changed | 3.9% | 2.3% | 0.7% |
| M modern typos fixed | 69.0% | 73.1% | 79.8% |
es old vs new: B 84.5 = 84.5, C 0% = 0%, D1 97.3 = 97.3, E 67.9 = 67.9, F 79.1 → 77.3 (~3 items),
M typed right changed 3.7 → 0.7%, M typos fixed 72.9 → 79.8%.
glide_bench en (200/set, top-1/top-3) old vs new: G1–G4 identical (89.0/97.5, 86.5/96.0, 73.5/90.5,
67.0/82.0); M (modern words) 42.5/66.5 → 47.5/73.0%.

### Round 2 (rebuilt 16:28–16:47, both languages)
Phrases en 696 / es 399 lines. Subtitles top-3: en 28.9% (same), es 26.1% (same). Texting holdout
(now a different, larger held-out set, so not comparable with round 1): en 24.1% (253 words),
es 28.9% (128 words). Context checks: en 19/25, es 11/15 (some of these contexts are now in the
training sentences). Round-1 files kept in tools/.cache/round1, originals in tools/.cache/baseline.
Note: `nohup sh script &` + `pgrep` can return the short-lived wrapper's pid; wait on the
`sh /tmp/rebuild.sh` pid itself (an earlier "killed" Spanish run was this mix-up).

### Done (was pending)
- Rebuild both languages with round-2 phrases (`WORKERS=2 sh /tmp/rebuild.sh en es`; the script
  is: ngram_count → build_dict → build_ngrams → build_typos → predict_bench ×2).

### Not tested on the phone
Predictions above in real chats; swiping rizz, eSIM, Zelle, wepa; recent names coming back.

### How to catch up
1. This entry. 2. tools/wordlists/README.md. 3. Suggester CACHE_* + noteRecent.

---

## Session 39 — 2026-11-12 (Claude Code): remember emails + usernames (like Gboard / Samsung)

### Why
Session 38 (day + night themes): user "looking good". Then asked for the keyboard to remember the
emails and sign-in names they use, as Gboard and Samsung Keyboard do.

### Decisions
- **Own small store** learn/LoginStore.kt: JSON in `noBackupFilesDir/logins.json` (never backed up;
  allowBackup is false anyway), max 50 entries, loaded once (keyboard and Settings share one
  process). Not in UserStore's SQLite: no DB version bump for 50 rows.
- **Which fields** (ime/EditorPolicy.kt `loginField`): email-type fields, plus one-line text fields
  whose hint/label matches email / correo / username / usuario / log in / sign in / iniciar
  sesión / account name (not "search"). Those hint fields now also get no autocorrect and no
  auto-capitals (usernames shouldn't be "fixed").
- **Learning** (TypingSession.captureField, run on Enter-with-action and when the field closes):
  - An email typed in a sign-in/email field counts 1 use. It shows from the 2nd use (the dictionary's
    rule), so a one-off recipient doesn't stick at once. The same text isn't counted twice within
    10 min (finish() runs on hide and on leaving the field).
  - **Password-next rule:** if the next field in the *same app* within 5 min is a password field,
    the previous field's one-token text (email or username) is saved as confirmed and shows at
    once. That covers sign-in pages with no hint and 2-step sign-ins (email page → password page).
  - Never: password fields, incognito, apps asking for no learning, number/phone fields, the
    Swipe Trainer, digits-only text (codes, phone numbers), text with spaces or > 80 chars.
- **Suggesting** (TypingSession.showLogins, replaces word suggestions in those fields): empty
  field → top 3 saved; typing → saved entries starting with it (any case), best in the centre;
  after "@" → domain endings, the user's own domains first, then gmail/outlook/hotmail/yahoo/icloud
  (shown as "@gmail.com"). A pick replaces the current token (after the last space/comma/
  semicolon, so multi-recipient fields work). Long-press a saved one → "Forget …?". Updates on
  every selection change (posted, coalesced).
- **Settings:** Clipboard & privacy → Privacy group → "Emails & usernames" (status "On · N saved")
  → page (settings/LoginsScreen.kt): switch with ⓘ, Saved list (Forget; "learning" tag until
  shown), Add (email or username by hand, confirmed), Forget all (confirm dialog). Style guide map
  updated.

### Files changed
- learn/LoginStore.kt (new).
- ime/EditorPolicy.kt (`loginField`, LOGIN_HINT), ime/TypingSession.kt (capture, password-next,
  showLogins, pick/long-press, onUpdateSelection refresh).
- settings/LoginsScreen.kt (new), CategoryScreens.kt (PrivacyScreen row + navigate param),
  SettingsActivity.kt (route "logins", Back → privacy), Prefs.kt (`rememberLogins`, default on).

### Build
Not built yet. Brace/paren and nested-comment checks pass; no `LocalContext` inside remember.

### Known gaps
- Apps that autofill the email themselves (password managers) aren't typed, so nothing is learned;
  add by hand in Settings.
- Hint detection depends on the app filling EditorInfo.hintText/label; Chrome pages often don't,
  then only the password-next rule and email-type fields apply.
- Settings → start page "Clipboard & privacy" status line doesn't mention it.

### Not tested on the phone
Gmail/Outlook sign-in (email field shows saved emails, "@" domains), a website sign-in in Chrome
(username before password gets saved, shows at once next time), long-press → Forget, Settings
page add/forget/forget all, incognito hides them, password fields never learn.

### Next step
User: Build + Install; sign in somewhere twice, check the bar. Then 3.9 ambient effects.

### How to catch up
1. This entry. 2. learn/LoginStore.kt. 3. TypingSession `captureField` / `showLogins`.

---

## Session 38 — 2026-11-12 (Claude Code): day + night for the built-in themes, Coquí day, El Yunque

### Why
The user asked to make sure the themes support day and night, including a Coquí day. Only 7 of 24
presets had a `dark` variant (Classic, Material You, Pastel, Mountains, Beach, City Lights,
Borinquen); the other 17 looked the same in both modes, so Dark mode did nothing for them.

### Decisions (user picked each one in a question dialog)
- **Coquí:** one theme, renamed "Coquí Night" → "Coquí", with a day look (leafy greens) and the old
  night colours. The id stays `coqui_night` (file 41-coqui_night.json) so anyone using it keeps it.
- **Fixed themes stay fixed:** Snow, Charcoal, Pure Black, High Contrast Light/Dark (they *are* the
  always-light / always-dark picks).
- **The other 12 got a second version.** Each keeps its fonts, key shape and look; only colours
  change. A theme that was dark gets a new `light` (day), and its old colours become `dark`.
  Dot Matrix (white body), Neon LED (lavender, smaller glow), Gamer RGB (light grey, same rainbow
  outline), Glass (sky blue), Sunset (golden hour), Ocean (turquoise → sand), Aurora (icy mint),
  Galaxy (pastel nebula), Coquí (day); new nights for Mechanical, Candy Glass (plum), Retro
  Typewriter.
- **New drawn scene "El Yunque"** (Coquí uses it in both versions): misty green peaks and ferns,
  sun by day; moon, stars and 26 fireflies by night. Our own drawing, no image files. It's in
  `Backdrop.SCENES`, so it also shows in Background → Scene and in Surprise me.
- **Gallery:** cards of themes with a night version show "☀ ☾" after the name.

### Files changed
- tools/build_themes.py (new variants; DOT_KEYS and RGB constants) → regenerated
  assets/themes 10, 11, 12, 13, 20, 21, 30–34, 41 (only these 12 changed).
- theme/Backdrop.kt: `yunque()` + `fern()` in Scenes, SCENES entry.
- settings/ThemesScreen.kt: ThemeCard name line.

### Build
Not built yet. build_themes.py contrast check: `problems: 0` (every text ≥ 4.5:1, both versions).
Brace/paren balance and the nested-comment check pass.

### Known gaps
- The contrast check can't see the drawn scene; Coquí's day gradient stands in for its sky/peaks,
  and ThemeResolver.readable still fixes text over pictures at run time.
- Long names plus "☀ ☾" may wrap to two lines on narrow cards (e.g. Retro Typewriter).
- The El Yunque drawing has not been seen yet (ferns' size/density may need tuning).

### Not tested on the phone
Settings → Themes → Dark mode Always light / Always dark: 19 previews change, 5 fixed ones don't.
Coquí by day and night (scene, readable keys and suggestion bar). New day looks of Neon LED, Dot
Matrix, Glass, Galaxy; new nights of Mechanical, Candy Glass, Retro Typewriter. Background → Scene
→ El Yunque.

### Next step
User: Build + Install, check the above (and Session 37's calibration layout if not done). Then 3.9
ambient effects.

### How to catch up
1. This entry. 2. tools/build_themes.py (themes 10–41). 3. Backdrop.kt `yunque`.

---

## Session 37 — 2026-11-12 (Claude Code): calibration page, sideways overlap + centred text

### Why
Session 36 (theme 3.8) built OK at 10:34; user: "it looks great". Then they reported two problems on
Fit to my thumbs (pics/Screenshot_20261112_103733_Bori Keyboard.jpg, folded + sideways):
1. Sideways, raising the key height made the practice keyboard cover the text and the slider.
   (The screenshot shows the sentence drawn over "Too tall or too short?…", and the slider and
   the "Now type this…" line on top of the number and q rows.)
2. Unfolded, all the text sat on the left, so typing the sentence was hard to follow.

### Cause
CalibrationScreen drew two independent layers in a Box: the instructions column pinned to the top
and the sentence + keys column pinned to the bottom (BoxScope.align(BottomCenter)). On a short
screen, or with taller keys, they overlapped. All the text was start-aligned across the full width.

### Fix (settings/CalibrationScreen.kt only)
- Typing step is now one Column: compact one-line header (← Back · Fit to my thumbs · posture), an
  instructions area with weight(1f) that shrinks and scrolls, a fixed row "Key height N%" +
  slider (always visible, max 560 dp wide, centred), then the centred sentence and the practice keys.
  Nothing can overlap; the keys keep their true size.
- Thumbs and Done steps: same compact header; text centred (TextAlign.Center, max 560 dp).
- PracticeKeyboard is no longer a BoxScope extension; the sentence is centred above the keys.
- Instruction text merged: "Type the sentence on the keys below…" (the "Too tall or too short?"
  line dropped; the slider row says it).
- The header uses top/side safe-drawing insets (it was safeDrawingPadding on the whole column).

### Build
Not built yet. Brace/paren and nested-comment checks pass.

### Known gaps
At the slider's top end (150%) sideways, the keys plus the slider can be taller than the screen;
then the bottom of the keys is cut off. Calibration itself caps at 130%.

### Not tested on the phone
Sideways folded + unfolded: drag the slider to max, so nothing overlaps; the sentence is centred
over the keys; thumb step text centred; Done page centred.

### Next step
User: Build + Install, redo Fit to my thumbs sideways and unfolded. Then 3.9 ambient effects.

### How to catch up
1. This entry. 2. CalibrationScreen (if step == STEP_TYPE block, CalibrationHeader, CenteredText).

---

## Session 36 — 2026-11-12 (Claude Code): theme 3.8, share + import + Surprise me

### Why
Session 35's fix built OK (10:26) and the user confirmed the bar is back. The user asked to keep
going with themes, so this is phase 3.8 from .bori/theme-plan.md. The settings two-pane layout and
search wait until after the theme phases.

### Decisions
- **File format** (theme/ThemeShare.kt): `.boritheme` = a zip with `theme.json` plus the photo, if
  the background is a photo. A bare theme.json also imports. Shared as `application/zip` so any
  app (Quick Share, Drive, Gmail, chats) takes it.
- **Share = what you see:** the user's Keys & letters picks (look, shape, font, capitals, size,
  see-through, hints) and Background override are baked into both day and night variants. Photos
  come from filesDir/backgrounds (the user's pick) or the theme's own folder. Not baked in: the
  Key borders switch, effects/LED, sounds (they're the viewer's prefs, not part of ThemeSpec).
  Material You tokens stay tokens, so they follow the receiver's wallpaper.
- **Import is defensive:** whole file at most 24 MB, theme.json at most 256 KB, pictures at most
  12 MB each, at most 8 entries. Only plain names (no '/', '\\', '..'), pictures must decode
  (bounds), JSON goes through ThemeStore.decode. It always gets a new `my_<time>` id in Mine
  (never overwrites anything); name and author are capped at 40 characters. A photo background
  whose picture is missing is dropped. Then it becomes the keyboard's theme.
- **Ways in:** Theme → "Add a shared theme" (system file picker, any type, since shared files
  often come as octet-stream; no storage permission). Bori is also a share-sheet target (SEND,
  application/zip + octet-stream; no VIEW filter, so Bori doesn't take over opening every zip).
  SettingsActivity.importShared runs the import on a thread, shows a Toast, then opens the gallery.
- **Surprise me** (theme/SurpriseTheme.kt): random light/dark, base hue and accent hue (same,
  ±30°, 180°, ±120°). Plain 40%, 2-stop gradient 40%, drawn scene 20%. Random look, shape, font,
  radius, gaps, sometimes an outline in the accent colour (+ glow on dark). Text is moved toward
  black/white until ≥ 4.5:1 on both its key and the background (outline keys show the
  background), hints ≥ 3:1, strip text ≥ 4.5:1 on every gradient stop, the Enter label white or
  near-black with the Enter key darkened/lightened until 4.5:1. Scenes rely on
  ThemeResolver.readable. Fun two-word names ("Mango Storm", "Coquí Glow"); author "Surprise me".
- **Surprise UX** (Theme hub, first group): each tap saves into Mine and applies it. Tapping
  again while the last surprise is still the theme replaces it (Mine doesn't fill up with
  tries). "Undo surprise · Back to X" deletes it and restores the theme from before the first
  tap. Leaving the page keeps the current surprise.
- **Rows** (style guide): Theme hub gets "Surprise me" (+ Undo when it applies) and a new
  "Share" group: "Share <name>" and "Add a shared theme". The editor's Theme group gets "Share".
  Errors and results show as a Footnote line.

### Files changed
- theme/: ThemeShare.kt (new), SurpriseTheme.kt (new), ThemeStore.kt (`dirOf`).
- settings/: ThemeShareUi.kt (new: shareTheme, importThemeNow, rememberThemeImporter,
  importMessage, dropTheme), ThemesScreen.kt (hub rows; `selected` is now state),
  ThemeEditorScreen.kt (Share row), SettingsActivity.kt (importShared on SEND).
- AndroidManifest.xml (SEND filter on SettingsActivity), res/xml/file_paths.xml (cache
  shared_themes/).

### Build
Bori build OK 2026-11-12 10:34 (no errors). Share/import + Surprise me not yet confirmed on the phone.

### Known gaps
- The editor's Duplicate copies theme.json only, not a photo in the theme's folder (rare: the
  editor doesn't set photos yet).
- The Undo surprise row goes away if you leave the Theme page.

### Not tested on the phone
Share → Quick Share/Drive/Gmail → open on the other side via "Add a shared theme" or the share
sheet → same look, photo included. Surprise me ×10 (all readable? any ugly fonts?), Undo,
replace-on-retap. A damaged/random zip shows a clear message. Share from the editor.

### Next step
User: Build + Install, try the above. Then 3.9 ambient background effects (rain, snow, leaves…).

### How to catch up
1. theme/ThemeShare.kt. 2. theme/SurpriseTheme.kt. 3. ThemesScreen hub (Surprise + Share group).

---

## Session 35 — 2026-11-12 (Claude Code): suggestion bar (and 📋) invisible on solid themes

### Why
User: "the clipboard has completely been gone" (pics/Screenshot_20261112_075925.jpg). The whole
suggestion bar was blank: no 📋, no ⚙, no dividers, just the theme colour above the number row.

### Cause
Session 30 (press effects) set `clipChildren = false` on the key frame, the column and the root in
view/InputRootView.kt so effects can spill over the bar. KeyboardView.onDraw began with
`canvas.drawColor(theme.solidBackground)`; drawColor fills the whole clip, and with child clipping
off that is the whole input view, so the keyboard (drawn after the bar) painted over it. Only on
solid-colour themes: gradient/scene/photo themes make solidBackground transparent, which is why
it wasn't seen earlier. The bar still took taps; it just wasn't visible.

### Fix
view/KeyboardView.kt, view/SuggestionStripView.kt, view/SearchBarView.kt: fill only the view's own
rectangle (`drawRect(0, 0, width, height, backgroundPaint)`, skipped when transparent; the paint
is a field, so there are no allocations in onDraw). The bar and search bar weren't affected (their
FrameLayout clips), but they're changed too so the trap can't return. `clipChildren = false` is
kept. Rule for later: no `canvas.drawColor` in views inside InputRootView.

### Build
Bori build OK 2026-11-12 10:26. User: "it is back!" (pics/Screenshot_20261112_102728: bar shows ⚙, suggestions, 📋 in the theme's colours).

### Not tested on the phone
Solid theme: ⚙ + 📋 visible, 📋 opens the clipboard, ⌨ returns, suggestions, the copied chip.
Gradient/photo theme still fine; press effects still spill over the bar.

### Next step
User: Build + Install, check the bar. Then theme 3.8 (share .boritheme + import + Surprise me), as
agreed in this session's plan. The settings two-pane layout + search wait until after the theme phases.

### How to catch up
1. This entry. 2. KeyboardView.onDraw (first lines). 3. .bori/theme-plan.md 3.8.

---

## Session 34 — 2026-11-12 (Claude Code): settings, every page below the start page (built OK 20:49)

### Why
Session 33 built; user: "It looks great so far … but the settings within those categories have
gotten too overwhelming and too long and listy. Apply the same thing we did to the top layer to
the secondary layer and every layer thereafter."

### Decisions (same guide: short pages, status on the second line, details one level down)
- Theme (ThemesScreen) is now a hub: preview; Gallery (theme name) → ThemeGalleryScreen
  (Gallery/Mine grid); Dark mode (ChoiceRow dialog); Key borders (switch); "Customize": Keys &
  letters → KeysLettersScreen (Style, Shape, See-through | Font, Capitals, Size, Hints | Use the
  theme's style), Background → BackgroundScreen (one "Background" choice; only the chosen type's
  group shows: gradient swatches + direction, scene, or photo controls; then Adjust: brightness,
  strength), Effects & lighting → EffectsScreen (Effect + colour; Lighting + lights up + colour +
  speed + brightness, each only when it applies), Design your own / Edit. Status lines from
  keysStatus / backgroundStatus / effectsStatus. currentPreviewTheme() gives pages a live preview.
- Typing: 3 switches + "Sound & vibration" → SoundVibrationScreen.
- Corrections: Autocorrect (with "Backspace undoes a fix" in its status); Suggestions (bar,
  next-word, tapped words, respect apps ⓘ); Learning (Your dictionary, Touch heat map). "Block
  offensive words" moved into Your dictionary's "Blocked" group.
- Swipe & gestures: Glide typing → Swipe trail (status: On · colour · width) → TrailScreen;
  Learn my swipe style; "Practice" group (Trainer, Test Lab) only while glide is on; Gestures.
- Layout & size: Fit to my thumbs; Key height (this posture); Other ways of holding →
  HeightsScreen (all four); Number row; Split keyboard (status) → SplitScreen (switch + spacing).
- Emoji & GIFs: Emoji style + preview; GIF search (status) → GifSearchScreen (SCREEN_GIFS, so the
  keyboard's GIF panel still links straight to it).
- Your dictionary: groups; word lists show 5 with "Show all N"; "Forget everything learned" asks
  in a dialog.
- Touch heat map: switches (learn ⓘ, move keys to fit my aim, disabled while learning is off),
  Fit to my thumbs; "Your taps" group with a posture ChoiceRow and the map; Copy/Clear rows.
- Theme editor: tapping the preview opens the colour picker in a dialog (no 6 sliders on the
  page); Colours group (Part, Colour swatch row, Gradient, Night colours, Editing day/night);
  Keys group (Style, Shape, Font, "More key options" reveals the 5 sliders); Theme group (Undo
  with change count, Name via dialog, Duplicate, Delete).
- Swipe Trainer and Test Lab use SettingsPage without the try bar.
- New routes + parentOf: sound, trail, heights, split, theme_gallery/keys/background/effects;
  gifs → emoji.

### Files changed
settings/: ThemesScreen.kt (hub, gallery, keys pages), BackgroundSettings.kt (BackgroundScreen),
EffectsSettings.kt (EffectsScreen), CategoryScreens.kt (Typing/Corrections/Gestures/Layout/Emoji
rewritten + SoundVibration/Trail/Heights/Split/GifSearch pages), SettingsActivity.kt (routes,
parentOf, Dictionary, HeightSlider, trailColorLabel), TouchMapScreen.kt, ThemeEditorScreen.kt,
SwipeTrainer.kt, SwipeTestLab.kt.

### Build
First Bori build failed: CategoryScreens.kt:257 and :270 (HeightsScreen, SplitScreen) called
`LocalContext.current` inside `remember { }` ("@Composable invocations can only happen from the
context of a @Composable function"). Fixed by reading the context first (`val context =
LocalContext.current`, then `remember { Prefs(context) }`); no other `remember { … Local….current }`
left in the code. Rebuilt: Bori build OK at 20:49.

### Result
User: "So far, the settings look amazing … love it so far", and asked to write it all into the
reference notes. Added **.bori/settings-style.md**: the rules (lists not cards, status on the
second line, short pages with details one level down at every level, small groups, label
wording, dependencies, which row type for what, live preview + pinned try bar, 640 dp width,
icons), the SettingsUi building blocks, Compose mistakes from Bori builds, and the page map.
Handoff and plan point to it. Claude Code's own memory also records the style and the
remember/LocalContext rule.

### Not tested on the phone
All pages. Check each page fits on about one screen; Back goes up one level everywhere (including
from the keyboard's GIF link: gifs → Emoji & GIFs); theme previews update after each change.

### Next step
User: Build + Install. Then: two-pane list-detail when unfolded and settings search (rest of the
redesign plan), then P6 (3.8 share .boritheme + Surprise me).

### How to catch up
1. ThemesScreen hub + status helpers. 2. CategoryScreens.kt. 3. ThemeEditorScreen picker dialog.

---

## Session 33 — 2026-11-12 (Claude Code): settings redesign, build 1 of 2

### Why
Theme editor (Session 32) built OK at 19:51. The user sent pics/Screenshot_20261111_195559 (the
start page: 8 big cards with feature-list descriptions) and asked to make settings concise and
sensible, researching settings UI design first. Plan approved: two builds.

### Research used
Android settings design guide (developer.android.com/design/ui/mobile/guides/patterns/settings):
lists not cards; the second line shows the current state; small groups with headings and
containment; subscreens for 15 or more settings; the label of a row = the title of the page it
opens; short impersonal labels with no "Don't"/"Set"/"Manage"; longer explanations on a second
screen; dependent settings under their parent; switch / radio dialog / slider patterns;
list-detail on big screens with a max width. Toptal/ScreensDesign/Eleken: about 4–7 top-level
groups by user goal, show values up front, search when long.

### Decisions
- settings/SettingsUi.kt: SettingsPage (back arrow + title, content max 640 dp wide, pinned
  "Try the keyboard here" bar at the bottom, above the keyboard thanks to imePadding), Group
  (primary label + surfaceContainer rounded 20 dp), RowDivider, NavRow (chevron), SwitchRow
  (whole row toggles, optional ⓘ), ChoiceRow (radio dialog, closes on pick), SliderRow,
  RowContent, InfoButton, Footnote.
- settings/SettingsIcons.kt: ImageVectors from path data (addPathNodes) instead of
  material-icons-extended (too big to compile on the phone). Material Icons paths (Apache-2.0,
  ATTRIBUTION.md) for back, chevron, info, palette, keyboard, height, spellcheck, lock, build,
  search; our own outline icons for swipe and emoji. A bad path gives an empty icon, not a crash.
- Start page: setup steps / split / calibration cards until done, then one group of 7 rows with
  icons and status lines (theme name · mode; height · number row · split; sound · vibration;
  autocorrect level; glide on/off; emoji style · GIFs; clipboard keep time · incognito), plus a
  separate "Help improve Bori" row. The intro line shows only during setup.
- Moves: Emoji style → "Emoji & GIFs" (GIF settings merged in; SCREEN_GIFS from the keyboard
  opens it); clipboard → "Clipboard & privacy"; Themes is no longer a row inside Appearance;
  "Appearance & layout" → "Layout & size"; "Corrections & suggestions" → "Corrections".
- Dependencies: next-word disabled while the suggestion bar is off; trail / learn style /
  Trainer / Test Lab only while glide typing is on; trail colours only while the trail is on;
  vibration strength under Vibration; sound type + volume under Sound (volume hidden for
  "Phone's own"); split spacing only while split is on; GIF key / safe search only while GIF
  search is on; clipboard keep time only while history is on.
- Wording: e.g. "Incognito (don't learn new words)" → "Incognito" + status; "Follow apps that
  turn suggestions off" → "Respect apps' no-suggestion request" with the long text behind ⓘ;
  the GIF privacy text and the clipboard explanation are behind ⓘ too.
- Sound packs: the chip row + try buttons became a "Sound type" radio dialog (picking plays a
  sample) + volume slider.
- Key height: the current posture's slider, with the other three behind "Other ways of holding
  the phone".
- CategoryScreen is kept as a wrapper around SettingsPage, so the Themes, Touch heat map and
  editor pages get the new top bar and try bar now; their insides are restyled in build 2.
  CalibrationScreen untouched.
- Files: settings/CategoryScreens.kt (new: the seven category pages + EmojiPreviewRow,
  AUTOCORRECT_CHOICES, CLIP_KEEP_CHOICES); settings/SettingsActivity.kt (656 lines, was 975:
  activity, routes, start page, setup cards, shared helpers, Dictionary, logs, height, split).
  Old copy kept at /tmp/bori/SettingsActivity.old.kt during the session only.

### Build
Not built yet. Brace/paren/nested-comment check 0 on all four files.

### Not tested on the phone
All of it. Check every old setting is still reachable: typing (caps, period, preview, vibration +
strength, sound + type + volume), corrections (level, bar, next-word, tap-fix, offensive, follow,
dictionary, heat map), gestures (glide, trail + colours, learn, trainer, lab, cursor, delete,
flicks), layout (fit, height ×4, number row, split + spacing), emoji style, GIFs (switch, key,
link, forget, safe search), privacy (incognito, history, keep, chip), help (typo log,
diagnostics), themes (unchanged inside).

### Next step
User: Build + Install. Then settings build 2: Theme hub (customise rows → subpages), Touch heat
map + editor restyle, two-pane list-detail when unfolded (≥ 600 dp), search. Then P6 (3.8).

### How to catch up
1. settings/SettingsUi.kt. 2. SettingsActivity.SettingsScreen + overviewStatus. 3. CategoryScreens.kt.

---

## Session 32 — 2026-11-12 (Claude Code): P5 = theme 3.7 design-your-own editor

### Why
Session 31 (sound packs) built with no errors. Next phase: 3.7.

### Decisions
- Editor works only on user themes (filesDir/themes/<id>/theme.json via ThemeStore). Built-ins
  are copied first: Themes shows "Make your own from “X”" for a built-in (makeOwnTheme: id
  my_<time>, name "X (mine)", author Me, category Mine) and "Edit “X”" for your own.
- Route: Settings screen string "edit:<id>" (EDIT_PREFIX), handled in SettingsActivity's `else`
  branch; parentOf → "themes". Survives a fold (STATE_SCREEN from Session 27).
- Parts: 14 (background top/bottom, letter keys, letters, function keys/labels, Enter key/label,
  pressed key, suggestions, hints, pop-up bg/letter, outline). Tap the preview: strip →
  suggestions; row maths like KeyboardPreview (strip 16%, 4 rows × 10 units); tapping the same
  key again switches key ↔ its label. Chips list every part too.
- Colours are written as #AARRGGBB (Material You tokens in a copied theme become today's
  colour when that part is edited; untouched parts keep their tokens).
- Picker: hue (rainbow strip above), richness, brightness, see-through, hex field (6 or 8
  digits), 14 quick colours. Drag steps update the preview only ("draft"); letting go commits:
  push the pre-drag spec on the undo stack (max 60), save, prefs.themeId = this theme,
  themeRevision++ so the open keyboard re-themes.
- Night colours: switch copies the day variant into `dark`; Day/Night chips choose which one
  is painted. Key style edits go to both variants.
- Key style: look, shape, font chips; sliders roundness 0–30, outline 0–4, glow 0–12,
  see-through 0–90%, spacing (gapV = gapH + 3).
- Contrast warnings (red, < 4.5:1) for letters, function labels, Enter label, suggestions,
  pop-up, using ThemeResolver.contrast with see-through colours blended over the background.
- Preview resolves the theme without the user's overrides, so it shows the theme itself; a
  note says Keys & letters / Background picks still apply on top.

### Files changed
- settings/ThemeEditorScreen.kt (new).
- settings/ThemesScreen.kt: navigate param, Make your own / Edit button, Mine note.
- settings/SettingsActivity.kt: route + parentOf.

### Build
Not built yet. Brace/paren/nested-comment check 0.

### Not tested on the phone
All of it. Watch: preview tap mapping, slider smoothness (each drag step re-resolves the theme),
the gradient switch state when switching Day/Night, Material You copies.

### Next step
User: Build + Install, design a theme. Then P6 = theme 3.8 share (.boritheme) + Surprise me.

### How to catch up
1. ThemeEditorScreen commit/draft. 2. Part.read/write. 3. ColorPicker.

---

## Session 31 — 2026-11-12 (Claude Code): P4 = theme 3.6 sound packs

### Why
Session 30 (effects + LED) built with no errors (user: "built no errors"). Next phase: 3.6.

### Decisions
- No numpy/ffmpeg/sox here, so tools/build_sounds.py synthesizes everything in pure Python
  (noise + one-pole and RBJ band-pass filters + swept sines + additive piano) into 16-bit mono
  22.05 kHz WAVs. ~0.4 s to run, 350 KB total. Loudness evened out: scaled to RMS 0.17 unless that
  would clip (so the sharp click packs stay a bit quieter). Our own sounds, no recordings
  (ATTRIBUTION.md "Key sounds").
- Packs: crisp, typewriter (enter = carriage noise + bell), thocky, clacky, creamy, bubble,
  water, wood, 8bit, each with key/space/enter/delete; piano = C4/C5/C6 notes.
- ime/SoundPacks.kt: one SoundPool (6 streams, sonification attributes), loaded lazily per pack;
  assets are copied to filesDir/sounds first because SoundPool needs a file and Bori's packager
  might compress assets (AssetFileDescriptor would then fail). Copies are refreshed when the
  APK's lastUpdateTime changes (versionCode stays 1 between Bori builds).
- Letters get a random 0.96–1.04 pitch so fast typing doesn't repeat exactly.
- Piano: each letter → a major pentatonic note (bottom row low, rows rise, left to right up);
  played from the nearest of C4/C5/C6 with the SoundPool rate (at most ±6 semitones). Enter =
  C major chord, space = C4, delete = G3.
- Follows ringer mode (silent/vibrate → no pack sounds; returns "handled" so the phone click
  doesn't play either). Feedback.quiet = password field (Stage 3 rule; vibration stays). While a
  pack is still loading, that press uses the phone's click.
- "Phone's own" (system) stays the default, so nothing changes until a pack is picked.
- UI: Settings → Typing → under "Sound on key press": pack chips (tapping plays a sample),
  volume slider, try buttons a / j / p / Space / Delete / Enter. The settings Feedback releases
  its SoundPool on leaving the screen.

### Files changed
- tools/build_sounds.py (new), assets/sounds/<10 packs>/*.wav (new).
- ime/SoundPacks.kt (new); ime/Feedback.kt (playSound, quiet, release); ime/BoriImeService.kt
  (quiet per field, release on destroy).
- settings/Prefs.kt (soundPack, soundVolume); settings/SettingsActivity.kt (SoundPackSettings).
- ATTRIBUTION.md.

### Build
Not built yet. Brace/paren/nested-comment check 0.

### Not tested on the phone
All of it. Listen for: clicks at sound ends, 8-bit/piano loudness, latency of the first press
after picking a pack, typewriter bell length.

### Next step
User: Build + Install, try the packs. Then P5 = theme 3.7 design-your-own editor.

### How to catch up
1. tools/build_sounds.py PACKS. 2. ime/SoundPacks.kt play/playPiano. 3. Feedback.playSound.

---

## Session 30 — 2026-11-12 (Claude Code): P3 = theme 3.5 press effects + LED lighting

### Why
Session 29 (backgrounds) built OK at 19:32; user: "I do see the options now, let's build the
next phase".

### Decisions
- Press effects: view/EffectsView.kt, a transparent view inside InputRootView's KeysFrame (so it
  is measured exactly at the keyboard's size and shares its coordinates; clipChildren off on the
  frame, column and root so bubbles/hearts can rise over the strip). Same onMeasure guard as
  KeyOverlayView. Fixed pool of 192 particles in parallel arrays, no allocations while
  animating; redraws with postInvalidateOnAnimation only while particles live. Effects: ripple
  (2 rings), pop, sparkle (rotating crosses), bubbles, hearts (one unit Path, scaled), confetti
  (gravity + spin), fireworks (radial + gravity), emoji burst (8 emoji as text), light wave (big
  ring), finger glow (3 soft discs). Colour: theme accent, rainbow (random hue each particle) or
  a fixed colour. KeyboardView.press() calls burst() on touch-down (not on slides).
- LED lighting: view/LedLights.kt computes a colour per key while KeyboardView draws; its alpha
  = brightness × strength. Modes: rainbow wave (hue by x + time), breathing (sine), light up on
  press (per-key fade, 700 ms / speed), colour cycle, row rainbow (static). Targets: keys (fill
  mixed with the light; see-through keys get at least the light's alpha), letters, outlines
  (KeyPainter.outline() strokes the shape it just drew, with its radius). Animated modes redraw
  every 33 ms (≈30 fps) only while isShown; static modes and idle reactive don't redraw. A lit
  key is passed to the painter as "accent" so it shows even with Key borders off.
- "Apply effects only" (theme-plan): effects and LED are prefs on top of any theme, like Keys &
  letters and Background.
- Off in password fields and when Settings.Global.ANIMATOR_DURATION_SCALE is 0 (Remove
  animations). applyEffects runs at onStartInputView, on any EFFECT_KEYS change and after a
  theme change (theme-colour effects follow the accent).

### Files changed
- view/EffectsView.kt, view/LedLights.kt (new).
- view/KeyboardView.kt: effects, led, LED drawing in onDraw (rows loop with row index), burst +
  led.onPress in press(), clear on detach, currentTheme(), LED_FRAME_MS.
- view/InputRootView.kt: effects view, clipChildren, cleared when a panel opens.
- theme/KeyPainter.kt: lastRad + outline().
- settings/EffectsSettings.kt (new), settings/ThemesScreen.kt ("Effects & lighting" card),
  settings/Prefs.kt (pressEffect, effectColor, ledMode/Target/Color/Speed/Brightness, EFFECT_KEYS).
- ime/BoriImeService.kt: applyEffects.

### Build
Not built yet. Brace/paren/nested-comment check 0 on all changed files.

### Not tested on the phone
All of it. Watch: smoothness of the rainbow wave on the unfolded screen (every key redrawn at
30 fps), effects drawn over the strip, emoji glyph rendering speed, battery.

### Next step
User: Build + Install, try effects and LED modes. Then P4 = theme 3.6 sound packs.

### How to catch up
1. view/EffectsView.kt burst/onDraw. 2. view/LedLights.kt colorFor. 3. KeyboardView.onDraw.

---

## Session 29 — 2026-11-12 (Claude Code): P2 = theme 3.4 backgrounds

### Why
Session 28 (calibration too tall) built; user: "better, let's keep going". Next phase in the
build order: theme 3.4.

### Decisions
- A background is a `BackgroundSpec` (theme/Backdrop.kt): type none/gradient/scene/photo,
  gradient colours + angle, scene id, photo file + zoom/focusX/focusY, brightness, blur,
  opacity ("picture strength" over the theme's background colour; this is the "background
  opacity" moved here from 3.3). Themes carry it as `ThemeVariant.backdrop` (so light and dark
  can differ); the user's choice is one JSON pref `background_override` (Prefs.backgroundOverride,
  in THEME_KEYS) that goes into ThemeOverrides.backdrop and beats the theme's. Type "none" =
  plain colour (also drops the theme's backgroundGradient).
- ThemeResolver turns it into `Backdrop` (KeyboardTheme.backdrop; colours as List<Int> so the
  data class equality works as a cache key). Photo files: the override's in filesDir/backgrounds,
  a user theme's next to its theme.json (for the editor/sharing later). File stamp in the key, so
  a replaced photo isn't served from cache.
- BackdropRenderer draws into one bitmap per (backdrop, size), LRU of 5, main thread, never
  recycles (a view may still draw it). InputRootView uses BackdropDrawable; KeyboardPreview
  draws the same bitmap, so previews match. solidBackground is transparent when there's a
  backdrop, so strip/keys/panels show it (same as gradients).
- Photo: decoded with inSampleSize to about the drawn size, centre-crop (cover) × zoom, focus
  picks the visible part. Brightness = ColorMatrix (darker: scale; lighter: towards white);
  non-photos get a black/white wash. Blur = draw small (1 + 15 × blur times smaller) then scale
  up with filtering: works on Android 8+, no RenderEffect needed.
- Import: system photo picker (PickVisualMedia, no permission; falls back to the document picker
  on old phones). ImageDecoder on 9+ (EXIF upright, scaled while decoding, software bitmap);
  BitmapFactory on 8 (may ignore EXIF rotation). Max 2048 px, JPEG 90, photo_<time>.jpg, older
  photos deleted.
- Auto text colour: ThemeResolver.readable() gets the backdrop's average colour (48×24 render)
  and switches stripText / text / textFunction (4.5:1) and hint (3:1) to #111111 or white if the
  theme's colour is too faint against the picture (blended under see-through keys).
- Scenes are our own drawings (no assets): mountains (3 ridges, sun / moon + stars), beach (sea,
  foam line, sand), skyline (two layers of buildings, lit windows at night), dot grid. Seeded
  Random so they look the same every time.
- Gradient choices: 12 ready-made (incl. celeste/white/red for PR) × 4 directions; custom colours
  come with the editor (3.7).
- Presets: 35 Mountains, 36 Beach, 37 City Lights (Scenes), light + dark, semi-opaque keys.
  build_themes.py: V(..., backdrop), SCENE(); the contrast check uses a gradient of the scene's
  main colours (sky first: the strip sits on the sky). 0 problems.

### Files changed
- theme/Backdrop.kt (new): BackgroundSpec, Backdrop, BackdropRenderer, BackdropDrawable, Scenes.
- theme/ThemeSpec.kt (ThemeVariant.backdrop), theme/KeyStyles.kt (ThemeOverrides.backdrop + key),
  theme/KeyboardTheme.kt (backdrop, solidBackground), theme/ThemeResolver.kt (backdrop + readable +
  contrast()).
- view/InputRootView.kt: BackdropDrawable.
- settings/BackgroundSettings.kt (new): Background card UI + BackgroundPhotos import.
- settings/ThemesScreen.kt: "Background" fold-out card; KeyboardPreview draws backdrops.
- settings/Prefs.kt: backgroundOverride.
- tools/build_themes.py + assets/themes/35-37 (new).

### Build
Not built yet. Brace/paren/nested-comment check 0 on every changed file. Risky spots:
ImageDecoder lambda, Drawable.getOpacity deprecation override, rememberLauncherForActivityResult
+ PickVisualMediaRequest (activity 1.9.3), early `return` in a composable.

### Not tested on the phone
All of it. Watch: memory with a big unfolded keyboard bitmap (~7 MB each, 5 cached), photo
decode time when opening the keyboard, Android 8 photo rotation, how the soft blur looks.

### Next step
User: Build + Install and try backgrounds. Then P3 = theme 3.5 press effects + LED lighting.

### How to catch up
1. theme/Backdrop.kt. 2. ThemeResolver.resolve (end) + readable. 3. settings/BackgroundSettings.kt.

---

## Session 28 — 2026-11-12 (Claude Code): calibration made the keyboard too tall

### Why
User ran P1 (Session 27 built and installed): "it's too tall now". Screenshot
pics/Screenshot_20261111_192157 (cover screen, upright, number row on): the keyboard covers
~58% of the screen, about 10% of the screen height per row.

### Cause
1. capture() assumed resting thumbs sit on the middle letter row (2.5 rows below them).
   Resting thumbs sit higher, so rows came out huge.
2. The row cap in KeyboardView.keyHeightPx allowed 13% of the screen per row (~2/3 of the
   screen with 5 rows), and calibration could pick up to 150%.

### Fix
- view/BigScreen.kt: new `KeySize` (baseDp / maxDp / rowDp), used by KeyboardView and the
  calibration page so they always agree. Cap is now max(base × 1.15, 8% of screen height) per row
  (5 rows ≈ 40% of the screen). This also caps heights already saved on the phone.
- settings/CalibrationScreen.kt: thumbs = line between the top and middle letter rows
  (THUMB_ROWS_BELOW = 3); calibration picks at most 130% (CAL_MAX_SCALE); a height slider on
  the practice step resizes the practice keys live and saves for this posture.

### Build
Not built yet. Brace/paren/comment check 0.

### Next step
User: Build + Install, redo Fit to my thumbs (folded), adjust with the new slider if needed.
Then P2 (theme 3.4 backgrounds).

---

## Session 27 — 2026-11-12 (Claude Code): P1 thumb calibration + "Move keys to fit my aim"

### Why
User asked to build all remaining phases; agreed plan: one phase per Bori build, calibration
first, then themes 3.4–3.10, autocorrect tidy-up, Stage 4, Stage 5 (plan.md "Build order").
Sessions 25 + 26 built OK at 19:12 (user: "done no errors"; handoff Last Bori build).

### Decisions
- Calibration is a full-screen Compose page (no CategoryScreen: it needs the whole screen and
  raw multi-touch). Route `SettingsActivity.SCREEN_CALIBRATE` ("calibrate"), parent "layout".
- Thumbs step: 1–2 pointers held still (12 dp) for 800 ms. Height: the thumbs' average y
  should be the middle of the middle letter row, so row = (keyboard bottom − 4 dp − thumb y) /
  2.5 (half the asdf row + zxcv row + bottom row; true with and without the number row).
  Keyboard bottom = window bottom − navigationBars inset (the IME's nav bar may be a bit
  taller; the user sees the result in step 2 and can fine-tune with the slider). Converted to a
  scale against KeyboardView's base row, capped like keyHeightPx, clamped 0.6–1.5, saved for
  the current posture.
- Split (big screen, split on, two thumbs on opposite halves): each half centred under its
  thumb. With reach = average thumb distance from its own side: edge/2 + (W − gapW)/4 = reach.
  Solve the edge with the current gap, clamp 0–0.2, then re-solve the gap, clamp 0.1–0.5.
  Split prefs are not per posture (only unfolded splits anyway).
- Practice step: KeyGeometry built from Layouts.build(ALPHA, device language, numberRow pref)
  with the same padding/gaps/split as KeyboardView.rebuildGeometry, so keys sit where the real
  ones will. Each tap down → TouchMap.recordPoint(posture, intended key, dx, dy) relative to the
  intended key (relative aim is valid even if the whole mock is a few dp off the real keyboard).
  Pangram EN "the quick brown fox…", ES "el veloz murcielago hindu…" (accents dropped).
  Spaces just advance. Mistakes aren't fixed: they're the data.
- Folding recreates SettingsActivity: it now saves the current page in onSaveInstanceState, so
  calibration reopens on the new posture at step 1 (state per posture is reset on purpose).
- Prefs.calibrated (set of posture ids) + calibrationAsked: onboarding card (after steps 1–2,
  until done or "Not now") and a one-time sticky strip prompt (after the big-screen question).
- "Move keys to fit my aim" (Prefs.aimKeys, default on, needs touchMap on): KeyboardView.aimedKeyAt
  is used on touch down and in move(). If the touch is on a letter, every letter whose face is
  within 0.3 key of the touch competes and the closest learned aim point (centre + shift) wins.
  Shifts come from TouchMap.shifts(posture), cached by map version, so no allocation per touch.
  Keys with < 15 taps have no shift (plain centre). Using it in move() keeps hysteresis
  consistent (otherwise an aimed key would flip back on the first wobble).
- TouchMap: shifts(posture) cache, applyTo() uses it, recordPoint().

### Files changed
- settings/CalibrationScreen.kt (new).
- settings/SettingsActivity.kt: route, STATE_SCREEN save/restore, CalibrationStep card,
  "Fit to my thumbs" button above the height sliders, KeyboardHeightSettings(navigate).
- settings/TouchMapScreen.kt: aim switch, Fit button, HeatMap internal, navigate param.
- settings/Prefs.kt: aimKeys, calibrated, calibrationAsked; TOUCH_MAP/AIM_KEYS public.
- learn/TouchMap.kt: shifts(), recordPoint().
- view/KeyboardView.kt: aimShifts, aimedKeyAt, AIM_ZONE.
- ime/BoriImeService.kt: applyAim (start + pref changes), askCalibration.

### Build
Not built yet. Brace/paren/nested-comment script: all 0. Risky spots: BoxScope extension
composable (PracticeKeyboard), awaitPointerEventScope loops, `pointerInput` capturing
delegated state vars, KeyboardTheme.forContext named args.

### Not tested on the phone
All of it. Check: thumbs circles appear, height changes after 0.8 s still; split halves move on
the unfolded screen; practice keys look like the real keyboard; folding keeps the page;
✓ list fills in; strip prompt shows once; aim switch has no effect until 15 taps per key.

### Next step
User: Build + Install, try calibration folded + unfolded. Then P2 = theme 3.4 backgrounds.

### How to catch up
1. settings/CalibrationScreen.kt capture() + PracticeKeyboard. 2. KeyboardView.aimedKeyAt.
3. TouchMap.shifts. 4. plan.md "Build order".

---

## Session 26 — 2026-11-12 (Claude Code): touch heat map

### Why
User approved the Session 25 proposal ("approved let's go"): heat map first, then calibration.
Sessions 25 and 26 are both unbuilt.

### Decisions
- learn/TouchMap.kt, a singleton (IME and Settings share the process, no android:process),
  stored as filesDir/touch_map.json (written atomically via .tmp, every 25 new taps and on
  finishInput if anything changed). Per Posture, per key: n, sums for mean + spread, and the last
  60 points for the picture. Points are offsets from the key centre in key units, like
  TypoRecorder/SwipeAdaptation, so key height and split changes don't spoil them.
- Forgetting: past 500 taps per key the sums are scaled down, so a new grip/case takes over.
- What counts (TypingSession.recordTouches): only taps whose intended letter is known.
  * Word committed as typed and valid (typedValid) or a confirmed undo-keep: typed letters.
  * Autocorrect fix with the same length and ≤2 changed letters: the fix's letters, but held
    in `pendingTouches` until the next commit/finish and dropped if backspace undoes the fix.
  * Strip pick with the same length and ≤2 changes: recorded at once (the clearest signal).
  * Never: glides (they have SwipeAdaptation), the Swipe Trainer field, incognito/no-learning
    fields, typos committed unfixed, inserts/deletes (taps can't be lined up with letters).
  * Accented letters count on their base key (á → a) unless they have their own key (ñ).
  * Taps further than 1.5 keys from the meant key are ignored as noise.
- Use: KeyProximity.distance() now measures to centre + learned shift (shift = sum/(n+20),
  clamped ±0.4 key, only keys with ≥15 taps). centerX/centerY stay the real centres, so
  TypoRecorder and SwipeAdaptation are unchanged. Shifts are re-applied when the map
  version, geometry or posture changes (TypingSession.proximity()).
- Not done yet: moving the real key hit boxes (which key a tap types). That's the bigger win
  but riskier; better done after seeing real data (Copy data) and with calibration.
- Pref `touch_map` (default on, like other learning): "Learn from where I tap".

### Files changed
- learn/TouchMap.kt (new).
- predict/KeyProximity.kt: setShifts(), distance() uses them.
- ime/TypingSession.kt: touchMap, pendingTouches, recordTouches/flushTouches, hooks in
  commitComposing, onSuggestionPicked, backspace undo, finish(); proximity() applies shifts.
- settings/TouchMapScreen.kt (new): switch, posture chips with tap counts (current first
  labelled "Now:"), Canvas QWERTY with red dots on target / orange outside the key, ring =
  average aim, Copy data / Clear this one / Clear all. Redraws every 0.7 s if the map changed
  (typing in the test box).
- settings/SettingsActivity.kt: "touchmap" route (parent corrections), button under
  Corrections → "Your aim".
- settings/Prefs.kt: touchMap.

### Build
Not built yet. Checked by reading and a brace/paren + nested-comment script (all 0). Risky
spots: `for (k in ko.keys())` over a Java Iterator (Kotlin's Iterator.iterator() covers it),
`continue` inside the map index in TouchMap.load, Compose drawRoundRect/drawCircle overloads.

### Not tested on the phone
All of it. Check: dots appear while typing in the test box; folded vs unfolded go to different
chips; an undone autocorrect adds no dots; incognito adds none; the file survives a restart.

### Next step
User: Build + Install (25 + 26). Type normally for a day, then Copy data and paste it so the
shifts and the 15-tap/±0.4 limits can be checked. Then the calibration screen (plan.md
Fit-to-thumbs), then maybe moving key hit boxes from the map.

### How to catch up
1. learn/TouchMap.kt. 2. TypingSession.recordTouches + proximity(). 3. KeyProximity.distance.
4. settings/TouchMapScreen.kt.

---

## Session 25 — 2026-11-12 (Claude Code): keyboard height per fold/rotation; heat map + calibration agreed

### Why
User sent a screenshot (unfolded Fold, split keyboard, Ocean-style gradient theme) and asked:
1. The height should differ for folded vs unfolded, because you hold it differently long-ways
   vs short-ways. 2. A touch heat map would help accuracy for training. 3. Idea: a calibration
   tool at setup where you hold the phone naturally and rest/tap your thumbs, folded and
   unfolded, and the keyboard sets its height and key size from that. Asked for yes/no: YES.

### Decisions
- Four postures (view/BigScreen.kt `Posture`): folded/phone or unfolded/tablet (smallest width
  >= 600 dp, same rule as split) x upright/sideways (orientation). Each has its own height pref
  `key_height_<posture>`; the old single `key_height_scale` is the fallback, so nobody's height
  changes on update.
- KeyboardView picks the scale from the current Configuration in keyHeightPx(), so a fold or
  rotation (onConfigurationChanged → requestLayout) uses the right height with no service code.
- Height maths: base = min(54 dp, max(36 dp, 10% of screen height)) as before, then x scale;
  capped at max(base, 13% of screen height) per row (~2/3 of the screen with 5 rows). At 100%
  it's exactly the old height. Range widened from 80–130% to 60–150%: the old cap made the
  slider do nothing in landscape.
- Settings: current posture's slider first, labelled "Key height now (…)", then the other
  three. Sliders write on every drag step; the IME listens to `key_height_*` and resizes live
  in "Try it here".
- Heat map + calibration: not built yet, added to plan.md as "Fit-to-thumbs". Proposed design:
  * Heat map: per posture, per key, running mean + spread of where taps landed (relative to
    the key centre, in key units), only for taps whose word was kept (not undone/corrected),
    never in incognito or password fields, stored locally. Settings view draws dots/blobs over
    the keyboard. The means become per-user tap offsets (roadmap C) in KeyProximity, the same
    way SwipeAdaptation shifts swipe key centres.
  * Calibration: full-screen page in the setup wizard and Settings. Step 1 rest both thumbs
    where comfortable → sets the keyboard height (thumb-rest y ≈ middle row) and on big screens
    the split edges/gap (thumb x). Step 2 type a short sentence on a test keyboard → sets key
    height/width scale from tap spread and seeds the heat map. Asks to fold/unfold and do the
    other posture. Re-run any time.

### Files changed
- view/BigScreen.kt: `Posture` enum.
- view/KeyboardView.kt: `keyHeightScales` (per posture) replaces `keyHeightScale`; keyHeightPx.
- settings/Prefs.kt: keyHeightScale(p) / setKeyHeightScale / keyHeightScales(), KEY_HEIGHT_PREFIX,
  HEIGHT_MIN/MAX.
- settings/SettingsActivity.kt: KeyboardHeightSettings composable.
- ime/BoriImeService.kt: sets keyHeightScales; live update on `key_height_*` changes.

### Build
Not built yet (no Gradle here). Checked by reading. Risk: `remember(p)` with
`mutableFloatStateOf` (both already used elsewhere), `Posture.entries` (Kotlin 1.9+, fine).

### Not tested on the phone
All of it. Check: height at 100% unchanged; folded vs unfolded keep separate heights; turning
the phone sideways picks the sideways height; the slider resizes the "Try it here" keyboard live.

### Next step
User: Build + Install, try the heights. Then build the touch heat map (data first, so the
calibration has something to seed), then the calibration screen.

### How to catch up
1. view/BigScreen.kt Posture. 2. KeyboardView.keyHeightPx. 3. SettingsActivity
KeyboardHeightSettings. 4. plan.md "Fit-to-thumbs".

---

## Session 24 — 2026-11-11 (Claude Code): key styles + fonts (phase 3.3)

### Why
User built 3.2 (Bori build OK 16:49) and is using a theme; asked for the next stage.

### Decisions
- One drawing class, theme/KeyPainter.kt, used by both KeyboardView and the Settings previews
  (through Compose's nativeCanvas), so previews always match the keyboard. No allocations in
  draw(); prepare() builds the outline gradient/glow and floating shadow.
- Looks: flat; raised 3D (darker side edge under the face, face drops when pressed); sunken
  (dark rim on top); outline (stroke only, fills when pressed/accent); floating (soft shadow,
  Android 9+, offset rect fallback on 8; only solid keys float, see-through keys would show the
  shadow). Very dark keys get a lighter edge so raised/sunken still show.
- Shapes: rounded (theme radius), square, pill, circle (wide keys like space/shift become
  pills), squircle (radius 32% of the short side, an approximation, not a true superellipse).
- User overrides sit on top of any theme (ThemeOverrides in theme/KeyStyles.kt; Prefs
  key_look, key_shape, key_font, label_case, label_scale, key_opacity, key_hints; "" = theme's).
  Prefs.THEME_KEYS lists every pref that re-themes the keyboard; the IME theme key includes
  overrides.key().
- Fonts only on key labels, key previews and long-press popups. The suggestion strip keeps the
  phone's font for readability (Creepster suggestions would be hard to read). Missing glyphs
  (arrows, emoji) fall back to system fonts.
- Fonts: 14 from github.com/google/fonts, variable fonts pinned (Inter 450, Nunito 600,
  JetBrains Mono 500, Doto wght 800 ROND 100, Orbitron 600), subset to Basic Latin + Latin-1 +
  Latin Ext-A + punctuation + €/™ = 808 KB total. Checked: all have ñ á é í ó ú ü ¿ ¡ €.
  Licence texts in assets/fonts/licenses/<id>.txt; ATTRIBUTION.md has a "Keyboard fonts" table
  with the copyright lines from those files.
- No pip in this environment: fonttools 4.68.0 was downloaded as its pure-Python wheel from PyPI
  into /tmp/bori/pylib and used with PYTHONPATH. Re-run: same trick, or `pip install fonttools`.
- Background opacity moved to 3.4 (it only means something over a photo).
- Preset styles: Snow floating; Pastel + Candy Glass pill; High Contrast Atkinson 110%;
  Dot Matrix Doto circles; Neon LED Monoton caps; Gamer RGB + Galaxy Orbitron; Mechanical
  JetBrains Mono raised; Retro Typewriter Special Elite raised circles; Borinquen Nunito
  raised; Material You squircle; most others Inter or Nunito.

### Files changed
Theme engine
- theme/KeyPainter.kt (new), theme/KeyStyles.kt (new: KeyLook, KeyShape, LabelCase,
  ThemeOverrides), theme/Fonts.kt (new: font list + Typeface cache).
- theme/ThemeSpec.kt: KeyStyle look, shape, opacity, font, labelScale, hints, labelCase.
- theme/ThemeResolver.kt: takes ThemeOverrides; key opacity fades keyLetter/keyFunction alpha.
- theme/KeyboardTheme.kt: look, shape, typeface, labelScale, hints, labelCase;
  forContext(overrides).
Keyboard
- view/KeyboardView.kt: draws through KeyPainter; label font/bold font cached in setTheme;
  labels centred on the painter's face rect; letter size; hints switch; letter case.
- view/KeyOverlayView.kt: key preview + popup use the theme font.
- ime/BoriImeService.kt: re-themes on any Prefs.THEME_KEYS change; key includes overrides.
Settings
- settings/ThemesScreen.kt: rewritten. "Keys & letters" card (folds open) with a big live
  preview, chips for style/shape/font/letters, letter-size and transparency sliders, hints
  switch, "Use the theme's style" reset. Cards draw with KeyPainter.
- settings/Prefs.kt: the override prefs, themeOverrides(), THEME_KEYS.
Assets / tools / docs
- assets/fonts/*.ttf + licenses/*.txt (new), tools/build_fonts.py (new),
  tools/build_themes.py (K(**style)), presets regenerated (contrast still 0 problems),
  ATTRIBUTION.md.

### Build
Bori build OK at 17:09 (after this session's code was written, so it most likely includes it;
no errors reported). Before that it was checked by reading plus a brace/paren balance script. Fixed one stray `}` from an
edit splice in KeyboardView before finishing. Risky spots: `Typeface.create(theme.typeface,
BOLD)` with a nullable property (platform type, should be fine), shadow layers inside Compose's
hardware canvas, `KeyLook.entries` (Kotlin 1.9+, fine on 2.0.21).

### Not tested on the phone
Everything above. Watch for: label vertical centring in fonts with odd metrics (Monoton,
Press Start, Pacifico), Doto readability, raised keys with see-through themes, the glow plus
raised look on Gamer RGB, the floating shadow on Snow.

### Next step
User: Build + Install. Themes → Keys & letters; try styles, shapes and fonts with the test
box open; check Dot Matrix, Retro Typewriter, Mechanical, Neon LED, Snow. Then 3.4
(backgrounds: photo picker with zoom/brightness/blur, gradients, procedural scenes).

### How to catch up
1. theme/KeyPainter.kt. 2. theme/KeyStyles.kt (ThemeOverrides). 3. settings/ThemesScreen.kt
KeysAndLetters. 4. tools/build_fonts.py (fonts) and tools/build_themes.py (presets).

---

## Session 23 — 2026-11-11 (Claude Code): theme gallery + 21 presets (phase 3.2; built OK 16:49, in use)

### Why
User: "I don't see any options in the settings yet." Correct: phase 3.1 (built OK, 10:38)
was the engine only, with no visible change. This session adds the first visible part.

### Decisions
- New Settings → **Themes** page (own row on the start screen; Appearance & layout links
  to it). The light/dark mode chips moved there from Appearance & layout.
- Gallery / Mine as two chips (no TabRow, less API risk). Mine lists filesDir themes; it's
  empty until the editor (3.7) and says so.
- Previews are a Compose Canvas mini keyboard (strip + 4 rows) drawn from the resolved
  KeyboardTheme, so they show exactly the colours the keyboard will use. They follow the
  chosen mode (wantsDark) and the Key borders switch.
- Picking writes Prefs.themeId; the IME already re-themes on THEME_ID, so the "Try it here"
  box at the top updates live.
- "Key borders" (Gboard-style, Prefs.keyBorders, default on): off draws only labels; pressed
  and accent (Enter / caps lock) keys still get a shape. Part of the IME's theme key.
- Presets needed more than flat colours, so the spec gained (all optional, older files fine):
  `category`, colours `backgroundGradient` (top→bottom), `keyBorder`, `keyBorderGradient`
  (left→right, e.g. rainbow), keys `borderWidth`, `glow`. Glow uses Paint.setShadowLayer
  (hardware-accelerated on shapes from Android 9; skipped on 8).
- Gradient themes: InputRootView paints the gradient once; the strip, search bar, keyboard and
  panels draw `theme.solidBackground` (transparent when there's a gradient). The keys stay
  INVISIBLE under panels, so nothing shows through.
- Asset files are numbered (00-classic.json, 01-material_you.json …) so gallery order and
  sections come from the file names. classic.json was renamed to 00-classic.json.
- Presets come from tools/build_themes.py, which also checks WCAG contrast (text/key,
  function text/key, onAccent/accent, strip text, preview) over every gradient stop, with
  translucent keys blended. All 20 generated presets pass 4.5:1 (sunset/aurora gradients and
  pastel/mechanical/candy/galaxy accents were darkened to get there).
- Fonts are 3.3, so Dot Matrix and Retro Typewriter only have their colours/shapes for now.

### Themes (21)
Simple: Classic, Material You (wallpaper tokens, light+dark), Snow, Charcoal, Pure Black,
Pastel (light+dark), High Contrast Light/Dark. Glow & LED: Dot Matrix, Neon LED (cyan glow),
Gamer RGB (rainbow outline + glow), Mechanical. Glass: Glass, Candy Glass. Scenes: Sunset,
Ocean, Aurora, Galaxy, Retro Typewriter. Puerto Rico: Borinquen (light+dark), Coquí Night.
Themes without a dark variant look the same in both modes.

### Files changed
Theme engine
- theme/ThemeSpec.kt: category; gradient/outline colours; borderWidth, glow.
- theme/ThemeResolver.kt: resolves those; `keyShapes` parameter.
- theme/KeyboardTheme.kt: new fields, `solidBackground`, `hasOutline`; forContext(keyShapes).
- theme/ThemeStore.kt: comment only.
- assets/themes/: 00-classic.json (renamed) + 20 new NN-id.json.
- tools/build_themes.py (new): preset generator + contrast check.
Keyboard drawing
- view/KeyboardView.kt: borderPaint (outline, horizontal gradient shader, glow) built in
  setTheme/onSizeChanged, never in onDraw; keyShapes off skips plain key fills.
- view/InputRootView.kt: gradient background drawable.
- view/SuggestionStripView.kt, SearchBarView.kt, ClipboardPanelView.kt, EmojiPanelView.kt,
  GifPanelView.kt: solidBackground.
- ime/BoriImeService.kt: listens to KEY_BORDERS; theme key includes it.
Settings
- settings/ThemesScreen.kt (new): ThemesScreen, ThemeGrid, ThemeCard, KeyboardPreview.
- settings/SettingsActivity.kt: "themes" route + start-screen row; mode chips moved;
  CategoryScreen/SectionTitle/Note now internal.
- settings/Prefs.kt: keyBorders / KEY_BORDERS.

### Build
Not built (no compiler here); checked by reading. Risky spots: Compose drawRoundRect
overloads with Brush + alpha + Stroke in ThemesScreen, nativeCanvas text drawing.

### Not tested on the phone
Everything above. Check: gradient themes behind the strip and in the emoji/clipboard/GIF
panels; Neon/Gamer glow (Android 9+); Key borders off; Material You on the user's phone.

### Next step
User: Build + Install. Settings → Themes; tap several themes with the test box focused (the
keyboard should change at once); Key borders off; Dark. Paste errors if any. Then phase 3.3
(key styles + fonts; needs `pip install fonttools` for tools/build_fonts.py).

### How to catch up
1. .bori/theme-plan.md. 2. settings/ThemesScreen.kt. 3. tools/build_themes.py (edit presets
there, re-run, never hand-edit the generated JSON). 4. KeyboardView.updateBorderPaint.

---

## Session 22 — 2026-11-11 (Claude Code): Stage 3 theme plan + phase 3.1

### Why
User wants the theme engine "as similar as possible to Samsung's custom keyboard engine":
"the most fun keyboard I can". They answered the first draft's decisions: keep the phase
order; no effects/sounds in password fields; bundle free fonts. They also want rainbow/LED,
seasonal, holiday and weather themes, typewriter and iPhone-like sounds, a Nothing-style look,
raised/flat keys, crazy and normal fonts, press and background effects, and design-your-own.
No screenshots, so research Keys Cafe, SwiftKey and Gboard instead.

### Research (web, 2026-11-11)
- Keys Cafe: per-key colours, RGB colour effects, tap motion effects, touch sounds, photo
  backgrounds, night colours, "apply effects only", share/backup.
- SwiftKey: photo themes with brightness, key borders and hints switches.
- Gboard: solid/gradient/landscape themes, key-borders switch, photo themes, "My themes".
- LED/RGB apps: rainbow wave, breathing, pulse, ripple; neon/glass/3D/mechanical styles;
  thocky/clacky/creamy/typewriter/water-drop/piano sounds.
- Nothing's Ndot font is proprietary, so we use Doto (OFL-1.1). Apple's sounds can't be
  shipped, so we synthesize our own "Crisp tap". Open-Meteo for weather (CC BY 4.0, no key).

### Decisions
- Plan v2 (.bori/theme-plan.md, APPROVED): 10 phases (3.1–3.10), ~14–17 sessions. New:
  LED lighting in 3.5, ambient effects 3.9, living themes 3.10 (time/season/holiday offline;
  weather opt-in with coarse location or typed city, coordinates rounded to 0.1°).
- plan.md INTERNET rule now covers opt-in weather too.
- Colour values are strings with `|` fallbacks and Material You tokens (`@accent1_600`), so
  one theme file can follow the wallpaper and still work on Android 8–11.
- ThemeSpec has light + optional dark variants; missing colours fall back to the classic
  palette, so presets only list what they change.
- Theme re-apply is keyed on "id/night/revision" (was: night only). It also re-resolves on
  every configuration change so wallpaper colour changes show up.

### Files changed
Theme engine (phase 3.1)
- theme/ThemeSpec.kt (new): ThemeSpec, ThemeVariant, ThemeColors, KeyStyle (@Serializable).
- theme/ThemeResolver.kt (new): spec → KeyboardTheme; parseColor with hex/tokens/fallbacks
  (tokens via reflection on android.R.color.system_*, API 31+).
- theme/ThemeStore.kt (new): built-ins from assets/themes, user themes in
  filesDir/themes/<id>/theme.json, get() falls back to classic, save()/delete() ready for 3.7.
- assets/themes/classic.json (new): today's exact light/dark values.
- theme/KeyboardTheme.kt: forContext(context, night, themeId) now goes through the store.
- settings/Prefs.kt: themeId (default "classic"), themeRevision; THEME_ID/THEME_REVISION.
- ime/BoriImeService.kt: themeKey replaces themeNight; listener on THEME_MODE/ID/REVISION;
  DiagLog line "theme=<id> night=<b> <ms>"; a failing theme falls back to the classic palette.
Notes
- .bori/theme-plan.md (new), plan.md, handoff.md, this log.

### Build
Not built (no compiler here). Risky spots: `android.R.color::class.java.getField` reflection
(falls back to the hex value if it fails), kotlinx.serialization plugin on the new classes.

Build 1 failed: "Unclosed comment" in ThemeSpec.kt. The KDoc said `assets/themes/*.json`, and
Kotlin treats `/*` inside a comment as a nested comment opener, so the whole file became a
comment (every other error was "unresolved reference ThemeSpec"). Fixed by rewording; never put
a slash-star path in a Kotlin comment.

### Not tested on the phone
Everything. Expected: no visual change at all.

### Next step
User: Build + Install; check light, dark, Material You accent, theme mode switch, and Copy
diagnostics shows a "theme=classic" line. Then phase 3.2 (gallery + ~20 presets).

### How to catch up
1. .bori/theme-plan.md. 2. theme/ThemeSpec.kt + ThemeResolver.kt. 3. BoriImeService
applyThemeIfNeeded.

---

## Session 21 — 2026-11-11 (Claude Code): bigger clipboard + GIF search (KLIPY)

### Why
User: autocorrect is good now. The clipboard "only saves the last thing", so they want a more
advanced clipboard, plus GIF search in the keyboard. After that comes Stage 3 (theming) and a
light tidy-up of the autocorrect code.

### Findings
- Unpinned clips expired after **1 hour** (max 30), so older copies vanished. That's the most
  likely cause of "only the last thing".
- Bug: opening the panel re-captured the current system clip, so a clip the user had deleted
  or cleared came back if it was still on the system clipboard.
- The Tenor API was shut down on 2026-06-30. The choices are KLIPY (free, used by Discord and
  Bluesky, API mirrors Tenor's) or GIPHY (100 requests/hour unless paid).

### Decisions (asked the user)
- GIFs: **online search, opt-in**. INTERNET permission added (plan.md updated). GIF search is
  OFF by default, and the user pastes their own free KLIPY key in Settings → GIFs, so the key
  never goes into the public repo. Only the search words, country, filter level and a random
  customer id go to KLIPY. "Powered by KLIPY" is shown, as their guidelines require.
- Clipboard: setting "Keep copied items for" 1 hour / 1 day / **1 week (default)** / until
  deleted; up to 200 unpinned clips (30 pictures).

### Files changed
Clipboard
- clipboard/ClipboardHistory.kt: clips have ids; picture clips are copied into
  filesDir/clips/ (≤15 MB, in the background); each system copy is recorded once, by
  ClipDescription timestamp (stored as "stamp"), so deleted clips don't come back; the
  keep time comes from prefs; clearUnpinned() returns the removed clips for restore() (Undo);
  orphaned picture files are swept at the next start; the file format is now
  {stamp, clips:[...]} (old bare-array files still load); DiagLog lines "clip saved/skipped"
  (no content).
- view/ClipboardPanelView.kt: 🔍 chip, "📌 Pinned" / "Recent · N" sections, picture
  thumbnails, "5m ago" time and 🔗/✉/📞/🖼 icons per card, filter header "Clips with “x” ✕".
- ime/BoriImeService.kt: capture() also runs on every onStartInputView; picture clips are
  pasted via commitContent; Clear shows "Cleared N clips [Undo] [OK]".
GIFs
- gif/GifItem.kt (parses KLIPY items: sm.webp preview, sm.jpg still, md.gif to send if
  ≤4 MB else sm.gif, hd.gif link; skips non-"gif" items such as ads), gif/KlipyClient.kt
  (HttpURLConnection, search/trending/share), gif/MediaLoader.kt (previews in a 12 MB memory
  cache, animated via ImageDecoder on Android 9+; files to send go to cache/gifs/, last 8 kept).
- view/GifPanelView.kt: [ABC] [🔍 Search KLIPY] [😊], chips (🕘 Recent, 🔥 Trending, quick
  searches in en/es), a 3+ column grid with endless scroll, messages for off/no key/errors.
- view/EmojiPanelView.kt: GIF button next to ABC (Listener.onEmojiGif).
- ime/ContentSender.kt (new): commitContent through a FileProvider (authority
  com.bori.keyboard.files, res/xml/file_paths.xml). If the field doesn't take image/gif, the
  GIF link is typed instead and the strip says so.
Search typing (shared by GIF and clipboard search)
- view/SearchBarView.kt (new): [←] [🔍 query▌] [✕] in the strip's place.
- view/InputRootView.kt: Panel enum (KEYS/CLIPBOARD/EMOJI/GIF) + show(panel); searchBar.
- BoriImeService: while searchFor != null, letters/space/delete go into the query, Enter (⌕)
  searches, 😊 or ← cancels; glide, cursor slide, and delete slide (which clears instead) are
  off; the layout from before the search is restored.
Settings / docs
- settings/Prefs.kt: clipboardKeepMs, gifSearch, klipyKey, gifFilter, gifCustomerId, recentGifs.
- settings/SettingsActivity.kt: "GIFs" category (switch, key field, link to
  partner.klipy.com, safe search Strict/Medium/Low/Off, forget recents); keep-for chips under
  Privacy; EXTRA_SCREEN lets the keyboard open the GIF page directly.
- AndroidManifest.xml: INTERNET + FileProvider. README, ATTRIBUTION, plan.md updated.

### Build
Not built. No compiler here; checked by reading. Risky spots if Bori's build fails:
androidx.core FileProvider/InputConnectionCompat imports, `var screen by startScreen` in
SettingsActivity, KLIPY share POST body (a guess: {customer_id, q}; fire-and-forget).

### Not tested on the phone
Everything above. Especially: whether the clipboard listener gets copies while the keyboard
is hidden on One UI (capture-on-open covers the newest one either way), picture clips from
Samsung's screenshot copy, GIF sending in Samsung Messages / Google Messages / WhatsApp.

### Next step
User: Build + Install. Then:
1. Copy 3–4 different texts in a row, open 📋, and check they're all there. Copy a picture
   from Gallery/Chrome and paste it into Messages.
2. Delete a clip, close the panel, and open it again: it should stay gone. Try Clear → Undo,
   and 🔍 search.
3. Get a free key at partner.klipy.com, paste it in Settings → GIFs, turn GIF search on, then
   use 😊 → GIF → search "hello" → tap a GIF in Messages.
If anything fails, Copy diagnostics (look for "clip saved", "gif fetch failed", "gif send").
After that: Stage 3 theme engine, then a tidy-up pass on the autocorrect code.

### Typo log (same session, second request)
User pasted the typo log again (cut off at the end) and confirmed settings survived the install.
- 46 of the 56 lines were the Session 15 lines (the log is never cleared and has no times).
  10 new lines were added to tools/.cache/raw/typos/my_typos.jsonl (56 total).
- New real failures: **hub → huh** (undone) and **chcia → Chris** (user picked Chicago).
  thermfl/emebaels/mkns/Eegs undos look like test typing (the edits were "emeeebald", "mknz").
- hub: suggest_sim gives no autocorrect (huh only 0.2 ahead, real-word margin 1.0; hub freq
  100 counts as a "rare look-alike" below REALFIX_RARE_FREQ 170). In Kotlin, realWordFix
  scored the fix WITH the user's boosts but the typed word WITHOUT them; typing "huh" a few
  times adds about 1.1. **Fix:** predict/Suggester.kt realWordFix subtracts userBoost from
  the fix too (both sides without boosts). The sim never had user boosts there, so the
  benchmarks are unaffected.
- chcia: with taps the sim picks chica; Chicago is the best candidate (2.5–3 ahead), but
  completions can only autocorrect from an exact prefix (chcia transposes chic). Left for
  Stage B (allow a cheap-prefix completion with a big margin; needs a bench run).
- ime/TypoRecorder.kt: each line gets "t" (MM-dd HH:mm), and Copy typo log puts the newest
  lines first (pastes get cut off at the end).

### How to catch up
1. This entry. 2. ime/BoriImeService.kt sections "GIF panel" and "search bar".
3. clipboard/ClipboardHistory.kt capture().

---

## Session 20 — 2026-11-05 (Claude Code): app icon + first GitHub push

### Why
User: give Bori Keyboard the same Puerto Rican icon style as Bori Habit Tracker and Bori - Code,
then publish the project on GitHub as public open source.

### Decisions
- Looked at the two icons on GitHub (Atom0131/Bori-Code scripts/gen_bori_code_icon.py, and
  Atom0131/Bori---Habit-Tracker new_icon_assets/bori_app_icon_512.png). Shared style: celeste
  flag blue #4FB4F2, a red mark (#D10002), white, the coquí petroglyph in coquí blue #0E4F8A
  at the bottom.
- Keyboard icon: white keys (5/4/5 + spacebar), a red swipe trail across them (the habit
  tracker's red heartbeat line as a glide stroke), solid coquí below (strokes copied from
  Bori - Code's script). Generated by tools/gen_icon.py, same pattern as Bori - Code's script;
  checks the 33-unit safe circle (furthest point 32.9).
- Adaptive icon only (mipmap-anydpi-v26): minSdk is 26, so no PNG mipmaps needed.
- GitHub: public repo, GPL-3.0 LICENSE added (plan said GPL-3.0-or-later), short README.

### Files changed
- tools/gen_icon.py (new), tools/icon_preview.png (512 px render for the README).
- res/drawable/ic_launcher_{background,foreground,monochrome}.xml,
  res/mipmap-anydpi-v26/ic_launcher{,_round}.xml (generated).
- AndroidManifest.xml: android:icon / android:roundIcon.
- LICENSE, README.md (new); .gitignore: __pycache__/, *.jks, keystore.properties, .bori/agent.lock.

### Build
Icon not built yet.

### Next step
User: Build + Install; check the icon on the home screen and in Android's keyboard settings.

### How to catch up
1. This entry. 2. tools/gen_icon.py docstring.

---

## Session 19 — 2026-11-05 (Claude Code): "emoji broke the keyboard" = 🌐 key beside 😊

### Why
User: after the Session 18 build, tapping 😊 broke the keyboard and Android switched to Gboard.
Pasted Copy diagnostics twice; both pastes were cut off before the end, where DiagLog puts the
`=== CRASH` stack trace, so the exact error is still unknown.

### Checked (fine)
.bep format matches EmojiPack's reader (twemoji 1300 entries pointing at RIFF/WebP data,
custom.bep has 🇵🇷). Pack loading and decoding were already in try/runCatching.

### Decisions
- Can't see the trace, so: make it impossible to lose next time, and make the panel unable to
  take the keyboard down.
- Copy diagnostics now puts the crash report FIRST (pastes get cut at the end).
- A native crash (e.g. Skia WebP decode) can't be caught, so a crash guard: before loading a
  style the panel writes `opening_style` (commit, synchronous) to prefs `emoji_guard`; it is
  cleared 500 ms after the grid's first draw. If it's still there on the next open, that style
  is marked `crashed_style` and the panel uses the phone's font until the user picks a
  different style in Settings.

### Files changed
- ime/DiagLog.kt: export() moves `=== CRASH` to the top; new error(what, t) (6 stack frames).
- view/EmojiPanelView.kt: crash guard (GUARD_PREFS/GUARD_KEY/BAD_KEY, drawnOk, markDrawn via
  draw() override), "emoji open style=… pack=… recents=…" event, try/catch around custom pack,
  pack load, picture decode and drawBitmap (one failure → phone font for the session),
  hasGlyph guarded; emoji Bori draws itself (🇵🇷) kept even if the phone font lacks it.
- ime/BoriImeService.kt: toggleEmoji and onEmoji wrapped in try/catch (logs + saves, stays on
  the keys); "emoji tap" event.

### Follow-up (same session)
User pasted again: the `=== CRASH` was an OLD DeadSystemException in SettingsActivity.isEnabled
(line 106, fixed in Session 12; DiagLog keeps the crash section until Clear). The paste was
cut again at ~5,000 characters before the emoji part, and no "emoji open" line was visible,
so it's not certain the new build was installed. Changes:
- DiagLog.export: newest 45 events first, then the crash report (14 lines); crash header now
  has the date/time ("=== CRASH on main at 2026-…").
- Service onCreate logs `build=<install time>`; "emoji key" and "globe key: switching
  keyboard" events; DiagLog.save before/after toggleEmoji so a hard kill keeps them.

### Root cause found (same session)
With the new build's diagnostics: "globe key: switching keyboard" then "service onDestroy".
There was no crash: in text fields the bottom row had 🌐 and 😊 side by side (same size),
the user hit 🌐, and it switched to the next keyboard (Gboard). Fix in layout/Layouts.kt
bottomRow: 🌐 only shows when there's no 😊 (email/URL fields); holding space opens the
keyboard picker everywhere (onKeyLongPress, unchanged). The crash hardening above stays.

### Build
Diagnostics build installed OK (build=2026-11-05 10:32 shown in the log). Globe change built
and installed: user confirmed "it works".

### Settings "reset" after every install (open, same session)
User: swipe trail (rainbow, thicker) and emoji style (Blobs) go back to defaults after each
install, and Bori must be turned back on as the keyboard each time. Android shows "Update"
(not a fresh install). No Bori code resets or deletes prefs (checked: only DiagLog /
TypoRecorder / SwipeRecorder delete their own files on Clear). An old pre-Session-12 crash
survived several installs in files/, so app data seems to survive updates. Theory: Samsung
switches to Gboard while Bori updates, so Gboard's trail/emoji make it look reset. Added: at
service onCreate a "settings trail=<int: 0 theme, 1 rainbow, else ARGB> width=<dp>
emoji=<id> firstInstall=<time>" line. If firstInstall changes between builds, Bori really is
being removed (then data loss is in Bori Code's install, not our code).
Result: after building + installing this version the user reported "it's fixed" (settings kept
across the install). No code change touched saving, so the earlier resets were most likely
the Gboard switch during updates, or an install that removed Bori. If it comes back, compare
firstInstall= across two builds in Copy diagnostics.

### Not tested on the phone
Emoji panel itself (styles, 🇵🇷) and the new bottom row. Root cause of the crash: none (it was the 🌐 key): if it crashes again, the trace is now at the top.

### Next step
User: Build + Install, tap 😊 in Messages, tap an emoji, switch categories and styles. If
anything goes wrong, or the panel shows the phone's emoji instead of the chosen style, Copy
diagnostics and paste the first ~40 lines (look for "=== CRASH", "ERROR emoji" or
"crashed the keyboard").

### How to catch up
1. This entry. 2. view/EmojiPanelView.kt open() and pictureOf(). 3. Session 18 entry.

---

## Session 18 — 2026-11-05 (Claude Code): emoji styles + light-blue Puerto Rico flag

### Why
User: emoji from Oppo, Xiaomi, Samsung, Apple and other phone makers to choose from; then "the
old good blobs" and more cartoon styles ("I hate the modern new"); then the Puerto Rico flag
with the light-blue triangle, not navy.

### Decisions
- Phone makers' emoji art is proprietary: not shipped. User picked open sets instead (Twemoji,
  Fluent, OpenMoji, Noto) and a new style as default (Twemoji). Then added Blobs (Blobmoji,
  Apache 2.0), EmojiOne 2.2.7 (classic cartoon, CC BY 4.0) and FxEmoji (Firefox OS, CC BY 4.0).
- A style only changes Bori's emoji panel; the receiver sees their own phone's emoji. Told the user.
- Packs = one .bep file per style (index + 72 px WebP per emoji), loaded whole into memory
  (≤2.8 MB, one style at a time), decoded on draw with a 6 MB bitmap LruCache. Emoji a set
  lacks are drawn with the phone font. The hasGlyph filter is unchanged (no tofu in the field).
- Fluent uses the 3D PNGs (flat ones are SVG-only and I had no renderer at first).
- Puerto Rico 🇵🇷: own drawing (tools/make_emoji_overrides.py, sky-blue 1895 triangle) in
  custom.bep, used first in every style including "Phone default". The sent character is the
  normal 🇵🇷, so other phones show their own flag.

### Files changed
- tools: build_emoji_packs.py (new), make_emoji_overrides.py (new), emoji_overrides/1f1f5-1f1f7.png,
  README.md section "Emoji style packs".
- assets/emoji: twemoji 1.72 MB, fluent 2.44, openmoji 2.57, noto 2.78, blobmoji 2.44,
  emojione2 2.40, fxemoji 1.54, custom.bep (APK +~15.5 MB).
- view/EmojiPack.kt (new: .bep reader, STYLES list, custom()), view/EmojiPanelView.kt
  (open(recents, style), draws bitmaps, EMOJI_FILL 0.66), ime/BoriImeService.kt (passes style),
  settings/Prefs.kt (emojiStyle, default twemoji), settings/SettingsActivity.kt (Appearance →
  "Emoji style" chips + preview row + credit; new imports asImageBitmap, sp, EmojiPack).
- ATTRIBUTION.md: "Emoji styles" table.

### Build tooling problems (fixed)
- No Pillow/pip: installed with `xbps-install python3-Pillow`, and `librsvg-utils` for SVGs.
- Noto moved PNGs to 2D/png/72; names need 4-digit hex (emoji_u00a9); OpenMoji too (00A9).
- Worker threads raced building the Fluent/FxEmoji name maps (half-empty maps, 8× metadata
  downloads): maps now built before the pool. Network errors retry 5×, never cached as missing.
- `pkill -f build_emoji_packs` also killed the shell running it (name in its command line).

### Coverage
twemoji/openmoji/noto 1300/1300; fluent 1226 (no flags, no couples/families); blobmoji 1198;
emojione2 1078 and fxemoji 824 (old sets: newer emoji use the phone font).

### Build
Not built yet. Watch SettingsActivity imports and EmojiPack's private constructor use.

### Not tested on the phone
Everything: panel drawing, scroll speed with bitmaps, memory, Settings preview, APK size.

### Next step
User: Build + Install. Settings → Appearance → Emoji style: try each; open 😊 panel, scroll
categories, check 🇵🇷 in Flags.

### How to catch up
1. This entry. 2. view/EmojiPack.kt, tools/build_emoji_packs.py docstring.

---

## Session 17 — 2026-11-05 (Claude Code): swipe trail colour and thickness

### Why
User: "can we also add swipe trail custom like trail color and size".

### Decisions
- Colour presets instead of a full colour picker (fits the screen, no extra library): Theme
  (keyboard accent, the old behaviour and default), Rainbow, white, black, red, orange, yellow,
  green, cyan, blue, purple, pink. A full picker can come with the Stage 3 theme engine.
- Stored as one Int: 0 = theme, 1 = rainbow (alpha-0 values, never a real preset), else ARGB.
- Rainbow hue follows the point index (4° per point), so colours stay put along the path.
- Thickness slider 2–16 dp, default 5 dp (old fixed value); older segments still taper.
- Settings shows a live preview curve drawn like the keyboard draws it.

### Files changed
- settings/Prefs.kt: glideTrailColor, glideTrailWidth, TRAIL_THEME / TRAIL_RAINBOW /
  DEFAULT_TRAIL_WIDTH.
- view/KeyboardView.kt: `trailColor`, `trailColorOrAccent()` passed to setTrail.
- view/KeyOverlayView.kt: `trailWidthDp`, rainbow drawing (RAINBOW_STEP).
- ime/BoriImeService.kt: applies both prefs in onStartInputView.
- settings/SettingsActivity.kt: Swipe & gestures → under "Show swipe trail" (only when on):
  colour swatches, thickness slider, preview (TrailSettings, TRAIL_COLORS, trailColorName);
  new Compose imports (Color, background, border, size, height, semantics).

### Build
Not built yet. Watch for Compose import errors in SettingsActivity.kt.

### Not tested on the phone
Everything above. Changes apply the next time the keyboard opens in a field.

### Next step
User: Build + Install. Settings → Swipe & gestures: pick a colour + thickness, then swipe.

### How to catch up
1. This entry. 2. KeyOverlayView.drawTrail, SettingsActivity TrailSettings.

---

## Session 16 — 2026-11-05 (Claude Code): emoji panel fills the whole screen

### Why
User: "emojis make the whole keyboard go big and over the screen" (tapping 😊).

### Cause
In view/InputRootView.kt the keys area was a plain FrameLayout with WRAP_CONTENT height, and
the emoji panel (MATCH_PARENT) holds a ScrollView whose grid measures its full content height
(~1000 emoji = hundreds of rows). FrameLayout took the tallest child, so the ScrollView claimed
all the height the window allowed and the keyboard covered the screen. The comment "keys stay
laid out (invisible) so the height doesn't jump" assumed the keyboard set the height; it didn't.

### Change
- view/InputRootView.kt: new private `KeysFrame` (FrameLayout) measures the keyboard first, then
  measures every other child (clipboard, emoji panel) EXACTLY at the keyboard's width x height.
  The clipboard panel gets the same fix (it would grow with many clips the same way).

### Build
Not built yet.

### Not tested on the phone
Emoji panel height, scrolling inside the grid, the clipboard panel, split layout on the Fold.

### Next step
User: Build + Install, tap 😊: the panel should be exactly the keyboard's height and scroll.

### How to catch up
1. This entry. 2. view/InputRootView.kt `KeysFrame`.

---

## Session 15 — 2026-11-02 (Claude Code): typo-log fixes + correction/prediction roadmap

### Why
User: prediction is okay but should get better; pasted the typo log (46 lines). Asked what other
keyboards use (Google etc.), four sources, and how long training would take. Decision: fix what
the log shows now (Stage A); plan the rest in stages with time estimates (plan.md checklist).

### Research (4 sources)
1. Gboard decoder: Ouyang et al. 2017, arXiv 1704.03987: spatial model + lexicon FST + n-gram LM in one
   search (corrections, completions, predictions, optional spaces). Bori works the same way, with a trie.
2. Gboard neural LM: Hard et al. 2018, arXiv 1811.03604 (CIFG-LSTM, ~1.4M parameters, 10k words, federated);
   2024 "Neural Search Space in Gboard Decoder" puts a neural LM inside the decoder.
3. FUTO Keyboard: AOSP LatinIME + on-device transformer LM, fine-tuned on the phone while idle,
   "Transformer LM strength" slider; FUTO Swipe = 0.6M + 0.3M + 1.5M parameter models.
4. AOSP LatinIME / HeliBoard: n-gram dictionaries (3 previous words) + user history + proximity
   edit costs. SwiftKey: on-device neural LM since 2016 (closed source).

### What the log showed (replayed with tools/suggest_sim.py)
Most bare "undo" lines were edits (no "keep" followed; djne→done, iur→our were right fixes).
Real failures: rje/rhse → "rhe" (a learned slip used as the fix), "points or" → of, w → a,
asmr → "as Mr", wednes → wedges (wanted Wednesday), opne → one (wanted open; the typo list itself
says opne→one), thebapp → Theban (wanted "the app": "app" freq 124 < SPLIT_MIN_FREQ 140).

### What changed
- predict/Suggester.kt:
  - Learned words as fixes: unconfirmed learned words that are listed typos are skipped; other
    unconfirmed ones get LEARNED_UNCONFIRMED_PENALTY = 2.0 (rje no longer → rhe).
  - realWordFix: two-letter words with freq >= REALFIX_SHORT_COMMON_FREQ (170) are never swapped (or/of).
  - Split pieces must be lowercase words (dict.isLowercaseWord) except I/I'm/I'll/I've/I'd
    (no "as Mr").
  - Completion as autocorrect: typed invalid, >= 5 letters, exact prefix, the top suggestion is a
    completion that beats the best fix by AUTO_COMPLETE_MARGIN 1.0 while that fix costs >= 0.9;
    not at MILD (wednes → Wednesday).
- dict/BinaryDictionary.kt: isLowercaseWord().
- ime/TypingSession.kt + TypoRecorder.kt: after an undo, the word committed in its place is logged
  as kind "edit" (typed → what the user meant). Cleared on cursor moves and finish().
- tools/suggest_sim.py: the same rules, plus knobs CAPITAL_PIECE_MIN_FREQ, SPLIT_EXACT_ENTRIES,
  AUTO_COMPLETE_*, REALFIX_SHORT_COMMON_FREQ for CFGS sweeps.
- tools/typo_bench.py: set A + 8 log cases; set U reads "edit" lines (looks_like_fix filter).
- tools/.cache/raw/typos/my_typos.jsonl: +15 lines from the paste (46 total).

### Tried and rejected (benchmarks)
- Never real-word-fixing single letters (w stays w): Spanish E −2 items ("t" → y is a real fix). Reverted;
  undoing w→a twice already stops it (KEEP_AFTER).
- Never splitting a dictionary word typed exactly: es F −1.2 pts, en F −2.8, E −1.3. Reverted; the
  lowercase-pieces rule already stops asmr → "as Mr".

### Results (typo_bench 300/set; before → after)
- en: B 86.2 → 86.2, C 0% → 0%, D1 90.3 same, E 73.0 same, F 78.4 same, M/V same. A: 7 of 9 new log
  cases pass (opne, thebapp, w still fail). L 64 → 58% only because new hard items were added (old items unchanged).
- es: B 84.5 same, C 0%, E 68.4 → 67.9 (1 item, the two-letter rule), F 79.1 same, M/L/V same.
- Run times on the phone: en 37–40 min, es 10 min per full run (one process); slower in parallel.

### Revised Stage B estimate (from these run times)
One full en run ≈ 40 min, so a sweep must use N=100 per set (~13 min en, ~4 min es) with 3 runs in
parallel: ~40 configs ≈ 3 h en + 1 h es, then a 300/set confirmation (~50 min). Total ≈ 4–5 h unattended.

### Build
Not built yet.

### Known gaps / not tested on the phone
- opne → one (needs Stage B: typo-list weights, transposition vs extra letter), thebapp → Theban (split min freq).
- w → a until the user undoes it twice.
- "edit" log lines can be noisy (the user may delete more than the word); the bench filters them.

### Next step
User: Build + Install APK. Type wednes, necc, "points or", rje, rhse, asmr. Keep the typo log on;
after a few days paste Copy typo log (now with "edit" lines). Then Stage B (tools/fit_costs.py sweep).

### How to catch up
1. This entry. 2. plan.md roadmap (Stages A–F). 3. Suggester.chooseAutocorrect / realWordFix / piece,
TypingSession.logEdit.

---

## Session 14 — 2026-11-02 (Claude Code): next-word prediction that reads the sentence

### Why
User: predictions should read the sentence and guess the next word like Gboard / Samsung / open
keyboards; together with correction it should get much better.

### Research
- Gboard: Katz 5-gram FST (1.25M n-grams) until 2018, then a CIFG LSTM (670 units, 10k words,
  ~1.4M params) trained with federated learning (Hard et al. 2018, arXiv 1811.03604).
- AOSP LatinIME / HeliBoard: n-gram dictionary with up to 3 previous words + learned user history.
- FUTO Keyboard: optional on-device transformer (native llama.cpp; not possible for Bori).
- Samsung Keyboard: closed source.
- Decision: 4-gram model now (pure Kotlin, offline). Neural LM stays Stage 6 (no numpy/torch here
  to train, ~2 GB free RAM).

### User decisions
Add OpenSubtitles text (OPUS); about 6–8 MB of extra data per language.

### What changed
- tools/ngram_count.py (new): counts 2/3/4-grams in Tatoeba + first 20M OpenSubtitles lines per
  language (3 worker processes, GNU sort -m merge, ~4 min/lang) into tools/.cache/ngrams/. The
  last 1M subtitle lines are a held-out block (a first try held out every 50th line: duplicate
  films leaked and top-1 looked like 33%).
- tools/build_dict.py: bigrams now come from those counts (BIGRAMS=tatoeba = old way). Same words
  and nodes; bigram contexts 30k → 79k (en), .bdic 18.7 → 21.4 MB (en), 18.6 → 22.8 MB (es).
- tools/build_ngrams.py (new): .ngram format (2- and 3-word contexts), interpolated absolute
  discounting on top of the app's bigram score, gain-based (Stolcke-style) pruning to
  KEEP3=250k / KEEP4=130k entries → 4.6 MB en, 4.7 MB es.
- tools/predict_bench.py (new), tools/README.md, ATTRIBUTION.md (OPUS/OpenSubtitles credit).
- dict/NgramModel.kt (new) reader; BinaryDictionary.Reader is now internal (+ longs()),
  `BinaryDictionary.ngrams`; dict/Dictionaries.kt loads <lang>.ngram (optional: missing,
  mismatched node count or OOM → word pairs only; load line in Copy diagnostics).
- predict/Suggester.kt: Context has `earlier` words + `earlierStart`; lm() adds the 3/4-gram
  score (so corrections, completions, splits and glide ranking use the sentence too); predict()
  takes candidates from n-gram + bigram lists and scores all with lm(); user triple boost.
- ime/TypingSession.kt: contextOf reads up to 3 words back in the sentence (commas skipped, other
  punctuation stops, CONTEXT_CHARS 64 → 128); learn() records three-word phrases.
- learn/UserStore.kt: DB v4, `triples` table (learned, forgotten with Forget / word removal;
  nothing learned in incognito as before).

### Results (tools/predict_bench.py, 4000 held-out subtitle lines)
| | top-1 | top-3 |
|---|---|---|
| en old app | 10.5% | 19.4% |
| en new | 16.8% | 28.8% |
| es old app | 10.9% | 17.6% |
| es new | 15.8% | 26.0% |
Size sweep: 1.7 MB already gives 28.5% top-3 (en); above ~4.6 MB nothing changes.
typo_bench (300/set): en unchanged within 1 item (E 73.6→73.0, F 79.5→78.4, B/C/D same; C 0%).
es: B 83.5→84.5, E 72.2→68.4, L 100→90% ("una cancion" learned 12× no longer fixed: "una canción"
dropped out of "una"'s top-32 bigrams). C false changes still 0%.

### Build
Not built yet.

### Known gaps / not tested on the phone
- Load time and memory of the bigger files (2 languages cached ≈ 2×(22+4.7) MB).
- tools/suggest_sim.py still models bigram-only lm (typo_bench uses one previous word, so it's
  unaffected); keep in mind when tuning with multi-word context.
- Subtitle-flavoured guesses ("cómo estás" → nena) are possible.
- The user triples table has no pruning (same as pairs).

### Next step
User: Build + Install APK. Try "what are you", "see you", "I'm going to", "voy a", "muchas";
check correction "how are yiu". Paste Copy diagnostics if prediction looks unchanged (look for
the `ngrams en read in ...` line).

### How to catch up
1. This entry. 2. tools/README.md "Sentence-context prediction". 3. Suggester.lm/resolve/predict,
dict/NgramModel.kt.

---

## Session 13 — 2026-11-02 (Claude Code): taps land "a pixel off" on rows 2 and 3

### Why
User: keys on the second and third rows feel slightly misaligned; constant mistypes. Asked to
compare with Gboard, Samsung Keyboard and open-source keyboards.

### Findings
- `layout/KeyGeometry.kt` is fine: row 2 is offset half a key, z starts at 1.5 keys (under s),
  same as AOSP/Gboard. Hit boxes tile the keyboard with no gaps. Labels are drawn centred.
- The real problem was in `view/KeyboardView.kt`: every ACTION_MOVE re-ran `keyAt()` and switched
  the key with **no hysteresis**, and a tap typed whatever key was under the finger **at lift-off**,
  passing the lift-off x/y to correction. Thumbs roll a few px when lifting, so a tap near a
  border (more common on the staggered rows) typed the neighbour.
- AOSP / HeliBoard (`KeyDetector`, `PointerTracker`): key changes only after the finger goes a
  hysteresis distance past the key edge (`isMajorEnoughMoveToBeOnNewKey`); on up they send the key
  with `mKeyX/mKeyY` = where that key was first touched, not the lift point. HeliBoard's vertical
  correction is 0dp. Gboard and Samsung Keyboard are closed source; can't read their code.

### Decision / changes (view/KeyboardView.kt only)
- `Pointer.keyX/keyY`: position when the finger got onto the current key; `commit()` sends those.
- Hysteresis `KEY_HYSTERESIS = 0.25` key widths/heights past the key's hit box before the touch
  moves to another key. Sliding to a neighbour still works, just needs a clear move.
- No vertical touch offset added (HeliBoard uses none; KeyProximity already handles near-misses).

### Build
Built and installed by the user (2026-11-02). User: "thanks it more in line now", so rows 2/3
taps feel aligned.

### Not tested on the phone
Not separately checked: popups, flicks, rollover and glide start should be unchanged.

### Next step
User: Build + Install, type normally for a while. If still off, a "touch offset" setting or using
the Swipe Trainer's learned offsets for taps are the next options.

### How to catch up
1. This entry. 2. `view/KeyboardView.kt` `move()` (hysteresis) and `commit()`.

---

## Session 12 — 2026-11-02 (Claude Code): swipe/suggestions/corrections off in Google search

### Why
User: swipe, suggestions and corrections work only in Bori's own settings, "no corrections
happen at all" anywhere else.

### Diagnosis (from the user's Copy diagnostics)
- Dictionary was fine: `en read in 97 ms, heap 39/512 MB`, loaded 507,886 words.
- Google search box (com.google.android.googlequicksearchbox) fields are type 2752513
  (0x2A0001) and 2801665 (0x2AC001): text fields with **TYPE_TEXT_FLAG_NO_SUGGESTIONS**
  (0x80000). `EditorPolicy` treated that flag as "no suggestions, no autocorrect, no glide",
  so the log showed `glide on=false ... fieldAllows=false`.
- Google Messages (type 2244609, no flag) worked: glide on, 8 words decoded per swipe.
- type=0 lines (launcher, messaging between fields) are TYPE_NULL and are fine to skip.
- The crash at the end was `DeadSystemException` from `isEnabled()` in SettingsActivity's
  700 ms poll while the phone's system restarted. Not our bug, but it crashed Settings.

### Decision
Gboard glides and corrects in the Google search box, so Bori now ignores the app's
no-suggestions flag by default. New setting (Typing → Suggestion bar) "Follow apps that turn
suggestions off", default off, brings back the old behaviour. Passwords, non-text classes and
email/URL fields still get nothing (unchanged).

### Files changed
- ime/EditorPolicy.kt: new `appNoSuggestions` field; the flag no longer sets `noSuggestions`.
- ime/TypingSession.kt: `suggestionsOn` also checks `appNoSuggestions && prefs.followNoSuggestions`;
  glideStatus logs both.
- settings/Prefs.kt: `followNoSuggestions` (key follow_no_suggestions, default false).
- settings/SettingsActivity.kt: the new switch + note; `isEnabled`/`isSelected` wrapped in
  runCatching (DeadSystemException).

### Build
User (2026-11-02): "thanks we are good to go". Taken as built and working.

### Known gaps / not tested
- Email and URL fields (e.g. Chrome's address bar) still have no swipe; Gboard swipes there.
  Change only if the user asks.
- Two swipes in the log decoded 0 words (82 and 144 points): the decoder found no candidate
  for those shapes. Worth a Swipe Trainer look if the user notices words being dropped.

### Next step
User: Build + Install, then test swipe, suggestions and autocorrect in the Google search box,
then Messages again. If something's still off, paste Copy diagnostics (the glide line now shows
`appNoSuggest=` and `follow=`).

### How to catch up
1. This entry, then Session 11.
2. `ime/EditorPolicy.kt` `from()`, `ime/TypingSession.kt` `suggestionsOn`.

---

## Session 11 — 2026-11-01 (Claude Code): no swipe and no correction after the Session 10 build

### Why
User, after the 2026-11-01 14:42 build (all of Session 10): "the keyboard now has no swipe and
no text correction at all".

### Diagnosis
- Swipe (`canGlide`) and correction both turn off only when `session.setDictionary(null)`, i.e.
  the dictionary failed to load. `Dictionaries` used `runCatching`, which also swallows
  `OutOfMemoryError`, so it failed silently.
- The assets are fine: `tools/verify_dict.py app/src/main/assets/dict/en.bdic` reads all
  1,232,733 nodes / 507,886 words; the build ran after the last asset write.
- Likely cause (not confirmed on the phone): memory. `fromAsset` did `readBytes()` on the
  18.7 MB asset (a growing buffer: ~2-3× the file in short-lived copies) and then copied it into
  ~20 MB of arrays, with the other language (another ~20 MB) still cached. The old 5 MB files fit.

### Changes
- dict: `BinaryDictionary.kt` streams the file through a 64 KB little-endian `Reader` straight
  into its arrays (no whole-file buffer; same .bdic format). `Dictionaries.kt`: on
  OutOfMemoryError drops the cached languages, GCs and retries once; logs load time, typo count,
  heap use, or the exact error to DiagLog.
- `AndroidManifest.xml`: `android:largeHeap="true"`.

### Build
Built + installed: user confirmed swipe and correction work again ("it worked"). So the
dictionary load (most likely memory) was the cause.

### Next step
Session 10 phone tests (see Session 10 "Next step"). If swipe/correction ever go off again: Settings → Copy diagnostics and
paste it; look for the "dictionary en ..." lines (OUT OF MEMORY / load error / read in N ms).
If memory is the problem, the next fix is caching one language only, or shrinking the trie.

### How to catch up
1. This entry, then Session 10 below.
2. `dict/Dictionaries.kt` `load()`, `dict/BinaryDictionary.kt` `Reader`.

---

## Session 10 — 2026-11-01 (Claude Code): rhe→the for real, typo list, 500k-word vocabulary

### Why
- User: "rhe is still not becoming the" (after the 2026-11-01 11:21 build), and "train the
  keyboard on more common typing mistakes".
- Then: it doesn't know AI terms, "foldable", slang (every decade, 1920s–2020s), education,
  science and medical words; "train it on Merriam-Webster / Urban Dictionary". Asked to pick a
  size, the user chose "Everything".

### Root cause of rhe
- The Python model already fixed rhe. On the phone, "rhe" had been **learned as the user's
  word**: before Session 6 it was never corrected, so it was committed many times, and
  `UserStore` counts every commit (learned after 2). Learned unknown words were never fixed.
- Workaround without a build: Settings → Your dictionary → remove "rhe".

### Decisions
1. **Learned-slip rule.** An unknown word is protected only when the user *confirmed* it:
   personal dictionary, one undone fix (`recordKeep`), or picked as typed in the bar. Words
   only typed still show as suggestions, and lose to a likelier fix (rhe: learned score −9.5
   vs "the" −8.2). Fixed words are demoted. No data migration needed.
2. **Typo list** (`assets/dict/<lang>_typos.txt`, 25k lines each, `tools/build_typos.py`):
   - generated keyboard slips of the commonest 6000 words, only short words (≤5) and
     first-letter slips (the corrector handles the rest), kept only when one word explains
     the slip 8× better than any other (neighbour keys, swaps, drops, doubles; a dropped accent
     counts as the easiest slip, which stopped "asi"→"si");
   - Wikipedia's misspelling lists (en wikipedia.dat, es Corrector ortográfico; CC BY-SA);
     Norvig's sets are test-only (licence unclear);
   - the user's own typos (set A).
   A listed non-word (or a weak/rare word) is offered as its word with cost 0.3 and bonus 0.5
   (`TYPO_LIST_COST/BONUS`; bonus 0–1 made no difference, 0.5 lets taps win more).
3. **Vocabulary.** Merriam-Webster and Urban Dictionary are copyrighted, so open equivalents:
   - **Wiktionary** via kaikki.org (CC BY-SA), streamed through `tools/wiktionary.py` (534 MB
     gz, 2 minutes, only a TSV kept): every word and inflection, minus misspellings and
     obsolete/archaic words nobody uses. Entries with no usage at all are kept only with a
     slang/informal/dated/Internet label or a subject (medicine, computing, chemistry...).
     Keeping every entry (WIKT_ALL=1) gave 1M words, 36 MB and 4× slower search: not shipped.
   - **wordfreq** (CC BY-SA 4.0 data; Reddit, Twitter, Wikipedia, news, books): read with our
     own msgpack reader (`tools/wordfreq_data.py`). It contains typos (teh, rhe), so it only
     sets frequencies, never adds words. Weight `WF_SCALE` 0.3 and only for words with no
     subtitle count (`WF_RAISE=unknown`): weight 1.0 or raising counted words cost swipes of
     modern words −6.7 and es D1 −0.8.
   - Result: en 122k → 508k words, es 200k → 689k; 18.7 / 18.6 MB (were 5 MB).
   - New flags: 32 **rare** (no frequency at all: weakly valid, never swiped) and 64 **weak**
     (added from Wiktionary with a frequency: weakly valid, swipeable). A weak word typed
     exactly stays unless a fix beats it by `RARE_TYPED_BONUS` 3.0 (0.5 changed 8% of rare
     words typed right; 1.5–3 same fix rates). Curated list words are never weak.
   - Puerto Rico section in `es_modern.txt` (wepa had become "sepa": weak words lose to close
     common words; curated words are fully valid).
4. **Rejected:** swipe "shape promotion" (best-shaped word into the first alternative): G1
   top-3 96.7→95.3, G4 86.7→84.7 for M +0.7–1.3. Removed from both Kotlin and Python.
5. Rare words are excluded from glide candidates (an obscure exact-shape word must not win).

### The user's typo log (pasted 2026-11-01, saved to tools/.cache/raw/typos/my_typos.jsonl)
- 32 lines: 24 undos, 8 picks. Many undone fixes were right (djne→done, stepls→steps,
  wordc→word, logmkn→login, teaxmer→teacher): **backspace after a fix is often the start of an
  edit**, not "keep my word". So the Session 10 rule "one undo confirms" (and the older "undo
  teaches the word at once") would have protected those typos forever.
  - Now an undo only sets `pendingKeep`; the word is kept/learned only when the restored word
    is committed unchanged (`keepUndone`, logged as "keep"). Set U scores only picks and keeps.
- "rje" was autocorrected to **"rhe"** on the phone (the learned slip had become a target).
  Fixed by the learned-slip rule, plus two-neighbour-key slips of the 300 commonest short
  words in the typo list (rje/tje/rhw → the, snd → and; weight 0.1 vs a one-key slip).
- asmr → "as Mr" and clicky → "click" are fixed by the bigger dictionary; or → of by the
  learned/confirmed rules. "Bori" added to en_modern (was bori → born).
- Clear picks added to set A: rje, everythin, eveyr, necc→necessary (all fixed now).
- Still debatable without context: opne → one (maybe "open"), cenus → Venus/cents (maybe
  "census"), reposned → reposted (maybe "responded"). With "keep" records the next log will say.

### Results (n=250 per set, D1 on held-out half only, C false changes 0% throughout)
| set | en old | en new | es old | es new |
|---|---|---|---|---|
| A your typos | 87% | 88%† | 100% | 100% |
| B sloppy | 88.2 | 86.8 | 82.1 | 84.5 |
| D1 misspellings | 79.6 | 83.2 | 92.8 | 90.8 |
| D2 hard spelling | 47.2 | 51.2 | – | – |
| E fast | 70.7 | 74.3 | 71.0 | 69.9 |
| F thumb | 75.5 | 80.1 | 76.5 | 81.2 |
| M modern typos fixed | 71.3 | 70.9 | 78.4 | 77.8 |
| L learned slips fixed | 69% | 64% | 100%* | 100% |
| V rare words typed right kept | – | 97% | – | 97% |
*es old L was 70% before the typo list (set L is new this session).
†en A has 4 more items from the typo log (all fixed); the 5 misses are the old ones (mlde,
staged, troublwithe, goolets, itsa). E/F rose with the two-key slips (rje → the).
V(uncommon) "typed right, changed" is 7–10% in en (was 2% on the old dictionary): weak words
lose to close common words more often. Its sample differs per dictionary, so it's noisy.
Glide, same words as the old dictionary (en): G1 90.7/96.7 (same), G2 87.3/96.7 (same),
G3 63.3/88.0 (+0.7 top-3), G4 66.7/84.0 (−1.3/−2.7), M 49.3/74.0 (+0.6). es: G1 83.3/99.3, G2 86.0/96.7, G4 70.0/87.3 (all same), G3 74.0/92.7 (−0.6 top-3),
M 42.0/65.3 (−2.0 top-1, +0.6 top-3).

### Files changed
- ime: `TypingSession.kt` (keep only after an undone word is committed unchanged:
  `pendingKeep`/`keepUndone`; recordKeep on picking as typed; demote any fixed word),
  `TypoRecorder.kt` (new kind "keep"). learn: `UserStore.kt` (removed unused `isLearned`).
- predict: `Suggester.kt` (confirmed-only protection, typo-list offer, weak/rare typed rule,
  TYPO_LIST_COST/BONUS, RARE_TYPED_BONUS).
- dict: `BinaryDictionary.kt` (`typos`, `typosFromAsset`, `isRare`, `isWeak`),
  `Dictionaries.kt` (loads `<lang>_typos.txt`).
- swipe: `GlideDecoder.kt` (rare words never swiped).
- assets: `en.bdic`, `es.bdic` (rebuilt), `en_typos.txt`, `es_typos.txt` (new).
- tools: `build_typos.py`, `wiktionary.py`, `wordfreq_data.py` (new); `build_dict.py`
  (Wiktionary + wordfreq merge, flags 32/64, BDIC_OUT, WF_SCALE, WF_RAISE, WIKT_ALL);
  `suggest_sim.py` (typo list, user words, weak/rare rule); `typo_bench.py` (sets L, V,
  D1_HALF, BDIC, TYPO_FILE); `glide_bench.py` (set M, BDIC, SET_BDIC); `glide_sim.py` (rare
  words not swiped); `wordlists/es_modern.txt` (Puerto Rico); `README.md`.
- `ATTRIBUTION.md`: Wiktionary, wordfreq, Wikipedia misspelling lists; typo lists CC BY-SA.
- Data (git-ignored): `tools/.cache/raw/wiktionary/*.tsv`, `tools/.cache/raw/wordfreq/`.

### Build
Not built yet. Session 9b (emoji) and everything after the 11:21 build is unbuilt. Watch:
`BinaryDictionary.typos` (`internal set`), `Suggester` (`typedNode`/`dictNode`).

### Not tested on the phone
- Memory: two 19 MB dictionaries stay cached (`Dictionaries` keeps 2). If the keyboard gets
  killed or slow to open, cache one language.
- Speed: the trie is 2× bigger (1.23M nodes); the Python model's correction went 75 → 235 ms
  per word (3×). Kotlin was a few ms, so expect roughly 10–15 ms per keystroke; watch for lag.
- APK size grows ~27 MB.

### Known gaps
- English: B −1.4 (one item of 76), L 69→64%, "mlde" now becomes "made" (listed as mode,
  but "made" is far more common). Spanish D1 −2.0 and E −1.1: more real words means more slips that
  look like words. Weak words with a close common neighbour can still be "fixed" (wepa was);
  add such words to `wordlists/*_modern.txt`.
- wordfreq data ends around 2021 (no chatgpt, skibidi): curated lists cover newer words.

### Next step
User: Build → Install APK, paste errors. Then test: "rhe " → the, "teh ", "adn ", "qeu "; type
foldable/agentic/rizz/copacetic/tachycardia by tap and swipe; a made-up name (undo the fix once,
type it again, it stays); wepa/janguear stay; keyboard opens fast after switching languages.
Then turn the typo log on again and paste it after a day of typing: "keep" lines now show which
undos were meant.

### How to catch up
1. This entry, then `.bori/handoff.md`.
2. `tools/README.md` sections "Big vocabulary" and "Typo list".
3. Benchmarks: `D1_HALF=1 TYPO_FILE=<holdout list> python3 -I tools/typo_bench.py en 250`.

## Session 9b — 2026-11-01 (Claude Code): emoji panel

### Why
The user asked for emoji before building ("before that can we add emojis"). The emoji panel
is in plan Stage 4.

### What
- 😊 key in the bottom row, between the comma (or 🌐) and space, in normal text fields only
  (not email/URL). Space shrinks by 1 (4 wide, or 3 when 🌐 shows).
- Tapping 😊 swaps the keys for the emoji panel (same stacking as the clipboard panel):
  - A scrolling grid drawn by one custom view (cheaper than ~1000 TextViews), 44 dp cells.
  - Bottom bar: ABC (back to keys), category tabs (🕘 Recent, Smileys, People, Animals,
    Food, Activities, Travel, Objects, Symbols, Flags; scrolls sideways), ⌫ (repeats when held).
  - Emoji the phone's font can't draw are dropped (`Paint.hasGlyph`), so no tofu boxes on
    older Android.
- Recents: newest first, max 32, in prefs `recent_emoji`. Not recorded in incognito or in
  no-learning fields (passwords), same rule as word learning. Opens on Recent when it has items.
- Emoji taps commit the word being typed as-is (no autocorrect), like paste. Taps get the
  usual key click/buzz.
- Opening the clipboard closes the emoji panel and vice versa. Both close on new field/hide.

### Files changed
- view: `EmojiData.kt` (new, emoji lists), `EmojiPanelView.kt` (new), `InputRootView.kt`
  (`emoji`, `emojiOpen`, `showEmoji`).
- layout: `Key.kt` (`KeyCode.Emoji`), `Layouts.kt` (😊 key in `bottomRow`).
- ime: `BoriImeService.kt` (EmojiPanelView.Listener, `toggleEmoji`, recents),
  `TypingSession.kt` (`onEmoji`; `paste` now shares a private `insert`).
- settings: `Prefs.kt` (`recentEmoji`).

### Build
Not built (nothing from Sessions 6–9b compiled yet).

### Known gaps / not tested
- No skin-tone picker (long-press) and no emoji search.
- No setting to hide the 😊 key.
- Untested: grid scrolling vs. taps inside the ScrollView, hasGlyph filtering on the phone,
  space bar width with 🌐 + 😊 on the cover screen.

### Next step
Same as Session 9: Build → Install APK, paste errors; then also tap 😊, send a few emoji,
check Recent, hold ⌫.

## Session 9 — 2026-11-01 (Claude Code): clipboard, tap-to-fix, long words and repeated letters

### Why
- The user asked for copy and paste "like Gboard" while keeping text correction working.
  I read "still aid in text correction" as Gboard's tap-a-word-to-fix as well.
- Mid-session they asked for a real analysis of correction and swipe on longer words and
  words that repeat letters, and fixes to make them more accurate.

### Clipboard (Gboard-style; plan Stage 4 "clipboard w/ expiry")
- `clipboard/ClipboardHistory.kt` (new):
  - Listens to the system clipboard. The default IME may read it on Android 10+.
  - Keeps text clips in `filesDir/clipboard.json`, on this phone only.
  - Unpinned clips expire after 1 h, with at most 30 kept. Pinned clips stay until deleted.
  - Never records while incognito or with the setting off. Never keeps clips that the
    copying app marks `EXTRA_IS_SENSITIVE` (passwords, Android 13+).
  - Turning history off clears everything except pinned clips.
- `view/ClipboardPanelView.kt` (new):
  - Replaces the keys at the same height. The keys stay laid out but invisible
    (`InputRootView` stacks them in a FrameLayout).
  - Top row: Select all / Cut / Copy / Paste / Clear, done with
    `performContextMenuAction`. Paste closes the panel; the others keep it open, so
    "Select all → Copy" works.
  - Clips are shown two per row, pinned first. Tap pastes and closes the panel.
    Long-press asks in the strip: [Pin/Unpin] [Delete].
- Suggestion strip:
  - New 📋 button on the right (⌨ while the panel is open). The slots got narrower by 48 dp.
  - Text copied in the last 2 min shows as a wide "📋 text" chip in place of the
    predictions. Tapping it pastes; it's consumed once used.
  - Typing shows normal suggestions instead.
- Settings: Privacy & clipboard → "Clipboard history" and "Offer copied text in the
  suggestion bar".

### Tap-to-fix (in-text correction)
- When the user moves the cursor onto a word (tap, or a space-bar slide that settles):
  - After 150 ms, `TypingSession.findRecorrect` reads the word around the cursor and runs
    the normal suggester on it, with no touch points and the word before it as context.
  - The strip shows the usual layout: quoted original / bold fix / alternative.
  - Picking a fix replaces the whole word (`setComposingRegion` + `commitText`), and
    picking the original changes nothing.
  - Both selection echoes are pre-registered as expected, so they aren't read as cursor
    moves.
- Any key, glide, paste or new cursor move drops it. Logged as "tapfix" in the typo log.
- Setting: Corrections → "Fix words you tap on" (default on).

### Analysis: long words and repeated letters
- New `tools/shape_bench.py` measures accuracy by length (2-4/5-7/8-10/11+) × repeats
  (double: coffee; revisit: banana; plain).
  - Glide misses are labelled pruned / geo / lm / twin.
  - `POOL=3000` samples everyday words; `CFGS` sweeps constants.
- **Glide finding:** of 118 en misses (sample from the commonest 20k), 0 were pruned, 3 were
  cut before rescoring, 107 lost on language-model rescoring and 8 were doubled-letter twins
  (teen/ten, latte/late). es: 0 pruned, 121 lm, 12 twins (mostly accents: llamé/llame).
  So the candidate search is fine; rare words lose to common ones on noisy swipes.
- **Glide sweeps, all rejected:**
  - Higher GEO_WEIGHT (3/4/5.5): G1 dropped from 89% to 86/83.5/80% and G4 from 74.5% to
    70.5/67.5/62%, for at most +3.5 on G3.
  - A new swipe-length-scaled geometry weight (`GEO_LEN_GAIN`, `GEO_L75.5ROM`,
    `GEO_L70.4AX`) helped rare 5-10 letter words by up to 12 points but cost G1 −3 and
    G4 −5.5. Gentle settings were within noise.
  - Left in glide_sim.py, off (0.0), not in Kotlin.
- **Glide fix shipped:** `TypingSession.twinFirst`.
  - Asks the decoder for 8 results (was 6).
  - If one has the same key sequence as the top word (doubled letters or accents differ,
    ñ kept distinct), it goes to the first alternative slot, one tap away.
- **Tap finding:** long words with 3 slips were never found, because the cost limit was
  2.0 for every 5+ letter word. Rushed last letters lost to deletion
  (backgrounda → background).
- **Tap fixes shipped:**
  - Length-scaled limits. Search (`SpellCorrector.maxCost`): 8-10 → 2.5, 11+ → 3.0.
    Autocorrect (`Suggester.autoCost`, Normal): 8-10 → 2.3, 11+ → 2.7.
  - `SUB_LAST_DISCOUNT` 0.15: a neighbour key (≤1.2 keys away) on the last letter of a
    5+ letter word costs 0.15 less.
  - Rejected: cheaper doubled-letter insert/delete (0.35/0.4). No gain, and set M typos
    dropped 2.7.
  - Known trade: "pupper" (a correctable variant under doggo) now autocorrects to "puppet".

### Results (n=400; C false changes 0% throughout)

| | A | B fixed / in-bar | D1 fixed / in-bar | D2 fixed / in-bar | E | F | M kept / typos fixed |
|---|---|---|---|---|---|---|---|
| en before | 85% | 86.8 / 92.6 | 79.0 / 88.8 | 44.5 / 52.8 | 71.1% | 71.4% | 97.6 / 69.9 |
| en after | 85% | 86.8 / 93.4 | 80.8 / 91.2 | 48.8 / 61.3 | 72.5% | 73.1% | 97.5 / 71.3 |
| es before | 92% | 79.3 | 86.5 | – | 69.2% | 71.2% | 97.9 / 74.8 |
| es after | 92% | 80.7 / 89.7 | 87.2 / 97.0 | – | 70.4% | 73.0% | 98.2 / 78.4 |

shape_bench, en fast typist (E), autocorrected right:

| | plain | double | revisit |
|---|---|---|---|
| 8-10 before → after | 82% → 82% | 72% → 82% | 80% → 85% |
| 11+ before → after | 75% → 85% | 75% → 88% | 60% → 80% |

The normal typist (B) at 11+ letters: plain 80% → 90%, revisit 95% → 100%.

### Files changed
- clipboard: `ClipboardHistory.kt` (new).
- view: `ClipboardPanelView.kt` (new), `InputRootView.kt` (panel stack, `showClipboard`),
  `SuggestionStripView.kt` (📋 button, clip chip, prompts over the panel).
- ime:
  - `BoriImeService.kt`: clipboard wiring, `toggleClipboard`, panel listener.
  - `TypingSession.kt`: paste, `beforeEditorAction`, clip chip, tap-to-fix, `twinFirst`,
    `contextOf`.
  - `TypoRecorder.kt`: doc only.
- predict: `SpellCorrector.kt` (maxCost by length, SUB_LAST_DISCOUNT), `Suggester.kt`
  (autoCost by length).
- settings: `Prefs.kt` (clipboardHistory, clipboardChip, tapToFix), `SettingsActivity.kt`
  (switches; "Privacy & clipboard").
- tools: `shape_bench.py` (new), `suggest_sim.py` (MAX_COST_MID/LONG, AUTO_COST_MID/VLONG,
  SUB_LAST_DISCOUNT), `glide_sim.py` (GEO_LEN_* knobs, off), `README.md`.

### Build
- Not built. Nothing from Sessions 6–9 has been compiled yet (last OK build 2026-10-27
  19:04). Watch for Kotlin errors in the new files first.

### Not tested on the phone
- All of the above, in particular:
  - Clipboard reads on Android 10+ when Bori is the default IME.
  - `performContextMenuAction` in apps like Chrome and WhatsApp.
  - Tap-to-fix in apps that echo selections oddly.
  - The 📋 button width on the cover screen.

### Known gaps
- No image clips (text only).
- The panel doesn't scroll to new clips while open.
- No underline on the tapped word.
- Glide accuracy on rare words is limited by noisy-swipe ambiguity. Next lever: real Swipe
  Trainer recordings (glide_bench set R) and learned-word boosts.

### Next step
User: Build → Install APK, paste errors. Then test:
- copy text in another app, see the chip, tap it;
- 📋 → pin a clip, Select all → Copy;
- tap a misspelled word earlier in a sentence and pick the fix;
- swipe latte/teen (twin in the left slot);
- tap-type long words with slips (intentionally, backgrounds).

### How to catch up
1. Read this entry.
2. `python3 -I tools/shape_bench.py en all 40` (~10 min) and
   `python3 -I tools/typo_bench.py en 400`.

---

## Session 8 — 2026-10-28 (Claude Code): modern words and slang (1920s–2020s), swipe logging

### Why
- The user found that "foldables" and many other modern words were not recognised. The
  frequency source is 2018 movie subtitles, which predate most of them.
- They asked for slang from the 1920s to the 2020s with accurate spelling and about 10
  spelling variations each, plus modern AI and tech terms.
- "Merb Dictionary" was read as Merriam-Webster. It is copyrighted and was not used: the lists
  are our own work.

### Decisions
- **Hand-curated lists:** `tools/wordlists/en_modern.txt` (~420 entries + variants) and
  `es_modern.txt` (~120). The format is in `tools/wordlists/README.md`:
  `word tier era variants...`.
  - Tiers 1–4 map to corpus counts 7000/1500/400/100, about ranks 5k/14k/30k/70k.
    Variants get one tier rarer.
  - Existing words are only ever raised.
  - Capitals in the main entry set the display form (ChatGPT, LLM, iPhone, TikTok).
  - The `=word` prefix forces exactly that spelling. It stops "ur"/"btw"/"ok" from showing as
    "Ur"/"BTW"/"OK" because of Hunspell names and acronyms.
  - Entries with hyphens or digits are skipped (daddy-o, g2g, wi-fi…), because they can't be
    typed as one word.
- **Only real spellings:** variants are attested spellings, inflections and stretched forms
  (lol, lolol, lool, lolz, lul, lel…). Many words have fewer than 10. None were invented:
  invented spellings would block real typo corrections.
- **Removed entries that would hurt corrections:**
  - "fo" (a typo for "of"), "ig", "tl".
  - Spanish accentless forms (movil, chevere, orale, weon). The build keeps those invalid so
    they get corrected.
  - "porfavor", so it still splits into "por favor".
- **Brand names that are also common words stay lowercase:** discord, twitch, pixel, android,
  copilot, uber.
- **New .bdic flag 16, "keep as typed":**
  - The build sets it on word-list main entries of tier ≤ 2 (`KEEP_MAX_TIER`).
  - Real-word fixes skip these words (`Suggester.chooseAutocorrect` →
    `BinaryDictionary.keepsAsTyped`; Python: `suggest_sim` checks `flags & 16`). Before, bae
    became bad, nerd became need, u became I, and ate became are.
  - Rarer entries and variants stay correctable on purpose (yall → y'all, aint → ain't,
    graxias → gracias).
  - "yea" (en) and "like" (es) were promoted to main entries.
- **New benchmark set M** (`typo_bench.load_modern`): every list word typed right must stay,
  and synthetic typos of them should be fixed.

### Results (n=400; correct words wrongly changed: 0% in every config)

| | A real | B | D1 | E fast | F thumb | M kept | M typos fixed |
|---|---|---|---|---|---|---|---|
| en before | 85% | 86.8% | 79.0% | 71.6% | 71.4% | – | – |
| en after | 85% | 86.8% | 79.0% | 71.1% | 71.4% | 97.5% | 69.3% |
| es before | 92% | 79.3% | 86.8% | 69.2% | 71.2% | – | – |
| es after | 92% | 79.3% | 86.5% | 69.2% | 71.2% | 97.9% | 74.8% |

- D2 seemed to drop from 49.8% to 44.5%. That came from a different sample: the set leaves out
  misspellings that are valid words, so the picks shift. On the *same* items, old and new
  dictionaries scored the same (49.75%, no word lost or gained;
  `$SCRATCH/diff_hard.py`-style check).
- en E lost 1 item of 218.
- The dictionaries are rebuilt: en has 122,063 words (+789), es 160,750 (+192).

### Swipe "completely broken" report
- The user reported swipe not working at all, then said it was back. No cause was found in the
  code, and the dictionary file was checked: same format, the Kotlin reader's constraints are
  met.
- DiagLog now also records:
  - `glide on=… (setting, letters layout, suggestions setting, field allows, dictionary,
    decoder)` each time it's evaluated,
  - every swipe (`glide swipe points=N`),
  - `glide decoded N words`, `glide dropped: no key geometry`,
  - dictionary loaded or FAILED to load.
- If it happens again, Copy diagnostics shows which condition switched it off.

### Files changed
- tools: `wordlists/` (new: README.md, en_modern.txt, es_modern.txt), `build_dict.py`
  (merge_wordlist, `=` prefix, F_KEEP), `suggest_sim.py` (flag 16), `typo_bench.py` (set M),
  `README.md`.
- assets: `dict/en.bdic`, `dict/es.bdic` (rebuilt).
- dict: `BinaryDictionary.kt` (keepsAsTyped). predict: `Suggester.kt` (skip real-word fix for
  kept words).
- ime: `BoriImeService.kt`, `TypingSession.kt` (glide/dictionary diagnostics, glideStatus).
- `ATTRIBUTION.md` (word-list row).

### Build
- Not built yet with these changes.

### Known gaps
- No multi-word phrases ("no cap", "bee's knees"): a dictionary entry is one word.
- No bigram data for the new words, so they don't get next-word predictions yet.
- Wiktionary (CC BY-SA, same licence as the dictionaries) could add many more slang words
  later. It needs filtering for offensive terms.
- Still open: YouTube text in split view, and the side-by-side keyboard not opening (both need
  diagnostics from the phone).

### Next step
User: Build → Install APK. Type foldables, rizz, bruh, lol, ChatGPT, bae, nerd, u, ate and check
that they stay. Add words to the lists any time; rebuild with build_dict.py and check
typo_bench (sets C and M).

### How to catch up
1. Read this entry and `tools/wordlists/README.md`.
2. `python3 -I tools/build_dict.py en` (≈2 min) and `es` (≈40 s); then
   `python3 -I tools/typo_bench.py en 400`.

---

## Session 7 — 2026-10-27 (Claude Code): split keyboard for foldables + big-screen onboarding

### Research (what Samsung and Gboard do)
- **Samsung Keyboard** on the Galaxy Fold:
  - The inner screen shows the split keyboard by default the first time; the cover screen uses
    the standard one.
  - It switches from the keyboard toolbar or Settings → Style and layout → Modes
    (Standard / Split / Floating).
  - Samsung's support pages don't describe a separate first-unfold pop-up. I could not confirm
    one, so ours is our own design in the same spirit.
- **Gboard** (beta, 2022) offers "Split layout" with duplicated keys (G and V on both halves).
- Sources:
  - samsung.com/uk/support/mobile-devices/how-to-change-the-split-keyboard-on-my-galaxy/
  - 9to5google.com/2022/06/30/gboard-split-keyboard/

### Decisions
- **Split is on by default on big screens.** A big screen means `smallestScreenWidthDp >= 600`
  (`view/BigScreen.kt`): an unfolded foldable or a tablet.
  - The cover screen and phones in landscape stay full-width, because their smallest width is
    phone-sized.
  - Unfold and refold are handled because geometry rebuilds on size/config change.
- **Split shape (`KeyGeometry.splitGap`):**
  - Each row is cut at the keyboard's middle, and the right half moves over by the gap.
  - A key straddling the middle stays left (g, v, ":"). The space bar stretches across the gap.
  - The gap's touch area is shared by the two keys beside it, so there is no dead zone.
  - No duplicated keys (Samsung style): duplicates would give one letter two positions, which
    breaks KeyProximity and the glide decoder.
  - Gap size (`KeyboardView.splitGapPx`): each half keeps ~42 dp keys, and the gap is clamped
    to 15–50% of the width. The number and phone pads never split.
- **Key-size estimates use medians now** (KeyProximity spacing, KeyboardView.keyWidthPx), so
  the jump across the gap doesn't inflate tap correction or the glide/flick/slide thresholds.
- **Asking the user:**
  - Settings shows **Step 3 "Pick your big-screen layout"** (Split (recommended) / Full width),
    only when Settings is open on a big screen.
  - Look & feel always has a "Split keyboard on big screens" switch. Both share one state.
  - The keyboard itself asks once in the suggestion bar the first time it opens on a big
    screen: "Big screen: split the keys?" [Split] [Full].
    - It's a sticky prompt: new suggestions don't hide it, but answering does.
    - It is skipped in password fields and once a choice was made anywhere
      (`Prefs.splitAsked`).
- `SuggestionStripView`: the block-word prompt became a general `showPrompt(text, yes, no,
  sticky, onAnswer)`. `showBlockPrompt` is built on it and behaves the same.

### Follow-up (same session): adjustable split spacing
- The user asked for width control too: hands differ, and keys on the very screen edge are hard
  to reach.
- `Prefs.splitGap` is the space between the halves, 10–50% of the width (default 30%).
- `Prefs.splitEdge` is the space at each screen edge, 0–20% (default 4%).
- The keys take the remaining width. On a ~673 dp Fold inner screen the defaults give ~42 dp
  keys, the same as before.
- `KeyboardView.setSplitSpacing` adds the edge margin to padH. The edge keys' touch areas still
  run to the screen edge.
- Settings → Look & feel shows both sliders (plus Reset) under the split switch while split is
  on.
- `BoriImeService` listens for these prefs (`Prefs.listen`/`unlisten`, removed in onDestroy).
  An open keyboard (the "Try it here" box) moves its keys as soon as a slider is let go.
- The old fixed 42 dp/15–50% gap formula was removed.

### Follow-up 2: keyboard won't open with two apps side by side (not solved yet)
- The user reports that on the unfolded Fold, with two apps side by side, the keyboard
  "glitches out and refuses to open".
- Nothing obvious was found in the code (`onEvaluateFullscreenMode` is already false), and
  there is no logcat here. So diagnostics were added instead of a guessed fix.
- New `ime/DiagLog.kt`:
  - It keeps the last 300 lifecycle events in memory: service create/destroy, config
    changes, window shown/hidden, start/finish input view (package + inputType only), and
    input view size changes.
  - On window hidden and on onDestroy it saves them to `files/diag_log.txt`.
  - An uncaught-exception handler writes the events plus the stack trace, then lets the
    previous handler run.
  - It never records typed text.
- Settings → Privacy has "Copy diagnostics" / "Clear" (`DiagnosticsSection`).
- **Next:** the user reproduces the problem, copies diagnostics and pastes them. Fix from
  that data.

### Follow-up 3: Settings reorganised
- The one long page had grown cluttered. It is now a start screen plus category pages.
- **Start screen:**
  - Setup steps 1–2, hidden once both are done.
  - The big-screen Step 3, only on a big screen and only until a choice is made.
  - "Try it here", then six category cards.
- **Categories:**

  | Category | Settings |
  |---|---|
  | Typing | Capitals, double-space period; key press feedback: vibrate, strength, sound, key preview |
  | Corrections & suggestions | Autocorrect level, suggestion bar switches, "Your dictionary" |
  | Swipe & gestures | Glide, trail, learn style, Trainer + Test Lab; space cursor, delete slide, flicks |
  | Layout & size | Number row, key height; big screens: split switch + spacing sliders |
  | Privacy | Incognito, "Your dictionary" |
  | Help improve Bori | Typo log, diagnostics |

- Each category page has Back, its title and a "Try it here" box at the top (`CategoryScreen`).
- Back goes from Dictionary to Corrections and from Trainer/Lab to Swipe & gestures
  (`parentOf`). The system back button follows the same order.
- Cards use `Modifier.clickable` (not the experimental `Card(onClick)`).
- Only `SettingsActivity.kt` changed. No setting keys or behaviour changed.
- Build error:
  - `SettingsActivity.kt:334:47`: "@Composable invocations can only happen from the context of
    a @Composable function". `remember { Prefs(LocalContext.current) }` read a CompositionLocal
    inside the non-composable remember lambda.
  - Fixed by reading `val context = LocalContext.current` first. No other occurrences.

### Follow-up 4: night mode + theme after folding
- The keyboard already followed the system dark mode. The user wanted it to adapt to night.
- New `ThemeMode` (theme/KeyboardTheme.kt): SYSTEM (default), LIGHT, DARK, NIGHT (dark
  19:00–07:00 by the phone's clock, checked whenever the keyboard opens).
  - `KeyboardTheme.wantsDark(context, mode)`, plus `forContext(context, night)`.
  - Stored in `Prefs.themeMode` (`theme_mode`).
- Settings: the category is renamed "Appearance & layout" and has Theme chips at the top.
- `BoriImeService.applyThemeIfNeeded` runs:
  - in onCreateInputView (a fold can rebuild the view while it stays open, and the new views
    started with the default light palette),
  - in onStartInputView,
  - in onConfigurationChanged,
  - live when the theme pref changes.
- The user reports that after folding, text in YouTube is "invisible in white". It's unclear
  whether that means our key labels or YouTube's own text box. The fix above covers the
  keyboard side. Asked the user to clarify.
- The user's answers:
  - It's the text typed in YouTube's search bar.
  - It never shows, even after space.
  - It happens only with Bori (Samsung Keyboard is fine), and only on the inner screen.
- Checked: every beginBatchEdit has a matching endBatchEdit. Composing/commit text is a plain
  String with no spans.
- No cause found yet. DiagLog now also records:
  - every onUpdateSelection (positions only),
  - EditorInfo imeOptions, privateImeOptions and initial selection,
  - whether split is active.
- The diagnostics the user pasted:
  - The app is a patched YouTube, `app.morphe.android.youtube`. Its field has
    inputType 0x2000B1 = TEXT + VARIATION_FILTER, and imeOptions = SEARCH + NO_FULLSCREEN.
  - The selection advanced 0→15 as the user typed, so the text reached the field.
  - The composing region was always -1: EditorPolicy put FILTER fields in noSuggestions, so
    every letter was committed one by one.
  - In Messages, composing worked and the text showed.
- **Fix (confirmed working on the phone by the user):** FILTER fields are no longer forced into
  noSuggestions (`EditorPolicy.from`). They compose and suggest like Gboard/Samsung do.
- The side-by-side "won't open" bug was not in this log (it wasn't reproduced). It's still open.

### Files changed
- layout: `KeyGeometry.kt` (splitGap).
- view: `BigScreen.kt` (new), `KeyboardView.kt` (splitOnBigScreens, splitGapPx, median key
  width), `SuggestionStripView.kt` (general prompt).
- predict: `KeyProximity.kt` (median spacing).
- settings: `Prefs.kt` (splitOnBigScreens, splitAsked), `SettingsActivity.kt` (BigScreenStep,
  split switch).
- ime: `BoriImeService.kt` (applies the pref, askBigScreenLayout).

### Build
- Not built. The bracket balance of all edited files was checked.

### Known gaps / untested on the phone
- The whole split layout on a real Fold: gap size, unfold/refold while the keyboard is open,
  and glide across the gap.
- The prompt only appears when the keyboard opens on a big screen. If you unfold while it is
  already open, it appears next time.
- Swipe Trainer recordings made on a split keyboard won't line up in
  `tools/glide_bench.py load_recordings` (one affine fit per axis). Record on the cover screen,
  or add split support to the fit later.
- No floating keyboard mode yet.
- The split spacing sliders are untested on the phone, including the live update while the
  test box is open.

### Next step
User: Build → Install APK (Sessions 6 and 7 have never been compiled), paste any errors. Then:
- On the Fold, unfold and open the keyboard: it should be split and ask once.
- Pick Full, then switch it back under Look & feel.
- Check that the cover screen stays normal.

### How to catch up
1. Read this entry and Session 6.
2. Split logic: `KeyGeometry` init, `KeyboardView.splitGapPx`, `BoriImeService.askBigScreenLayout`.

---

## Session 6 — 2026-10-27 (Claude Code): correction fixes, bigger benchmarks, vibration, typo log, number row

### Decisions and reasons
- **"and" became "ans".** "ans" (dict freq 85) had been *learned* from earlier uncorrected
  slips, and learned words were exempt from real-word fixes.
  - `learn/UserStore.kt` is now **DB version 3**. A new table `kept(lang, word, count)` is
    created in onCreate and in onUpgrade from <3.
  - New functions: `keptCount`, `recordKeep`, and `demote(word)` (decrements the learned
    count). New constant `KEEP_AFTER = 2`. Clearing or removing a word also clears `kept`.
  - `Suggester.chooseAutocorrect` blocks a real-word fix only for personal words or words the
    user kept (undid the fix for) at least `KEEP_AFTER` times.
  - realWordFix compares the language model *without* user boosts (`lm(..., withUser = false)`).
  - `TypingSession`: when a typed dictionary word gets fixed, it is demoted. Undo calls
    `recordKeep` for dictionary words (`Undo.typedIsWord`) and `recordWord` for others.
- **rhe→the was never applied** (it showed in the bar only). A wrong first letter cost
  0.75 + `SUB_FIRST_EXTRA` 0.3 = 1.05, which is more than the autocorrect limit of 1.0 for
  3 letters. `ot→it` cost the same, which is over the search limit for 2 letters.
  - `SUB_FIRST_EXTRA` 0.3 → **0.15**. `DEL_FIRST_EXTRA` stays at 0.3, because lowering it
    didn't help.
- **almost tjere → "three".** The pruned Tatoeba bigrams contain "almost three" but not
  "almost there", and a missing pair cost ln(1 − BIGRAM_WEIGHT) = ln 0.35.
  - New `Suggester.BIGRAM_MISSING = 0.65` (Python: `suggest_sim.BIGRAM_MISSING`).
  - Swipe uses `Suggester.languageScore` → `lm`, so it gets the change too.
- **Split / long-word tuning** (in both Kotlin and Python):
  - SPLIT_MIN_FREQ 140, SPLIT_SHORT_MIN_FREQ 170 (2-letter pieces).
  - Autocorrect cost limit 2.0 for words of 7+ letters.
- **Vibration.** `ime/Feedback.kt` was rewritten:
  - `vibrate(strength)` sends a one-shot pulse of 6–32 ms, with amplitude 30–255 when the
    phone supports amplitude control, cached per strength.
  - New `Prefs.vibrationStrength` (1–100, default 60).
  - Settings → Typing has a "Vibration strength" slider that buzzes while you drag it.
- **Typo log** (opt-in, off by default).
  - `ime/TypoRecorder.kt` writes `files/typo_log.jsonl` (cap 3000 lines).
  - It records picks and undos: typed and chosen words, the autocorrect, the previous word,
    and each tap's offset from the key centre in key units.
  - It never records when learning isn't allowed (passwords, incognito).
  - Settings has a switch plus Copy and Clear buttons, below "Your dictionary".
  - When pasted into `tools/.cache/raw/typos/my_typos.jsonl`, it becomes benchmark set U.
- **Number row** (new `Prefs.numberRow`, default **on**; switch in Settings → Look & feel).
  - `Layouts.build(..., numberRow)` adds a row of digits (with the ¹½ etc. alternatives).
  - The top letter row then drops its digit hint and digit popup.
  - `KeyboardView.heightRows` (set to 5 by the service when the row is on) makes the
    keyboard the same height on every layout. Symbols and number pads get taller keys
    instead of the keyboard jumping.
  - The Swipe Trainer and Test Lab still use the 4-row layout.
  - Digits use the LETTER kind, as on the symbols layout. They get added to KeyProximity,
    which is harmless because words contain no digits.
  - Up-flicks on q–p now type their first accent (like the middle row) when the number row
    is on.
- **Long-press "9" on o couldn't be selected.**
  - The o popup has 9 items. It was shifted left to fit, and selection used absolute x, so
    the first finger jitter picked an accent.
  - `KeyOverlayView.showPopup` now lays the row out right-to-left when it doesn't fit to the
    right, so the default sits at the key.
  - `Popup.cellFor` picks cells relative to the key centre (one cell per cell width slid), so
    the default stays selected until you slide.

### Benchmarks
- `tools/typo_bench.py` sets:

  | Set | Contents |
  |---|---|
  | A | The user's real typos. This session added rhe, tjere, ger, suild, tunjng, ans. |
  | B | Sloppy taps |
  | C | Clean typing (counts false changes) |
  | D1 | Typo lists. en: Birkbeck wikipedia.dat + Norvig spell-testset1/2. es: Spanish Wikipedia "Corrector ortográfico/Listado", CC BY-SA. |
  | D2 | Hard spelling: Birkbeck aspell, missp, holbrook |
  | E | Fast typist |
  | F | Thumb typist; the first tap of a word is the least accurate |
  | U | The user's typo log |

  - It also counts bad splits.
  - Data lives in `tools/.cache/raw/typos/` (git-ignored); sources are in `tools/README.md`.
  - The GitHub Typo Corpus was rejected: mixed licences, and the link is dead (404).
- Final results (n=400; correct words falsely changed = **0%** in every config):

  | Lang | A real | B sloppy | D1 | D2 | E fast | F thumb |
  |---|---|---|---|---|---|---|
  | en before session | 69% (with new A items) | 85.1% | 77.8% | 36.8% | 68.3% | 64.3% |
  | en now | 85% | 86.8% | 79.0% | 49.8% | 71.6% | 71.4% |
  | es before session | 92% | 77.9% | 84.5% | – | 61.6% | 61.7% |
  | es now | 92% | 79.3% | 86.8% | – | 69.2% | 71.2% |

  - The D1 baselines are from before the split tuning (D1 later gained the Norvig lists).
  - en D2 bad splits went from 16 to 2.
- **Swipe:** new `tools/glide_bench.py`.
  - Sets: G1 common, G2 Tatoeba with context, G3 mid-frequency, G4 fast, and R = Swipe
    Trainer JSON in `tools/.cache/raw/swipes/*.json`. `MISSES=G1,...` lists misses.
  - en top-1/top-3 results: G1 84.8/96.5, G2 91.0/98.5, G3 72.8/90.8, G4 66.2/83.8.
  - Sweeps of GEO_WEIGHT, W_ENDS and the new W_ALIGN (DP alignment, `glide_sim.py`, default
    0) were within noise. The decoder constants are unchanged, and W_ALIGN was not ported.
  - The remaining misses are shape-ambiguous pairs (to/top, here/he, of/off). Real swipes
    from the user are needed.

### Files changed
- predict: `Suggester.kt` (chooseAutocorrect, lm withUser, BIGRAM_MISSING, split
  thresholds), `SpellCorrector.kt` (SUB_FIRST_EXTRA, long-word autoCost).
- learn: `UserStore.kt` (DB v3, kept/demote).
- ime: `TypingSession.kt` (demote/keep, typo log hooks), `Feedback.kt` (rewritten),
  `TypoRecorder.kt` (new), `BoriImeService.kt` (numberRow, heightRows).
- layout: `Layouts.kt` (number row).
- view: `KeyboardView.kt` (heightRows), `KeyOverlayView.kt` (popup direction and relative
  selection).
- settings: `Prefs.kt` (vibrationStrength, typoLog, numberRow), `SettingsActivity.kt`
  (vibration slider, TypoLogSection, Number row switch).
- tools: `typo_bench.py`, `suggest_sim.py`, `glide_sim.py` (W_ALIGN), `glide_bench.py`
  (new), `README.md` (typo data sources).

### Build
- Not built in this session. There is no compiler here; bracket balance was checked on every
  edited Kotlin file.
- The last Bori build (before this session's code) reported OK.

### Known gaps / untested on the phone
- DB v2→v3 upgrade, vibration strength (on phones with and without amplitude control), typo
  log copy/clear, number row layout and height, popup direction near both screen edges.
- ot→it: the cost is now under the limit, but it isn't in set A. Check it on the phone.
- The bigram data is pruned, so other "almost there"-type pairs may still be missing. A better
  bigram source (with a licence check) is a possible later step.

### Next step
User: Build → Install APK, paste any errors. Then test:
- rhe→the, almost tjere→there, ot→it, and that "and" stays "and"
- the number row and its switch; the height stays the same on ?123
- long-press o → 9 (with the number row off)
- the vibration slider

Then paste the Swipe Trainer "Copy data".

### How to catch up
1. Read this entry, `.bori/handoff.md`, `.bori/plan.md`.
2. `python3 -I tools/typo_bench.py en 400` (and `es`) should reproduce the "now" rows.
3. `python3 -I tools/glide_bench.py en 400` for swipe; drop Trainer JSON into
   `tools/.cache/raw/swipes/` to get set R.
4. Keep Python constants in sync with `Suggester.kt` / `SpellCorrector.kt` / `GlideDecoder.kt`.

---

## Session 5 — 2026-10-27 (Claude Code): correction engine + Swipe Trainer (interrupted, then finished)

### What happened
- A Claude Code session started at 19:57 ("Correction engine optimisation + Swipe Trainer").
  It edited code until 20:28, then stopped without writing this log or releasing the lock.
- A second Claude Code session (the same day) took over the stale lock. It reviewed every
  file changed after 17:50 for half-done edits, fixed one bug, and wrote this entry.

### Correction engine (predict/, tools/)
- **New benchmark `tools/typo_bench.py`** (on top of `suggest_sim.py`). It has three sets:
  - A: the user's real typos, hand-labelled.
  - B: sloppy taps simulated over Tatoeba sentences.
  - C: perfect typing, which counts correct words that get changed.
  - Run it with `python3 -I tools/typo_bench.py en 300`. `CFGS='[...]'` sweeps settings.
  - `tools/.cache/diag.py` lists misses (not committed).
- **Real-word fixes** (`Suggester.realWordFix`): a valid word that is probably a slip, e.g.
  ans→and or "I cant"→can't.
  - Only near-key swaps, swapped letters or an added apostrophe count. A fix never adds or
    drops letters, so plurals are safe.
  - A rare look-alike (freq < 170) needs a margin of 1.0. A common word needs a margin of
    2.5 and bigram support from the previous word (its/it's).
- **Missed-space splits** (`Suggester.bestSplit`): 2–3 words, e.g. ofthe→"of the" and
  thebdog→"the dog".
  - A letter next to the spacebar (b n v c m) can stand in for the space, at cost 0.3.
  - Each piece must be common (freq ≥ 110), plain, and exact or a cheap fix (cost ≤ 0.7).
  - Penalty 2.5 per extra word. Splits are only tried when the typed word isn't valid.
  - `SpellCorrector.search` got `maxCostOverride` and `tapOffset`, so a fragment uses its
    own touch points.
  - `TypingSession.learn` learns each word of a split.
- Benchmark now (300 words): en A 80%, B 86.2% fixed / 92.0% in bar, C 0.00% false
  changes. es A 92%, B 78.2% / 89.1%, C 0.00%.
- Still missed in en set A: mlde→made (want mode), dtsge, staged→stage, troublwithe,
  goolets, itsa→"it's" (want "it's a").

### Swipe learning + Swipe Trainer (swipe/, ime/, learn/, settings/)
- `swipe/SwipeAdaptation.kt`: for a swipe with a known word, it measures where the path
  passed each letter. The ends count as the touch-down and lift-off points; inner letters
  use the closest pass. Offsets go up to 0.8 key; anything larger counts as noise.
- `learn/UserStore.kt`: the DB is now **version 2**, with a new table
  `key_offsets(key, sx, sy, n)` (onUpgrade creates it).
  - An 8-sample prior shrinks offsets towards zero, and offsets are capped at ±0.5 key.
  - Past 300 samples, old samples are halved.
  - Also new: `keyOffsetSampleCount`, `clearKeyOffsets`.
- `GlideDecoder.decode` takes `offsetX/offsetY` per key and shifts the key centres.
- `TypingSession`:
  - A glide committed as decoded teaches offsets (`lastGlide` capture).
  - A glide whose word was replaced by an alternative teaches nothing.
  - Learning is off in the trainer field.
- `KeyboardView` now records timestamps per path point (`pathT`, ms since down).
  `Listener.onGlide` takes `ts`. The decoder doesn't use the timestamps yet; the trainer
  exports them.
- `ime/SwipeRecorder.kt` records labelled swipes. It only records when the field is ours
  with privateImeOptions `com.bori.keyboard.SWIPE_TRAINER` and the trainer has set a target.
  - Data goes to `files/swipe_trainer.json`, at most 80 points per swipe, together with the
    key geometry.
  - "Copy data" puts the JSON on the clipboard, for pasting into a chat for tuning.
- `settings/SwipeTrainer.kt`: about 130 en and 50 es target words (contractions, s-words,
  short words, the user's own words). It shows ✓/✗ for each try and the session hit rate.
  It also has the "Learn my swipe style" switch and a reset.
- `Prefs.adaptSwipe` (default on). The Settings Gestures section has a Swipe Trainer button.

### Fix in this session
- **Trainer stale-text bug:** the keyboard used to put the decoded word in the trainer field
  as composing text, and then the trainer cleared the field from the app side. If
  `onUpdateSelection` took that change for our own cursor move, the composer kept the old
  word, and the next swipe committed "word " into the field.
  - `TypingSession.onGlide` now records the swipe, learns from it and returns early in the
    trainer field, without typing anything. The trainer already shows the result.

### Files changed (Session 5 in total)
- predict: `Suggester.kt`, `SpellCorrector.kt`
- dict: `BinaryDictionary.kt` (`bigramLogP`, `isPlainWord`, `keyOf` helpers)
- swipe: `GlideDecoder.kt`, `SwipeAdaptation.kt` (new)
- ime: `TypingSession.kt`, `BoriImeService.kt`, `SwipeRecorder.kt` (new)
- view: `KeyboardView.kt`
- learn: `UserStore.kt`
- settings: `Prefs.kt`, `SettingsActivity.kt`, `SwipeTrainer.kt` (new)
- tools: `typo_bench.py` (new), `suggest_sim.py`

### Build status / not tested
- Last Bori build OK was 14:43, before Sessions 4 and 5. **None of Sessions 4–5 is
  compiled yet.**
  - Checks done here: brackets balance, and every new symbol has a definition.
  - No compiler is available here, so expect a few compile errors.
- Not tested on the phone:
  - the DB upgrade from version 1 to 2
  - the trainer field being detected (privateImeOptions on a Compose AndroidView EditText)
  - the split corrections in the strip and their undo
  - offsets actually improving glide

### Next step
1. User: Build + Install, then paste any compile errors.
2. Type normally and check: ans→and, "I cant"→can't, ofthe→"of the", and that backspace
   undoes each.
3. Settings → Gestures → Swipe Trainer: swipe about 50 words, tap Copy data, and paste the
   JSON into the chat. Then tune the decoder on real swipes, possibly using the timestamps.
4. Then Stage 3: theme engine.

### How to catch up
- Read this entry, then `.bori/handoff.md`.
- Correction logic: `predict/Suggester.kt` (realWordFix, bestSplit). Python twin:
  `tools/suggest_sim.py`. Benchmark: `tools/typo_bench.py`.
- Swipe learning: `swipe/SwipeAdaptation.kt` → `UserStore.addKeyOffsetSamples` →
  `GlideDecoder.decode(offsetX/offsetY)`.
- Trainer: `settings/SwipeTrainer.kt` + `ime/SwipeRecorder.kt`, plus the trainer branch at
  the top of `TypingSession.onGlide`.

---

## Session 4 — 2026-10-27 (Claude Code): glide accuracy tuning

### Feedback from the phone
Stage 2 built and works. The user said glide is "not bad, not super accurate".
- They asked to improve swipe accuracy before Stage 3.
- They reported every kind of miss: wrong similar word, short words fail, long words
  fail, and the right word not in the bar.
- Concrete case: "food" swiped as "did".

### Diagnosis
- The synthetic swipes in `tools/glide_sim.py` were too clean, so the simulator scored
  "food" correctly.
- Added `synth_real()`, a "realistic" mode:
  - per-swipe finger offset σ 0.15 key
  - aim noise 0.2–0.35
  - overshoot of up to 0.4 key past the last letter
  - corner smoothing window 2–6
  - jitter 0.04
- With it, the old decoder dropped to en 78.2% top-1 / 91.7% top-3, which matches the
  user's experience.

### Changes (sweeps in tools/.cache/real.py, not committed)
- **New endpoint term** `W_ENDS = 2.0`: distance of the swipe start to the first letter
  plus the swipe end to the last letter. +4 points top-1 on its own; fixes food→did and
  keyboard→keyboards.
- `RADIUS` 1.1 → **1.3** (fewer right words filtered out; "not in bar" fix).
- `MAX_GEO_KEEP` 40 → **80**.
- `GEO_WEIGHT` 4.0 → **2.0**. Checked on mid-frequency words (ranks 2000–2400) too, so
  the language model doesn't overrule rarer words: 2.0 ≈ 4.0 at top-1/top-3 and better at
  top-5; 1.5 is worse.
- Tried and rejected: one "missed letter" allowance for long words (no gain, 2.5× slower);
  W_SHAPE/W_LOCATION changes.
- `predict/KeyProximity.keyWidth` is now the mean spacing between neighbouring letter
  centres (it was the mean hit width, inflated by edge keys' wider touch zones). This
  matches the simulator's key units. It also slightly affects tap-correction distances
  (more accurate).
- `swipe/SyntheticSwipe.kt` has a realistic mode, and the Test Lab chips are now
  "Realistic swipes" (default) and "Clean swipes".

### Results (simulator, top-1 / top-3)
| | before | after |
|---|---|---|
| en realistic, 400 common | 78.2 / 91.7 | 85.5 / 96.0 |
| en clean, 600 common | 86.7 / 97.7 | 91.0 / 99.3 |
| es realistic, 400 common | (n/a) | 84.0 / 95.5 |
| es clean, 400 common | 88.5 / 98.0 | 90.5 / 99.0 |
| en realistic, mid-frequency | 72.3 / 89.0 (geo 4) | 72.0 / 89.0 (top-5 91.7 → 94.0) |

Still ambiguous by geometry: here/he, three/the (the h→r→e path passes along h→e). The
second word is shown in the bar.

### Files changed
`swipe/GlideDecoder.kt`, `swipe/SyntheticSwipe.kt`, `predict/KeyProximity.kt`,
`settings/SwipeTestLab.kt`, `tools/glide_sim.py`.

### Next step
1. User: Build + Install, swipe normally, and run the Test Lab (Realistic). Report words
   that still fail.
2. Further ideas if needed:
   - Use touch timestamps: slow-downs and pauses mark letters (needs time in the path;
     not recorded yet).
   - Per-user adaptation: learn the user's finger offset from accepted glides.
   - Context look-ahead.
3. Then Stage 3: theme engine.

---

## Session 3 — 2026-10-27 (Claude Code): Stage 2 glide typing + slide gestures

### Status coming in
- The user confirmed that the Session 2 fix works ("doing good"): the keyboard sits at the
  bottom and Stage 1 runs on the phone.
- Asked which stage to do next, the user chose Stage 2 (glide) before Stage 3 (themes).

### What was built (code written, NOT yet built)
**Decoder: `swipe/GlideDecoder.kt`**, our own geometric decoder, pure Kotlin, no model files.
1. Candidates come from a trie walk.
   - The first letter must be within 1.3 key sizes of the swipe start, and the last letter
     within 1.3 of the swipe end.
   - Inner letters must be passed within 1.1 key sizes, in order. This uses a greedy
     earliest match, with a precomputed `nextNear[key][index]` table so each trie step is O(1).
   - Apostrophes and hyphens (no key) are free, so "dont" swipes give "don't". Doubled
     letters need no movement. Accented letters use their base key.
   - Offensive and blocked words are skipped.
2. Geometry: the user path is thinned to one point per 0.25 key, and both paths are
   resampled to 40 points.
   - Score = −(6·mean location distance + 5·mean normalised-shape distance).
3. The top 40 by geometry are rescored: 4·geo + `Suggester.languageScore` (previous-word
   bigram, unigram, user boosts).

**Tuning:** `tools/glide_sim.py` is the Python reference model plus a synthetic swipe
generator (gaussian aim error σ 0.22 key, moving-average corner cutting, jitter).
- Results on the 600 commonest words: en 86.7% top-1 / 97.7% top-3; es (400 words)
  88.5% / 98%.
- Sloppy swipes (σ 0.3) give 81.5% / 94.8%.
- Tried and dropped: a path-length penalty and a turn-point penalty. Neither helped on
  synthetic data. They could be revisited with real swipes.
- Most top-1 misses are inherently ambiguous pairs (her/here, to/too, of/off,
  well/we'll, were/we're); context and the strip alternatives handle them.

**Gestures: `view/KeyboardView.kt`**, rewritten as a per-finger state machine with modes
KEY / GLIDE / SPACE_CURSOR / DELETE_SLIDE, so the gestures never fight each other.
- Glide starts when a single finger on a letter moves more than 0.6 key widths. It only
  works when the service sets `glideEnabled` (letters layout, suggestions on, dictionary
  loaded, setting on). Extra fingers are ignored during a glide. Batched historical
  touch points are recorded.
- Spacebar slide: more than 0.6 key widths of horizontal movement enters cursor mode,
  1 character per 0.45 key widths, sent as DPAD key events. Space long-press (the IME
  picker) went up to 600 ms so it doesn't fire before a slow slide.
- Delete slide: more than 0.6 key widths to the left; each 0.9 key widths selects one
  more word, and releasing deletes the selection. The touch-down still deletes 1 character
  first (delete acts on press).
- Key flicks: a quick (<300 ms), mostly vertical upward stroke of 0.5–1.6 key heights on
  a letter types its hint or first popup character (the digit on the top row). The
  glide/flick decision waits while a stroke still looks like a flick.
- `onSizeChanged` cancels all gestures (fold/unfold/rotation), and the geometry is
  rebuilt. `KeyProximity` is rebuilt whenever the geometry object changes, so the decoder
  always uses the current key positions.

**Trail: `view/KeyOverlayView.kt`**: a polyline of the last 120 points, older segments
thinner and fainter, fading out over 250 ms after lift-off, in the accent colour.

**Session: `ime/TypingSession.kt`**
- `onGlide(xs, ys, count, case)`. If a word is being composed, it is committed with a
  space first. Otherwise a space is added automatically if the previous character isn't
  whitespace or an opening character (`([{"'¿¡/@#-_`).
- The top word becomes composing text (`glideActive`), with alternatives in the strip
  [alt1 | best | alt2]. Shift state at glide start sets the case: ON = capitalised,
  LOCKED = all caps.
- While `glideActive`: no autocorrect; backspace removes the whole word; a letter tap
  commits the word plus a space and starts a new word; space or punctuation commit it.
- `moveCursor(steps)`, `deleteSlide(words)` (anchor from `getExtractedText`, selection by
  whitespace word boundaries, up to 2000 characters back), `deleteSlideEnd()`.
- All `composer.reset()` calls now go through `resetComposer()`, so the glide state is
  cleared too.

**Other**
- `swipe/SyntheticSwipe.kt` (Kotlin copy of the generator).
- `settings/SwipeTestLab.kt` (Settings → Gestures → Swipe Test Lab): 500 common words,
  Normal or Sloppy, reports top-1/top-3, ms per word, and the misses. It deliberately
  doesn't call `UserStore.load` (the store is shared with the keyboard).
- Settings now has a Gestures section: glide typing, swipe trail, spacebar cursor,
  delete slide, key flicks.
- Small API additions: `KeyProximity.letters`, `Suggester.languageScore` and
  `isBlockedWord`, `KeyboardView.Listener.onGlide/onCursorMove/onDeleteSlide/onDeleteSlideEnd`.

### Fold-aware geometry
Handled by size-driven rebuilds: `onSizeChanged` cancels gestures and rebuilds `KeyGeometry`.
The IME recreates its input view on configuration changes. `androidx.window`
WindowInfoTracker was not used: an IME service isn't an Activity. Revisit it in Stage 4 for
the split layout.

### Known gaps / risks
- Stage 2 is not compiled yet; expect a round of fixes.
- Real-swipe accuracy is unknown. The synthetic generator may be kinder than real fingers;
  tune with the Test Lab and real use.
- Delete slide deletes one character on touch-down before the slide starts.
- Cursor slide uses DPAD events: it works in normal text fields but some apps (terminal
  emulators, games) may ignore them.
- Glide doesn't yet learn per-user swipe habits, and there is no bigram look-ahead.

### Next step
1. User: Build + Install; paste errors → fix.
2. Try gliding in en/es, glide + punctuation, glide then tap, the spacebar and delete
   slides, and flicks on the top row. Run the Swipe Test Lab and report its numbers.
3. Then Stage 3: theme engine.

---

## Session 2 — 2026-10-27 (Claude Code): full-screen keyboard bug

### What happened
- The user reported that Stage 1 builds and installs and the keyboard works, but on
  launch it "shoots up to the top of the screen and becomes unusable".
- **Cause:** `InputRootView` (a FrameLayout with WRAP_CONTENT height) holds
  `KeyOverlayView` with MATCH_PARENT height. On the first measuring pass a plain View
  takes all the height it is offered (AT_MOST = the whole screen). The FrameLayout counts
  that, so the IME window became full-screen and the keys sat at the top.
- **Fix:** `view/KeyOverlayView.kt` overrides `onMeasure` and reports 0 height unless given
  EXACTLY. FrameLayout then re-measures it at the keyboard's real size, so previews still work.

### Files changed
- `app/src/main/java/com/bori/keyboard/view/KeyOverlayView.kt` (added `onMeasure`)

### Status / next step
- Stage 1 confirmed building. Fix not yet built: user to Build + Install and check that the
  keyboard sits at the bottom, key previews still appear above the top row, and rotation
  and fold/unfold keep it at the bottom.
- Then: test Stage 1 behaviour (corrections, undo, predictions, learning), then Stage 2.

### Rule for future views
Any overlay or decorative View added to the input view with MATCH_PARENT height must not
take height in AT_MOST/UNSPECIFIED mode, or the keyboard window will fill the screen.

---

## Session 1 — 2026-10-27 (Claude Code)

### How to catch up
1. Read `.bori/plan.md` (stages + checklist) and `.bori/handoff.md` (latest build result).
2. Skim this entry's "Next step" and "Known gaps".
3. Key code to read first: `ime/BoriImeService.kt` (keys, layouts, shift) →
   `ime/TypingSession.kt` (all text editing) → `predict/Suggester.kt` + `SpellCorrector.kt`.

### Goal and decisions made by the user
- Open-source, fully offline Android keyboard to replace Samsung Keyboard: Gboard-quality
  glide typing, much better spell check/prediction than Samsung, full theme engine,
  foldable-aware. Multi-stage project.
- **Name:** Bori Keyboard, package `com.bori.keyboard`.
- **Our own code only.** CleverKeys, HeliBoard, FlorisBoard, FUTO etc. are inspiration only;
  no code copied. Open data (word lists) imported with attribution.
- **Languages v1:** English + Spanish.
- **License:** GPL-3.0-or-later (changeable before first release). Dictionary files are
  CC BY-SA 4.0 (derived data, see `ATTRIBUTION.md`).
- **Constraints found:** Bori Code can't compile C++/NDK → FUTO Swipe library ruled out;
  glide decoder will be our own pure-Kotlin geometric decoder (Stage 2). No INTERNET
  permission. CleverKeys was rejected as a fork base (428 Kotlin files, Gradle codegen tasks
  Bori doesn't run).

### Stage plan (see `.bori/plan.md`)
0 skeleton + tap keyboard ✅ · 1 dictionaries/prediction/spell check (written, not built) ·
2 glide typing + slide gestures + fold geometry · 3 theme engine · 4 clipboard, one-handed/
split/floating, voice, emoji · 5 F-Droid release · 6 optional neural decoder/LM, more languages.

### Stage 0 — skeleton + tap keyboard (built and installed ✅)
- Template renamed from `com.example.myapp`; manifest moved to `app/src/main/AndroidManifest.xml`.
- Build: Kotlin 2.0.21 + Compose + serialization plugins, minSdk 26, targetSdk 35, Java 17,
  Compose BOM 2024.10.00, `androidx.window` (for Stage 2 fold events).
- IME: `ime/BoriImeService.kt`, `res/xml/method.xml` (subtypes en_US `layout=qwerty`,
  es_ES `layout=qwerty_es`), BIND_INPUT_METHOD service.
- Layouts in Kotlin (`layout/Layouts.kt`), not JSON (simpler, type-checked): QWERTY en,
  QWERTY+ñ es, two symbol pages, number pad, phone pad; email/URL bottom-row variants
  (`@`, `/`, `.com` popup). All layouts have 4 rows so height stays constant.
- `layout/KeyGeometry.kt`: rebuilt on every size change; hit rects tile the keyboard (no
  dead zones).
- `view/KeyboardView.kt`: Canvas drawing, multi-touch rollover, long-press accent popups,
  delete auto-repeat, shift (double-tap = caps lock), key previews.
  `view/KeyOverlayView.kt` draws previews/popups above the top row (no PopupWindow).
- `ime/EditorPolicy.kt`: password/number/phone/email/URL handling, no-suggestion and
  no-learning flags, enter action labels (Go/Search/Send/Next/Done).
- `theme/KeyboardTheme.kt`: placeholder light/dark palettes + Material You accent (API 31+);
  Stage 3 replaces it with the JSON ThemeSpec engine.
- `settings/SettingsActivity.kt` (Compose): setup wizard (enable → select), test field,
  typing prefs, key-height slider. `settings/Prefs.kt` (SharedPreferences).

**Build error fixed:** `aapt2: resource style/Theme.AppCompat.Light.NoActionBar not found`.
Our resources never referenced it. Cause: Compose needs newer AndroidX, so Bori drops its
built-in AndroidX set (which provided AppCompat) yet still links against Theme.AppCompat.
Fix: declare `androidx.appcompat:appcompat:1.7.0` in `app/build.gradle.kts`.
Harmless log noise after that: D8 "Kotlin metadata" warnings, and a stale
`com.example.myapp.MainActivity` class in Bori's build cache (not in project or manifest).

### Stage 1 — dictionaries, prediction, spell check (code written, NOT yet built)
**Data pipeline (`tools/`, Python stdlib only, raw downloads in git-ignored `tools/.cache/raw/`):**
- `hunspell_expand.py`: our own Hunspell affix expander (PFX/SFX, cross products, one level
  of continuation suffixes, UTF-8 flags, NOSUGGEST). en → 124k forms, es → 657k forms.
- `build_dict.py` → `app/src/main/assets/dict/{en,es}.bdic` (~5.1 MB each; en 121k words,
  es 160k words). Format: our own structure-of-arrays trie with log-scale frequencies
  (1–255), capitalisation flags, offensive (NOSUGGEST) flag, and next-word bigrams
  (quantised −ln P × 16). Full byte layout documented at the top of the file.
  - Frequencies: FrequencyWords/OpenSubtitles 2018 (CC BY-SA 4.0), filtered by Hunspell.
  - Bigrams: Tatoeba sentences (CC BY 2.0 FR).
- Data problems found and fixed:
  1. OpenSubtitles splits contractions ("don" + "t") → contraction counts come from
     Tatoeba, scaled by median ratio; split fragments reduced.
  2. "i" stored lowercase → display override to "I" (and "I'm", "I'll").
  3. Tatoeba overuses names (Tom, Mary…) → excluded as predicted words.
  4. Accent-less Spanish misspellings ("tambien", "aqui") slipped in via the common-slang
     rule → excluded so they get corrected.
- `verify_dict.py`: reads a .bdic back, checks structure, prints spot checks.
- `suggest_sim.py`: Python reference model of the Kotlin corrector + ranker; used to tune
  constants. **Keep it in sync with `SpellCorrector.kt` / `Suggester.kt`.**

**App code:**
- `dict/BinaryDictionary.kt` (bulk-loads arrays; `wordOf` restores capitalisation),
  `dict/Dictionaries.kt` (background load, keeps 2 languages in memory).
- `predict/SpellCorrector.kt`: weighted Damerau-Levenshtein walk over the trie with
  pruning. Costs: accent 0.15, first-letter substitution +0.3, apostrophe insert 0.1,
  doubled-letter insert 0.5, other insert 1.0, deletes 0.9 (first +0.3, doubled 0.5,
  apostrophe 0.3), transposition 0.65; substitution from touch distance:
  clamp(0.35 + 0.4·d, 0.45, 1.1), d in key sizes (`predict/KeyProximity.kt`). Max cost by
  length: ≤2 → 1.0, ≤4 → 1.3, else 2.0. Also finds prefix nodes for completions.
- `predict/Suggester.kt`: score = ln P(word | prev) (0.65 bigram / 0.35 unigram mix) + user
  boosts (0.6·ln(1+count), pair 1.0·ln(1+count)) − 4.5 × edit cost; completions −1.5 −0.1
  per extra char. Autocorrect: only to the single best-ranked correction, within cost
  limit (Normal: ≤2 chars 0.6, 3 → 1.0, 4 → 1.3, else 1.6; Mild ×0.65, Aggressive ×1.3);
  never by just dropping trailing letters of a ≤4-char word ("hel"→"he"); valid words are
  only re-cased ("i"→"I"). ALL-CAPS typing isn't corrected.
- `predict/Composer.kt`: composing word + per-char touch points, applies typed case.
- `learn/UserStore.kt`: plain SQLite (`user.db`, no Room/KSP) — learned words (valid after
  2 uses), word pairs, personal dictionary, blocked words; process-wide singleton shared with
  settings; in-memory maps, writes on a background thread.
- `ime/TypingSession.kt`: every text edit goes through here. Composing text, autocorrect
  on space/punctuation, backspace-undo of autocorrect (restores typed word, learns it,
  suppresses re-correction), "word |" + "." → "word. |" after a suggestion pick,
  double-space period, resume composing after backspacing into a word, learning (off in
  incognito/password/no-learning fields). **Cursor tracking:** keeps an expected cursor
  plus recent expected positions so the editor echoing our edits isn't mistaken for the
  user moving the cursor; a reported composing span matching ours re-syncs.
- `view/SuggestionStripView.kt`: ⚙ (🕶 when not learning) + 3 slots [2nd | best | 3rd];
  typed literal shown in quotes when it would be corrected; long-press → "Stop
  suggesting?" Block/✕.
- `KeyboardView.Listener.onKey(key, x, y)` now passes touch points.
- Settings: show suggestions, next-word predictions, block offensive words, autocorrect
  level (Off/Mild/Normal/Aggressive), incognito, "Your dictionary" screen (add per
  language, remove, forget learned, unblock, forget everything).
- `ATTRIBUTION.md`, `tools/README.md` added.

**Simulator results (Normal):** teh/thw→the, yuo→you, dont→don't, im→I'm, recieve→receive,
definately→definitely, adress→address, untill→until, tomorow→tomorrow, becuase→because;
cancion→canción, espana→España, tambien→también, aqui→aquí, manana→mañana,
graicas→gracias, nesesito→necesito, hcaer→hacer. Left alone: hel, thx, its, were, goin.
Kotlin should be ~1–5 ms per keystroke (Python 5–100 ms).

### Known gaps / risks
- Stage 1 has never been compiled — expect a round of compile fixes.
- Learning saves typos after 2 uses if autocorrect doesn't catch them (by design).
- `estoi` → `esto` rather than `estoy` (tuning item).
- "don" keeps a high frequency (it's a real word); watch for it as a completion of "do".
- No system spell-checker service (red underlines in other apps) yet — possible later add.
- Suggestions computed synchronously on the main thread; move to background if slow.
- Emoji deletion (2 code units) desyncs the expected cursor; handled as an external move
  (harmless).
- Nothing committed to git yet (repo initialised, user hasn't asked for a commit).

### Next step
1. User: Build + Install in Bori Code; paste any errors → fix.
2. Test on phone: messy typing in en/es, backspace after autocorrect, predictions,
   password field (no suggestions/learning), "Your dictionary" screen.
3. Then Stage 2: glide typing (gesture arbiter, own geometric decoder, composing +
   alternatives, apostrophes, auto-space), spacebar cursor, delete slide, key flicks,
   fold-aware geometry, Swipe Test Lab.
