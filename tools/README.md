# Dictionary tools

Python 3 (stdlib only). Run from the project root; outputs go to `app/src/main/assets/dict/`.

1. Download sources into `tools/.cache/raw/` (git-ignored):
   - `en_freq.txt`, `es_freq.txt`: FrequencyWords `content/2018/<lang>/<lang>_full.txt`
   - `<lang>_index.dic`, `<lang>_index.aff`, `<lang>_license`: wooorm/dictionaries `dictionaries/<lang>/`
   - `eng_sentences.tsv.bz2`, `spa_sentences.tsv.bz2`: https://downloads.tatoeba.org/exports/per_language/
2. `python3 -I tools/build_dict.py en` (and `es`): builds `<lang>.bdic`.
   It also merges `tools/wordlists/<lang>_modern.txt` (hand-curated slang 1920s–2020s,
   texting, AI/tech words and app names the 2018 subtitles miss; format in
   `tools/wordlists/README.md`). After editing a list, rebuild and rerun `typo_bench.py`
   (set M checks the list's own words; set C must stay at 0% false changes).
3. `python3 -I tools/verify_dict.py app/src/main/assets/dict/en.bdic the dont i'm`: checks the
   file and prints spot checks.
4. `python3 -I tools/suggest_sim.py en [typo[:prevword] ...]`: reference model of the app's
   corrector and ranker, for tuning constants. Keep it in sync with
   `predict/SpellCorrector.kt` and `predict/Suggester.kt`.

5. `python3 -I tools/glide_sim.py en [N]` (or `... en hello world`): reference model of the
   glide decoder (`swipe/GlideDecoder.kt`) plus the synthetic swipe generator used by the
   in-app Swipe Test Lab; prints top-1/top-3 accuracy. Keep constants in sync.

`hunspell_expand.py` is a small Hunspell affix expander used by the builder.
See `ATTRIBUTION.md` for data licenses.

## Big vocabulary: Wiktionary + wordfreq

`build_dict.py` also merges, when present:
- `tools/.cache/raw/wiktionary/<lang>_words.tsv`: every Wiktionary word and inflection, made by
  streaming kaikki.org's data through `wiktionary.py` (the commands are in its docstring; about
  2 minutes, nothing large is stored). Misspellings are dropped; obsolete/archaic words only
  stay if wordfreq has seen them. Entries with no usage at all are kept only when labelled
  slang/informal/dated/Internet... or with a subject (medicine, computing, chemistry...).
  `WIKT_ALL=1` keeps every entry (1M English words, 36 MB, 4x slower search; not shipped).
