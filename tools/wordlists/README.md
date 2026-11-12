# Extra word lists

Hand-curated words the frequency corpus (OpenSubtitles 2018) misses or barely has: slang from the
1920s to the 2020s, internet and texting words, AI and tech terms, app and device names.
Written for Bori Keyboard (GPL-3.0, same as the code); no third-party dictionary was copied.

Files (all `<lang>_*.txt` are merged, in name order; see tools/wordlist_files.py):
- `<lang>_modern.txt`: slang of every decade, texting, first AI/tech words (Session 8).
- `en_ai.txt`, `en_phone.txt`, `en_brands.txt`, `en_slang.txt` (Session 40): AI models and terms,
  phone settings/features/problems, brands and apps, today's slang incl. rude and sexual slang
  (user choice: plain words, not flagged offensive).
- `en_abbrev.txt` (Session 46): work/texting abbreviations that must never be "fixed" (ETA, ASAP,
  RSVP, TBD, WFH…). Names that are also words (RIP, MIA, COB, ID) are left out.
- `es_pr.txt` (Puerto Rico first: slang, food, towns, artists), `es_slang.txt` (Caribbean,
  Mexico, South America, Spain, Spanglish, texting), `es_tech.txt` (AI, phone, brands).
- `<lang>_phrases.txt`: not words but everyday texting sentences, one per line. ngram_count.py
  counts each PHRASE_WEIGHT times (default 100) so the new words get contexts ("I asked ChatGPT");
  every 10th line is held out (`HOLDOUT=phrases python3 -I tools/predict_bench.py en`).

Brand rule: capitals only for words that are only ever a name (iPhone, WhatsApp). A brand that is
also an everyday word (apple, pixel, galaxy, target, zoom, claro, mercado) stays lowercase,
or every normal use of the word would be capitalised.

Word-list format, one entry per line, `#` starts a comment:

    word tier era variant variant ...

- **word**: the main spelling. Capitals give the display form (`ChatGPT`, `LLM`); keys are lowercase.
  A word the dictionary already shows capitalised keeps that (a name), unless prefixed with `=`:
  `=ur` and `=btw` stay lowercase instead of "Ur" and "BTW". `=` works on variants too.
- **tier**: how common it is today. 1 everyday (≈ rank 5,000 in the corpus), 2 common (≈ 14,000),
  3 known (≈ 30,000), 4 rare (≈ 70,000). A word already in the dictionary is only ever raised.
- **era**: the decade it took off (`1920s` … `2020s`) or `tech`, `ai`, `brand`, `text`. Documentation only.
- **variants**: other real spellings, inflections and stretched forms people actually type (one tier
  rarer than the main word). Only attested spellings: invented ones would stop real typos from being
  corrected.

`tools/build_dict.py` merges these after the corpus; rebuild with `python3 -I tools/build_dict.py en`
(and `es`), then check `tools/typo_bench.py` (set C false changes must stay 0%).
