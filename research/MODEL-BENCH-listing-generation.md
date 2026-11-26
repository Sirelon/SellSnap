# Listing-generation model bench

Which OpenAI model and settings write SellSnap listings (`analyzeThing`, `AD_GENERATION_MODEL_ID`
in `OpenAIClient.kt`). gpt-4.1 shuts down on 2027-01-14
([deprecations](https://developers.openai.com/api/docs/deprecations)). Ticket: SIR-199.
Rerun with the `model-bench` skill (`.claude/skills/model-bench/SKILL.md`).

## Recommendation

**gpt-6.1-terra, reasoning effort `none`, image detail `high`, plus one prompt paragraph.** This is
the production setup since SIR-200 (prompt v8), for the listing, the attribute fill and the polish.

| Setting | Value | Why |
| --- | --- | --- |
| `model` | `gpt-6.1-terra` | 0 wrong items in all 360 calls of the 8 terra configs at effort `none`-`medium`; half the cost of gpt-4.1 |
| `reasoning.effort` | `none` | `low` and `medium` reason for 7 and 34 tokens on average and score the same; `high` adds specificity but 4.5 s and one wrong item |
| `temperature` | not sent | terra refuses it |
| `detail` | `high` (unchanged) | a 1280 px photo already fits inside `high`; `original` and `auto` cannot add pixels (Inferred: from the gpt-5.6-terra row of the [images guide](https://developers.openai.com/api/docs/guides/images-vision); the guide has no gpt-6.1-terra row). `low` shrinks the photo to 512 px, where the part-number labels (#3, #4, #11) are the likely loss; not run |
| `maxOutputTokens` | 1000 (unchanged) | at effort `none` the answer is about 92 output tokens and 0 reasoning tokens |

Prompt paragraph, at the end of `adGenerationInstructions` (prompt v8):

```
Names printed on the item: read every legible word on the item itself, on its label or tag, and on the box or packaging shown with it. A brand, model name or part number printed there is a visible fact: put it in the title, spelled as printed but in the brand's usual capitalisation (Nike, not NIKE); part numbers stay exactly as printed. Reading a name off the box does not make the box part of the offer.
Exact model: name it only when it is printed, or when a distinctive design visible in the photos fits one model and no other (for a phone, the camera layout together with the body colour). Many products have close lookalikes, and a general resemblance is not enough: when more than one model fits what is visible, give the brand or product line and stop there. A wrong model is worse than no model. Never fill the model's place in the title with a feature ("with three cameras", "with a strap"): item type, brand and colour are enough.
```

The capitalisation clause and the last sentence come from the v8 run below: "exactly as printed"
copied logos in capitals ("Кросівки NIKE", "GUESS"), against the prompt's no-ALL-CAPS rule, and
the phone title read "Apple iPhone з потрійною камерою".

It makes terra name the brand printed on the box (Guess, #2) in both runs, with no wrong model
anywhere. It does not make terra name a model it must recognise by design (iPhone 11 Pro, Air Max
Dn): only effort `high` or gpt-6.2-sol do that.

**Price stability is not solved.** No config meets SIR-116's bar (every one of 5 runs per item
within ±10% of the item's median) on all 5 stability items. With the recommended setup, 38 of 50
runs land on the item's most common price; the outliers are one 100-UAH step on a 700-900 UAH
item, which is already more than 10%. A price ladder in the prompt reached 23/25 once and did
not repeat (21/25 and 19/25 when combined with the paragraph above), so it is not recommended.
The production setup (gpt-4.1 at temperature 0.3) was not measured here, by the owner's decision.

Cost per listing with one new photo: about $0.0035 with nothing cached, about $0.002 with the
prompt prefix cached; gpt-4.1 costs about $0.0064 for the same tokens. Inferred: from the
measured token counts (3007 in, 92 out) and the prices in `bench.py`.

## Results

Run 2026-11-24/25, production prompt v7, 450 calls, $0.79. "Bonus" counts the 17 items with a
specificity point on run 1; "on modal price" counts the 25 stability runs (5 items × 5) that
land on each item's most common price. `$/call` depends on how much of the image was cached by
earlier calls, so compare it only within one model.

| Config | Wrong items | Refusals held | Wrong details | Bonus | On modal price | Within ±10% | $/call | Median s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| terra-none | 0 | 2/2 | 0 | 9 | 19/25 | 1/5 | 0.00101 | 3.5 |
| terra-low | 0 | 2/2 | 0 | 8 | 20/25 | 2/5 | 0.00142 | 4.0 |
| terra-medium | 0 | 2/2 | 0 | 9 | 16/25 | 3/5 | 0.00156 | 4.7 |
| **terra-specific** (recommended) | 0 | 2/2 | 0 | 10 | 18/25 | 3/5 | 0.00140 | 3.5 |
| terra-specific-b (repeat) | 0 | 2/2 | 0 | 10 | 20/25 | 1/5 | 0.00061 | 3.9 |
| terra-ladder | 0 | 2/2 | 0 | 9 | 23/25 | 4/5 | 0.00140 | 4.2 |
| terra-ladder-specific | 0 | 2/2 | 0 | 10 | 21/25 | 2/5 | 0.00142 | 5.2 |
| terra-ladder-specific-b (repeat) | 0 | 2/2 | 0 | 10 | 19/25 | 1/5 | 0.00063 | 3.9 |
| terra-ladder-specific-high | **1** (#43) | 2/2 | 0 | 12 | 21/25 | 2/5 | 0.00245 | 8.4 |
| sol-low (gpt-6.2-sol) | 0 | 2/2 | 0 | 12 | 13/25 | 2/5 | 0.00569 | 3.9 |

Production prompt v8 (2026-11-25, `terra-none` reading the prompt from `OpenAIClient.kt`, 45
calls, $0.037): 0 wrong items, refusals 2/2, 0 wrong details, bonus 10/17, 16/25 on modal
price, 2/5 within ±10%, median 6.4 s (Inferred: slower API that hour; the same config ran at
3.7 s an hour earlier). Titles: "Кросівки Nike", "Ботильйони Guess", "Смартфон Apple iPhone
темно-зелений". A chained spot check on #1, #2 and #11 (listing, attribute fill, polish "make it
shorter and add Nova Poshta delivery") filled brand, size and colour correctly and kept every fact.

- **terra-ladder-specific-high** called the hookah kaloud (#43) "a round metal stand with a
  handle", a wrong item by the owner's rule. It is the only terra config to name iPhone 11 Pro,
  Air Max Dn and JBL JR POP.
- **sol-low** names iPhone 11 Pro, Air Max Dn and Guess, but costs four times as much, prices the
  Carrera sunglasses (#17) at 1200-1500 UAH against about 900, and has the least stable prices.
- Bonus points no config earned: Ugears (#44, printed on the lid), Nike Court Borough (#1),
  Dr. Martens Adrian (#28), Nike Mercurial (#47).
- The August GPT-5 regression (a JBL speaker close-up read as headphones, #16) did not appear in
  any of the 10 configs.

Every answer is in `scripts/model-bench/.cache/runs.jsonl` on the machine that ran the bench; the
configs are in `scripts/model-bench/configs.json`.

## Upload size

Run 2026-11-26, SIR-174: the production setup (`terra-none`, prompt v8) on the same photos at
1600 px / JPEG 80 (`terra-none-1600`) and at the app's 1280 px / JPEG 75, 90 calls, $0.148.

| Upload | Wrong items | Refusals held | Wrong details | Bonus | On modal price | Within ±10% | Median input tokens | $/call | Median s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1280 px, q75 | 0 | 2/2 | 0 | 10 | 15/25 | 0/5 | 3106 | 0.00144 | 3.4 |
| 1600 px, q80 | 0 | 2/2 | 0 | 10 | 19/25 | 1/5 | 3946 | 0.00184 | 3.5 |

- Both sizes name the same item on every photo, and the close-up part numbers (#3 Denso
  PS 98100-A50, #4 Magneti Marelli, #11 Figo Havuc W30) are read at both. Bonus points differ on
  two items and cancel out: 1600 px named Air Max Dn (#56), 1280 px named the Chevrolet Tacuma
  (#33). One run per item, so this is noise.
- The bench photos are about 40% fewer bytes at 1280/q75 (sum of the 25 files).
- gpt-6.1-terra bills a 1600 px photo for more tokens than a 1280 px one, so the larger photo
  is not cut down to the same size before the model reads it. Inferred: from the input tokens
  in the table.
- Price stability is worse at 1280 in this run (15/25 against 19/25), within the spread of the
  exact repeats above (18 vs 20, 21 vs 19) and of the earlier v8 run at 1280 (16/25).

## Method

- **Photos:** 25 real seller photos from Firebase Storage, listed with their answer key in
  `scripts/model-bench/manifest.json`: 23 items across clothing, shoes, electronics, car parts,
  furniture and a car, plus one motion-blurred and one black frame that must be refused. Each
  answer-key entry was checked by eye against its photo. #39 is an AI-generated product shot and
  #41 a four-photo collage; both are real seller uploads.
- **Call:** the production prompt read from `OpenAIClient.kt`, market Ukraine, no seller note,
  one photo per call, resized like the app (EXIF orientation applied, 1280 px long side, JPEG
  quality 75; a config may set `uploadLongSide` and `uploadQuality`), the production user message.
- **Scoring:** each item has regexes over the lowercased Ukrainian title and description:
  `must` (item type), `wrong` (a wrong item), `wrongDetail` (a wrong model or part number),
  `bonus` (specificity), `maxPrice`. A call with no `must` match is read by eye; the one such
  call (#43 at effort `high`) is a wrong item. Bonus and wrong details are scored on run 1 of
  each item.
- **Stability:** #17 Carrera sunglasses, #1 Nike sneakers, #42 iPhone 11 Pro, #36 bar stool,
  #28 Dr. Martens loafers, 5 runs each. A difference of one or two runs out of 25 is noise; the
  two `-b` configs are exact repeats that show its size.

## What the bench does not cover

Several photos per listing, seller notes, markets other than Ukraine, regeneration (which chains
onto the previous answer and may hold the price steadier), the attribute fill and the listing
polish.
