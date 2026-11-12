<img src="tools/icon_preview.png" width="96" align="right" alt="Bori Keyboard icon">

# Bori Keyboard

A free, open-source, offline-first Android keyboard. Made in Puerto Rico. 🇵🇷

- **Glide typing** with its own pure-Kotlin decoder, plus a Swipe Trainer that learns your swipe style
- **Spell correction and prediction** for **English and Spanish**: large dictionaries,
  sentence-aware next-word prediction, autocorrect with one-tap undo, learns your words
- **Emoji panel** with selectable open emoji styles (Twemoji, Blobs, Fluent, OpenMoji, Noto,
  EmojiOne 2, Firefox), and the Puerto Rico flag with its light-blue triangle
- **Clipboard history** (text and pictures, search, pins, keep for an hour to forever), spacebar cursor slide, delete-word slide, key flicks, number row
- **Foldable-aware**: split keyboard on big screens
- Customisable swipe trail (colours, rainbow, thickness), light/dark themes
- Themes: 21 built-in looks (neon, glass, scenes, Borinquen, high contrast…) with live
  previews, key borders on/off, raised/flat/outline/floating keys, five key shapes and 14
  bundled open fonts
- **Nothing you type leaves the phone.** The only online feature is optional GIF search
  (Powered by KLIPY), off until you turn it on in Settings → GIFs with your own free API key.

## Building
A normal Android Gradle project (Kotlin, Compose for Settings, Views for the keyboard).
Developed and built on the phone with Bori - Code. Dictionary and emoji tooling is in `tools/`
(see `tools/README.md`).

## License
GPL-3.0-or-later (see `LICENSE`). Word lists, dictionaries and emoji art come from open
projects under their own licenses; see `ATTRIBUTION.md`.
