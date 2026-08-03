# Reference — numbers, thresholds, known defects

Companion to `SKILL.md`. Read when you need the concrete values.

## Device profiles

Both are accepted App Store Connect sizes. Values live in `DEVICES` in the generator.

| | iPhone | iPad |
| --- | --- | --- |
| ASC slot | iPhone 6.9-inch display | iPad 13-inch display |
| canvas | 1290 x 2796 portrait | 2752 x 2064 **landscape** |
| renderScale | 2 (render then Lanczos down) | 1 |
| screen cutout | 760 x 1652 (0.460) | 1776 x 1332 (exactly 4:3) |
| source capture | 1206 x 2622 (iPhone 16 Pro) | 2064 x 2752 → 2752 x 2064 after un-rotating |
| device.y | 792 | 500 |
| headline sizes | 96 / 86 / 76 | 100 / 88 / 80 |
| headlineRightReserve | 230 | 520 |
| doodleBand | top 78, bottom 742 | top 40, bottom `null` |

Keep the screen cutout's aspect ratio equal to the source's, or `preserveAspectRatio="…slice"`
crops content. Reserve room under the device for a ~33px rotation drop plus drop shadow.

The already-shipped Ukrainian iPhone set is **1284 x 2778** — the 6.5" slot, now optional.
Apple requires 6.9" for new submissions, hence 1290 x 2796 for everything new.

## Text measurement

`AVG_ADVANCE = 0.56` em, used for headline fitting and pill widths. Deliberately ~20%
pessimistic for Manrope ExtraBold — it over-reserves, which is the safe direction. Do not
"correct" it downward without re-checking the longest Romanian headline.

Longest strings across the copy blocks in use:

| lang | longest headline line | longest sub | longest pill |
| --- | --- | --- | --- |
| pl | 18 `jednym dotknięciem` | 41 | 11 `Weryfikacja` |
| ro | **20** `înainte de publicare` | **42** | 10 `Verificare` |
| bg | 17 | 36 | 10 `Публикация` |
| pt | 17 | 38 | 11 `Verificação` |
| en | 17 | 40 | 11 `Description` |

Romanian is the stress case for every width decision.

## Duplicate-screen fingerprint

`findSimilarScenes()` compares whole-image `mean` and `standard_deviation`:

```bash
magick identify -format "%[fx:mean] %[fx:standard_deviation]" file.png
```

Two scenes clash if **both** differ by `< 0.002`. Observed separation on iPad:

| source | mean | stddev |
| --- | --- | --- |
| `analysing_start_dark` (all locales) | 0.098 | 0.089 |
| `result_top_dark` (all locales) | 0.269 | 0.239 |
| `analysing_progress_dark` in bg / pt / ro | 0.269 | 0.239 | ← leaked the result screen |

Same-screen captures land within ~0.001; genuinely different screens are orders of magnitude
apart. Tight enough to ignore a spinner frame or caret blink, loose enough to catch a leak.
Override with `--allow-similar` only after looking at both images.

## copy.json structure

Keys are the filenames of the **legacy 2026-05-19 Android screenshots**, which no longer exist
on disk — they are positions in the flow wearing filename costumes. Do not rename them;
`Design/StoreScreenshots/generate-google-play-screenshots.mjs` still reads them. Readable
aliases live in `COPY_BLOCKS` in the App Store generator.

| alias | copy (en) | used by |
| --- | --- | --- |
| `hero` | "Sell faster with AI" / "Photo in, listing out" | `auth` |
| `new_listing` | "New listing in a minute" | `generate_ad_top` |
| `ai_writes` | "AI writes text while you wait" | `analysing_start` |
| `review` | "Review everything before posting" | `result_top` |
| `publish_tap` | "Publish in one tap" | `result_bottom` |
| `final_check` | "Publish with confidence" / "Final check before OLX" | `result_publish_dialog` |
| `live` | "Your listing is live" | **nothing — see SKILL.md** |
| `fallback:0` | "Create listings faster" | iPhone `analysing_progress` |
| `fallback:1` | "Less busywork, more sales" | nothing |

`hero`'s sub is verbatim the welcome screen's own on-screen subtitle. Before declaring "there
is no copy for screen X", check what screen X actually says.

## Source screen inventory

8 unique screens per device; 15 files (each has light+dark except `auth`).
Chronological order — **not** alphabetical:

```
auth → generate_ad_top → generate_ad_bottom → analysing_start
     → analysing_progress → result_top → result_bottom → result_publish_dialog
```

`generate_ad_*` is the photo-picker **input** screen. `analysing_start` and
`analysing_progress` are the same screen at two checklist states.

## Known source defects (as of 2026-08-03)

Re-verify after any re-capture; several are per-locale.

| Source | Problem |
| --- | --- |
| `ipad/**` (all 75) | stored sideways — fixed by `scripts/normalize-ipad-screenshots.sh` |
| `ipad/*/analysing_progress_*` | **capture race** — grabs the result screen if the AI finishes first. Use `analysing_start_dark` instead. |
| `ipad/{pl,pt,ua}/analysing_progress_light` | byte-identical to `result_top_light` |
| `ipad/*/analysing_start_light` | mislabelled; a progress state caught mid-animation |
| `ipad/*/generate_ad_bottom_*` | 2 of 3 grid columns empty, CTA clips the hint field |
| `ipad/*/result_top_light` | price glyph clipped mid-character |
| `ipad/*/result_bottom_dark` | different scroll offset, no attribute card |
| `iphone/ro`, `iphone/ua` | **only `auth.png` exists** — never captured |
| `iphone/pt/{auth,generate_ad_top_light}` | captured at a different scroll offset: heading clipped, status bar over the thumbnails |
| `iphone/*/generate_ad_bottom_*` | mid-scroll; dark shows the *empty* photo state |
| `iphone/*/analysing_progress_dark` | near-duplicate of `analysing_start_dark` (1/5 done) |
| `iphone/*/result_top_dark` | description card hidden behind the sticky CTA |
| `iphone/*/result_bottom_light` | success banner ~60% occluded |
| `iphone/*/result_publish_dialog_light` | 2 of 3 thumbnails render blank |

Verified **good** despite looking suspect in a quick pass: `iphone/*/analysing_start_dark`,
`iphone/*/result_bottom_dark`, `ipad/*/auth` (its repeated "try without account" label is the
app's real card+button design, not a render artefact).

## Capture-side preconditions

Capturing is a different skill (`sellsnap-screenshots`, `maestro-screenshots`,
`store-screenshots`), but two constraints shape what this skill can produce:

- iOS auth uses a SpringBoard sheet **Maestro cannot tap**, and the OLX password field is
  absent from the a11y tree, so login is typed by hand. iPhone locale coverage is therefore
  whatever was captured manually.
- `screenshotMode` in `composeApp/.../features/seller/ad/ScreenshotMode.kt` must be `true` to
  capture and **must never be committed as `true`**.
