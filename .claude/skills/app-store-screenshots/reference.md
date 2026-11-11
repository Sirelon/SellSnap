# Reference — numbers, thresholds, known defects

Companion to `SKILL.md`. Read when you need the concrete values.

## Device profiles

All three are accepted store sizes. Values live in `DEVICES` in the generator.

| | iPhone | iPad | Android phone |
| --- | --- | --- | --- |
| store slot | ASC iPhone 6.9-inch display | ASC iPad 13-inch display | Play phone screenshots |
| canvas | 1290 x 2796 portrait | 2752 x 2064 **landscape** | 1080 x 1920 portrait (9:16) |
| renderScale | 2 (render, then Lanczos down) | 1 | 2 |
| screen cutout | 760 x 1652 (0.460) | 1776 x 1332 (exactly 4:3) | 552 x 1196 (0.4615) |
| source capture | 1206 x 2622 (iPhone 16 Pro) | 2752 x 2064 after un-rotating | 1080 x 2340; `auth.png` 1080 x 2400 (crops ~2% of its height) |
| device.y | 792 | 500 | 560 |
| headline sizes | 96 / 86 / 76 | 100 / 88 / 80 | 72 / 64 / 56 |
| headlineRightReserve | 230 | 520 | 190 |
| doodleBand | top 78, bottom 742 | top 40, bottom `null` | top 52, bottom `null` |
| notch | pill | `null` (camera dot) | `null` (camera dot) |

- Keep the screen cutout's aspect ratio equal to the source's, or `preserveAspectRatio="…slice"` crops content.
- Reserve room under the device for the rotation drop plus the drop shadow: ~33px at ±2° (iPad), ~53px at ±5° (Android phone).
- Lay out each profile's vertical rhythm for its own aspect ratio. Scaling the iPhone y-values to 9:16 drops the pill row onto the bezel. The Android phone profile puts the text block in the top ~25%, ends the pills at y=492 and starts the bezel at y=560, leaving 108px below.
- `doodleBand.bottom` is `null` wherever fewer than ~70px separate the pill row from the bezel.
- The iPad canvas is landscape: the iPad app runs landscape, and the un-rotated captures are landscape.
- Apple requires the 6.9" slot (1290 x 2796) for new submissions. The shipped Ukrainian iPhone set is 1284 x 2778 (6.5" slot, now optional).

## Text measurement

`AVG_ADVANCE = 0.56` em, used for headline fitting and pill widths. It is ~20% pessimistic for Manrope ExtraBold, which over-reserves, the safe direction. Re-check the longest Romanian headline before lowering it.

Longest strings across the copy blocks in use:

| lang | longest headline line | longest sub | longest pill |
| --- | --- | --- | --- |
| pl | 18 `jednym dotknięciem` | 41 | 11 `Weryfikacja` |
| ro | **20** `înainte de publicare` | **42** | 10 `Verificare` |
| bg | 17 | 36 | 10 `Публикация` |
| pt | 17 | 38 | 11 `Verificação` |
| en | 17 | 40 | 11 `Description` |

Romanian is the stress case for every width decision. On iPhone the widest sub-line (`pl` `01-welcome`) ends 55px short of the yellow star, the tightest clearance in the set. A sub-line ~3 characters longer collides, so check any longer copy by eye.

## Duplicate-screen fingerprint

`findSimilarScenes()` compares whole-image `mean` and `standard_deviation`:

```bash
magick identify -format "%[fx:mean] %[fx:standard_deviation]" file.png
```

Two scenes clash if **both** differ by `< 0.002`. Override with `--allow-similar` only after looking at both images.

Same-screen captures land within ~0.001. Different screens sit orders of magnitude apart. The threshold ignores a spinner frame or caret blink and catches a leak. Observed on iPad:

| source | mean | stddev |
| --- | --- | --- |
| `analysing_start_dark` (all locales) | 0.098 | 0.089–0.095 |
| `result_top_dark` (all locales) | 0.269 | 0.239 |
| `analysing_progress_dark` in bg / pt / ro / ua | 0.269 | 0.239 (leaked the result screen) |

### Why md5 is not enough

`analysing_progress` is captured while the AI works. When generation finishes first, the capture is the result screen. Byte comparison finds some leaks and misses others:

| iPad locale | byte-identical to `result_top` | leaks by fingerprint |
| --- | --- | --- |
| bg | `analysing_progress_dark` | dark, light |
| pl | `analysing_progress_light` | light |
| pt | dark and light | dark, light |
| ro | none | dark, light (differs from `result_top` by one spinner frame) |
| ua | `analysing_progress_light` | dark, light |

Any screenshot captured mid-async-operation is a race. Fingerprint the sources and diff them before rendering.

