# Bori Keyboard settings: style guide

The user approved this style on 2026-11-12, after Sessions 33–34 ("the settings look amazing").
Every new or changed settings page must follow it. It is based on Android's settings design guide
(developer.android.com/design/ui/mobile/guides/patterns/settings) plus settings-UX articles from
Toptal, ScreensDesign and Eleken.

## Rules
1. **Lists, not cards.** Use rows inside rounded groups, never big cards with descriptions.
2. **The second line shows the current state**, not a feature list ("Autocorrect: Normal",
   "On · Rainbow · 6 dp", "Off"). Add a short description only when the label alone isn't clear.
3. **Short pages.** A page shows its main rows and fits on about one phone screen. Anything with
   several controls (colour swatches, many sliders, lists) goes **one level down**, behind a
   NavRow whose status line summarises it. This applies at every level.
4. **Small groups with headings.** The group title is a label in the primary colour. Keep 2–5
   rows per group.
5. **Labels:**
   - Short, with the important word first.
   - Impersonal: "Incognito", not "Don't learn my words".
   - No "Don't" or "Never", and no generic verbs (Set, Change, Manage, Use).
   - Don't repeat the page title.
   - A row that opens a page uses the same words as that page's title.
6. **Dependencies:** options that need a switch sit right under it, and only show (or are
   disabled) while it is on. Examples: Vibration → Strength; Glide typing → Trail, Practice.
7. **Row types:**

   | Use | For |
   |---|---|
   | SwitchRow (the whole row toggles) | on/off |
   | ChoiceRow (radio dialog that closes on pick) | one of several |
   | SliderRow (saves on release unless live feedback matters) | ranges |
   | The ⓘ InfoButton on the row | long explanations (never paragraphs between settings) |
   | A confirm dialog | risky actions (delete, forget all) |
8. **Live preview:** pages that change the look show a KeyboardPreview at the top
   (`currentPreviewTheme` in ThemesScreen.kt). Every page has the "Try the keyboard here" bar
   pinned at the bottom; it comes with SettingsPage. Use `tryBar = false` only for pages with
   their own typing box.
9. **Width:** content is at most 640 dp wide, centred, so unfolded screens don't stretch it.
10. **Icons:** only on the start page rows, from settings/SettingsIcons.kt (path data). Don't add
    an icon library: material-icons-extended is too big to compile on the phone.

## Building blocks (settings/SettingsUi.kt)
- **Page and layout:** SettingsPage(title, onBack, tryBar), Group(title), RowDivider,
  RowContent { custom }, Footnote.
- **Rows:**
  - NavRow(title, status, icon, enabled)
  - SwitchRow(title, checked, status, enabled, info)
  - ChoiceRow(title, choices, selected, status)
  - SliderRow(title, value, valueText, range, onChange, onDone)
- **Explanations:** InfoButton.

Older code may still call CategoryScreen, PrefSwitch, Note or SectionTitle; they are kept as
wrappers. Prefer the new blocks.

## Compose mistakes that broke Bori builds
- Read `LocalContext.current` (or any `Local….current`) **outside** `remember { }`:
  `val context = LocalContext.current; val prefs = remember { Prefs(context) }`. Inside
  `remember` it fails with "@Composable invocations can only happen from the context of a
  @Composable function" (Session 34). Check with `rg -n "remember \{[^}]*Local\w+\.current"`.
- No `/*` inside Kotlin comments, because Kotlin nests comments (Session 22). Check with
  `rg -n '\S/\*'`.

## Map (start page → pages → deeper pages)
- **Theme** → Gallery, Dark mode, Key borders, Keys & letters, Background, Effects & lighting,
  Design your own / Edit (the editor's colour picker opens in a dialog).
- **Layout & size** → Fit to my thumbs (calibration), Key height, Other ways of holding, Number
  row, Split keyboard.
- **Typing** → 3 switches, Sound & vibration.
- **Corrections** → Autocorrect, Suggestions, Your dictionary, Touch heat map.
- **Swipe & gestures** → Glide typing, Swipe trail, Learn my style, Practice (Trainer, Test Lab),
  Gestures.
- **Emoji & GIFs** → Emoji style, GIF search.
- **Clipboard & privacy** → Incognito, Clipboard history, Keep copies for, Suggest copied text.
- **Help improve Bori** → Typo log, Diagnostics.

Routes live in SettingsActivity (`when (screen)`), and Back targets in `parentOf`.
