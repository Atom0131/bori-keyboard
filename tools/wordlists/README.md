# Extra word lists

Hand-curated words the frequency corpus (OpenSubtitles 2018) misses or barely has: slang from the
1920s to the 2020s, internet and texting words, AI and tech terms, app and device names.
Written for Bori Keyboard (GPL-3.0, same as the code); no third-party dictionary was copied.

`<lang>_modern.txt`, one entry per line, `#` starts a comment:

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