On iPhone the discriminator is clean: `analysing_progress_light` sits at mean 0.91–0.93 against 0.64–0.66 for `result_top_light` in every locale.

## copy.json structure

Keys are the filenames of the **legacy 2026-05-19 Android screenshots**. Those files no longer exist on disk: the keys are positions in the flow wearing filename costumes. Do not rename them, because every translation is keyed by them, and the Play flow and the generator both read `copy.json`. Readable aliases live in `COPY_BLOCKS` in the generator.

Shape: `{ <lang>: { screenshots: { <legacy-filename>: {headline[], sub, pills[]} }, fallback: [2 blocks] } }`. Languages present: `uk`, `en`, `pl`, `ro`, `bg`, `pt` (9 blocks each).

| alias | copy (en) | used by |
| --- | --- | --- |
| `hero` | "Sell faster with AI" / "Photo in, listing out" | `auth` |
| `new_listing` | "New listing in a minute" | `generate_ad_top` |
| `ai_writes` | "AI writes text while you wait" | `analysing_start` |
| `review` | "Review everything before posting" | `result_top` |
| `publish_tap` | "Publish in one tap" | `result_bottom` (iPhone, iPad) |
| `final_check` | "Publish with confidence" / "Final check before OLX" | `result_publish_dialog` |
| `live` | "Your listing is live" | Android phone `published`, `ua` only |
| `fallback:0` | "Create listings faster" | iPhone `analysing_progress` |
| `fallback:1` | "Less busywork, more sales" | Android phone `result_bottom` |

`hero`'s sub is verbatim the welcome screen's own on-screen subtitle. Before declaring "there is no copy for screen X", read what screen X says.

`copy.json` has a full `en` block. `LOCALE_TO_LANG` maps `en`, but no `store/captures/<device>/en/` folder exists, so no English set renders.

## Manifest escape hatches

Capture coverage differs per locale. Two `scenes.json` fields absorb that without forcing a lowest common denominator:

- `themeByLocale`: per-locale theme override, for a locale whose captures hold one theme only (Android phone `ua` is dark-only).
- `onlyLocales`: restricts a scene to the locales that hold its source. Without it, one locale-specific scene fails the missing-sources check for every other locale and skips them all.

Both stores accept a different screenshot count per language.

## Truthful captions

A caption is true of the screen's state, not just its identity.

- **Never caption a state later than the screen shows.** The furthest state on every capture except Android phone `ua` is the pre-publish confirmation sheet. `live` ("Your listing is live") belongs only to the `published` capture. `fallback:1` is the claim-free block for a screen no other block describes truthfully.
- **Check the CTA.** Android phone result captures have no simulated location, so the CTA reads `Publish · 1 to fix`. "Publish in one tap" over it is false, so the Android phone `details` scene uses `fallback:1`. A capture with a mock location (green CTA) restores `publish_tap`.
- **Check the claim against the fields.** `result_bottom` shows `Materiał` and `Dostawa` still empty and `Kolor` empty in some themes. "Everything filled automatically" is false there.
- **Empty photo state.** The iPhone `generate_ad_top_dark` and `generate_ad_bottom_dark` show no photos and a blocked CTA (`Najpierw dodaj zdjęcie`). They support no AI claim.

What each source screen supports:

| Screen | Shows | Caption must not claim |
| --- | --- | --- |
| `auth` | welcome, OLX sign-in, guest mode | anything created, generated or published |
| `generate_ad_top` | photo picker, 1–8 photos, `Generuj z AI` | an ad generated or posted |
| `analysing_start` | upload spinning, 4 steps pending | any step complete |
| `analysing_progress` | checklist partly ticked | publication, a duration |
| `result_top` | `Gotowe w N sekund`, AI title and description, publish CTA pending | already live; a price (none on this screen) |
| `result_bottom` | prefilled Szczegóły, Lokalizacja, ready banner | every field filled |
| `result_publish_dialog` | `Gotowy do publikacji?` with title, category, price | live, published, instant (copy says 1–2 minutes) |
| `published` (ua Android) | tick, listing card, olx.ua link, status tile | nothing beyond "live" |

## Source screen inventory

8 unique screens per device; 15 files (light and dark each, except `auth`). Flow order, not alphabetical:

```
auth → generate_ad_top → generate_ad_bottom → analysing_start
     → analysing_progress → result_top → result_bottom → result_publish_dialog
```

- `generate_ad_*` is the photo-picker **input** screen. `analysing_*` comes after it.
- `generate_ad_bottom` is `generate_ad_top` scrolled down. `result_bottom` is `result_top` scrolled down. `result_publish_dialog` is a modal over `result_bottom`.
- `analysing_start` and `analysing_progress` are one screen at two checklist states.
- Android phone `ua` holds a different, dark-only set: `auth`, `auth_dark`, `generate_ad_top_dark`, `analysing_start_dark`, `result_top_dark`, `result_bottom_dark`, `result_publish_dialog_dark`, `published`. It comes from the 2026-05-19 build and shows a different item than the other locales. The UI has moved on since.

