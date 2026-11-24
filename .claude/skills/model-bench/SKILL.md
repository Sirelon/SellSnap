---
name: model-bench
description: >-
  Rerun and score the listing-generation model bench: real seller photos through the production
  prompt, scored against an answer key. Use before changing the model, reasoning effort, image
  detail or prompt of listing generation (`analyzeThing`, `AD_GENERATION_MODEL_ID`), or to compare
  a new OpenAI model against the current one.
---

# Model bench

The bench lives in `scripts/model-bench/`; the last results and the reasons behind the scoring
are in `research/MODEL-BENCH-listing-generation.md`. Read that file first: its tables are the
baseline a new run is compared with.

## Steps

1. **Fetch the photos.** Copy `local.properties` from the main checkout if this is a worktree,
   then run `python3 scripts/model-bench/bench.py fetch`. Done when `.cache/photos/` holds one
   JPEG per manifest item. The photos are seller pictures: they stay in the gitignored `.cache/`.
2. **Define the configs.** Each config in `scripts/model-bench/configs.json` names a model and
   any of `effort`, `detail`, `temperature`, `maxOutputTokens`, `promptAppend` (text added after
   the production prompt). A new model also needs its prices in `PRICE` in `bench.py`, quoted from
   the OpenAI pricing page with the date. Done when every config you want to compare is in the file.
3. **Quote the cost, then run.** A gpt-6.1-terra config costs $0.0006-0.0025 per call and a run
   is 45 calls (25 photos plus 4 repeats on 5 stability items). Stay under $2 without asking the owner;
   above that, post the estimate and wait. Run `python3 scripts/model-bench/bench.py run <config>...`;
   it skips calls already recorded for an unchanged config and prompt, so a rerun after an error
   costs only the missing calls. Done when the log ends without `ERROR` lines.
4. **Score.** `python3 scripts/model-bench/bench.py score <config>...` prints the table. Every
   `read by eye` line is a keyword miss, not a verdict: open the answer with `bench.py show
   <config>`, compare it with the item in `manifest.json`, and decide pass or fail yourself. Done
   when every miss has a verdict.
5. **Write it down.** Update the results tables and the recommendation in
   `research/MODEL-BENCH-listing-generation.md`, with the date, the configs and the total cost.

## Scoring rules (decided by the owner)

- The right item is required: a wrong item type fails the call. The two dark or blurry photos must
  be refused; refusing a usable photo fails the call.
- Specificity earns a bonus point and never fails a call: the exact model (iPhone 11 Pro), the brand
  printed on the box (Guess), the exact part number.
- No category is excluded. A car price is a guess and is not scored.
- Price stability is read from 5 runs of each stability item: SIR-116's bar is every run within
  ±10% of the item's median, and `on modal price` counts runs on the item's most common price.
  Reasoning models refuse `temperature`, so a config for one must earn its stability. One or two
  runs out of 25 is noise: repeat a config under a second name before trusting a small gain.

## Changing the answer key

Each item in `manifest.json` carries `must` (every regex must match the lowercased title plus
description), `wrong` (any match fails the call), `wrongDetail` (a wrong model or part number),
`bonus`, `refuse` (accepted refusal codes) and `maxPrice`. The regexes match Ukrainian, the
bench's market. Look at the photo yourself before you change an entry, and keep the item text exact: it is the only answer key a reviewer sees.