- `tools/.cache/raw/wordfreq/wordfreq/data/large_<lang>.msgpack.gz`: wordfreq's frequency
  lists, taken out of the wheel on PyPI (`https://pypi.org/pypi/wordfreq/json` → the .whl →
  unzip only those two files), read by `wordfreq_data.py` (own msgpack reader, the package
  isn't installed). It raises counts (weight `WF_SCALE`) but never adds words: it also counts
  typos like "teh".
- Words with no frequency at all get flag 32 (rare): weakly valid when typed (a fix must beat
  them by `RARE_TYPED_BONUS`), never swiped, and a listed typo that is a rare word is fixed.
- Trial builds: `BDIC_OUT=<dir>`; benchmark them with `BDIC=<dir>/{lang}.bdic` (typo_bench,
  glide_bench) and compare on the same words with `SET_BDIC=<old>/{lang}.bdic` (glide_bench).

## Typo benchmark (`typo_bench.py`)

`python3 -I tools/typo_bench.py en 400` scores the corrector model in `suggest_sim.py`
(keep its constants in sync with `predict/Suggester.kt` and `predict/SpellCorrector.kt`).
`CFGS='[{"SPLIT_PENALTY": 3.0}, ...]'` runs a sweep. Typo data lives in
`tools/.cache/raw/typos/` (git-ignored; only the Wikipedia pairs reach the app, through
`build_typos.py`):

- `wikipedia.dat`, `aspell.dat`, `missp.dat` from Roger Mitton's Birkbeck spelling error
  corpus: https://www.dcs.bbk.ac.uk/~ROGER/corpora.html
- `es_list.json`: raw wikitext of es.wikipedia "Wikipedia:Corrector ortográfico/Listado"
  (CC BY-SA), from
  `https://es.wikipedia.org/w/index.php?title=Wikipedia:Corrector_ortogr%C3%A1fico/Listado&action=raw`
- `my_typos.jsonl`: the user's own typo log (Settings → Keep a typo log → Copy typo log).

## Typo list (`build_typos.py`)

`python3 -I tools/build_typos.py en` writes `app/src/main/assets/dict/en_typos.txt`
(`typo<TAB>word`, at most 25,000 lines), which the keyboard uses as strong corrections
(`Suggester.TYPO_LIST_*`, mirrored in `suggest_sim.py`). Sources: generated keyboard slips
of the commonest 6,000 words, only where one word clearly explains the slip (short words and
first-letter slips; longer words are left to the corrector); Wikipedia's misspelling lists
(`wikipedia.dat`, `es_list.json`; CC BY-SA, credited in ATTRIBUTION.md); and set A.
To score D1 honestly, build with `--holdout --out <tmp>` and run
`D1_HALF=1 TYPO_FILE=<tmp> python3 -I tools/typo_bench.py en` (D1 then uses only list items the
holdout build left out). Rebuild after changing the dictionary.

## Word-shape benchmark (`shape_bench.py`)

`python3 -I tools/shape_bench.py en [glide|tap|all] [N]` splits glide and tap-correction
accuracy by word length (2-4, 5-7, 8-10, 11+) and by repeated letters (double: coffee;
revisit: banana; plain). Glide misses are labelled: `pruned` (never a candidate), `geo` (cut
before language-model rescoring), `lm` (lost on rescoring) or `twin` (same keys, only doubled
letters or accents differ). `POOL=3000` samples everyday words only (default: commonest
20,000); `CFGS='[{...}]'` sweeps constants of glide_sim.py / suggest_sim.py.

## Sentence-context prediction (n-grams)

Bori predicts the next word from up to three previous words of the sentence (like AOSP LatinIME's
4-gram dictionaries and Gboard's n-gram decoder before its neural model).

1. Download the subtitle text (OPUS OpenSubtitles 2018, monolingual). Only the first 20M lines
   are kept, streamed, so the full 2–3.6 GB file is never stored:
   ```
   mkdir -p tools/.cache/raw/opensubs && cd tools/.cache/raw/opensubs
   for l in en es; do curl -s https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2018/mono/$l.txt.gz \
     | gunzip | head -n 20000000 | gzip -1 > $l.20M.txt.gz; done
   ```
2. `python3 -I tools/ngram_count.py en` (and `es`): counts 2/3/4-grams in Tatoeba + subtitles
   into `tools/.cache/ngrams/` (about 15 min per language with 3 worker processes; uses GNU sort
   for the merge). The last 1M subtitle lines are held out for the benchmark.
3. `python3 -I tools/build_dict.py en`: the `.bdic` bigrams now come from these counts
   (`BIGRAMS=tatoeba` gives the old Tatoeba-only bigrams).
4. `python3 -I tools/build_ngrams.py en`: writes `assets/dict/en.ngram` (2- and 3-word
   contexts on top of the `.bdic` bigrams). Must be rebuilt after every `.bdic` rebuild (node ids;
   the app refuses a mismatched file). Pruning knobs: `TOP3 TOP4 MIN_CTX3 MIN_CTX4 MIN_ENT3
   MIN_ENT4 KEEP_RATIO` (env), `NGRAM_OUT=<dir>` for trial builds.
5. `BASE_BDIC=<old en.bdic> python3 -I tools/predict_bench.py en 4000`: top-1/top-3 next-word
   accuracy on the held-out lines (`BDIC=`/`NGRAM=` to test trial builds).

## Emoji style packs

`python3 -I tools/make_emoji_overrides.py` draws Bori's own overrides (light-blue Puerto Rico flag)
into `tools/emoji_overrides/`. `python3 -I tools/build_emoji_packs.py [style ...]` downloads only the
emoji listed in `view/EmojiData.kt` from each open set (cache: `tools/.cache/emoji/`), scales them to
72 px WebP and writes `app/src/main/assets/emoji/<style>.bep` + `custom.bep` (overrides). Needs Pillow
with WebP and `rsvg-convert` (Void: `xbps-install python3-Pillow librsvg-utils`). Re-run after adding
emoji to EmojiData.kt. Delete `*.missing` markers in the cache to retry emoji a set lacked.