## Known source defects

Re-verify after any re-capture. Several are per-locale.

| Source | Problem |
| --- | --- |
| `ipad/*/analysing_progress_*` | capture race: grabs the result screen when the AI finishes first (see the leak table above). Use `analysing_start_dark`. |
| `ipad/*/analysing_start_light` | a progress state caught mid-animation, not a start state |
| `ipad/*/generate_ad_bottom_*` | 2 of 3 grid columns empty, CTA clips the hint field |
| `ipad/*/result_top_light` | price glyph clipped mid-character |
| `ipad/*/result_bottom_dark` | different scroll offset, no attribute card |
| `ipad/ro/result_*` | demo item is brown men's shoes filed under women's footwear, and the detail row's size contradicts the title |
| `ipad/*` status bar | date and clock come from other sessions: Polish `Wt. 28 lip` inside bg / ro / ua / pt captures, English `Mon 20 Jul` on every `auth`, a different clock per frame |
| `ipad/*/{analysing_*,result_*}` | the back chevron sits on the status-bar clock |
| `ipad/*/auth`, `ipad/*/generate_ad_top_*` | `Profil` nav-rail icon and the guest-mode icons render as empty outline placeholders |
| `iphone/ua` | **only `auth.png` exists**; never captured |
| `iphone/ro` | 14 of 15 files; `analysing_progress_dark` is absent and unused |
| `iphone/pt/{auth,generate_ad_top_light}` | different scroll offset: hero image and heading clipped, status bar over the thumbnails |
| `iphone/{bg,pl}/auth` | primary CTA clipped at the bottom edge to an unlabelled orange sliver |
| `iphone/*/generate_ad_top_*` | sticky `Generuj z AI` overlaps the Camera / Gallery row |
| `iphone/*/generate_ad_bottom_*` | mid-scroll; dark shows the *empty* photo state |
| `iphone/*/analysing_progress_dark` | 1/5 steps done: near-duplicate of `analysing_start_dark` |
| `iphone/*/result_top_dark` | description card hidden behind the sticky CTA |
| `iphone/*/result_bottom_light` | success banner ~60% occluded |
| `iphone/*/result_publish_dialog_light` | 2 of 3 thumbnails render blank |
| `android-phone/{bg,ro}` | no `analysing_progress` captures |
| `android-phone/{pl,ro,bg,pt}/result_*` | CTA shows `Publish · 1 to fix` (see Truthful captions) |
| `android-tablet/*` | `auth.png` only; no 7"/10" assets |
| demo item (all locales) | the New Balance 998 close-up of laces and a barcode label fills ~40% of the device on `result_top` |

Verified **good** despite looking suspect: `iphone/*/analysing_start_dark`, `iphone/*/result_bottom_dark`, and the doubled "try without account" label on `ipad/*/auth` and `iphone/pt/auth` (the app's real card-plus-button design, not a render artefact).

Presentation limits: on iPad the whole UI sits in a 1776px cutout, so body copy lands at 11–16px and reads as grey mush at gallery-thumbnail size. Only `generate_ad_top` is genuinely tablet-adaptive (nav rail, 3-across grid); every other iPad screen is a stretched phone column. The app's dark theme is a brown close to the orange canvas, so dark `details` and `confirm` frames separate weakly from the background.

## Capture-side preconditions

Capturing is the `sellsnap-screenshots` skill (`.claude/skills/`). Three constraints shape what this skill can produce:

- iOS auth uses a SpringBoard sheet Maestro cannot tap, and the OLX password field is absent from the a11y tree, so login is typed by hand. iPhone locale coverage is whatever was captured manually.
- `screenshotMode` in `composeApp/.../features/seller/ad/ScreenshotMode.kt` must be `true` to capture and **must never be committed as `true`**.
- `scripts/normalize-ipad-screenshots.sh` un-rotates iPad captures. `xcrun simctl io … screenshot` on a landscape iPad simulator writes the raw 2064 x 2752 framebuffer with no EXIF tag, so the UI is sideways while the file looks plausible. The script is idempotent (portrait files only) and the iPad capture scripts call it.

## Rendering facts

- Pipeline: SVG string, headless Chrome `--screenshot`, ImageMagick downsample, JPEG q96.
- Source PNGs inline as base64, so an iPad intermediate SVG is ~2.5 MB. Delete the SVGs after rendering; never commit them.
- Font: `composeApp/src/commonMain/composeResources/font/manrope_variable.ttf`, embedded through `@font-face` with a `file://` URL.
- `--sheet` writes contact sheets to `store/previews/`. They are for review, never for upload.
