# App Store screenshot generation — progress & insights log

Living log for the session that built `store/assets/`.
Purpose: this file is the raw material for a future **skill**. It records what was
true, what was surprising, what broke, and what a future run must not repeat.

Date: 2026-08-03 · Branch: `main`

---

## 1. Goal

Produce App Store–ready screenshots for **iPhone** and **iPad**, per store locale,
where every caption is *logically bound* to the screen it sits on. Plus a
copy-paste doc of all store text. Ukrainian is already shipped and out of scope.
Russian and Kazakh are out of scope.

---

## 2. Terrain map (established by direct inspection, not assumption)

### 2.1 Two generations of source screenshots coexist — do not mix them up

| Location | What it is | Naming | Used by |
| --- | --- | --- | --- |
| `Design/Screenshots/{iphone,ipad,tablet}/` (deleted 2026-08-04, in git history) | **Legacy, gen-1.** Hand-taken simulator screenshots, Ukrainian only, dated 2026-06-03. | `Screenshot 2026-06-03 at 14.07.14.png` | the 3 existing generator scripts |
| `store/captures/{iphone,ipad,android-phone,android-tablet}/<locale>/` | **Current, gen-2.** Maestro-automated, per-locale, semantic filenames, light+dark. Added in `6e6b1d4`. | `result_top_dark.png` | nothing yet — **this task wires it up** |

> **Insight #1 — the big one.** All three generator scripts that existed at the start pair a
> screenshot to its caption **by sorted filename index** (`copy[index]`).
> With `Screenshot 2026-06-03 at 14.11.23.png` filenames that ordering is
> accidental — it is wall-clock capture order, which happened to match the flow.
> Any re-capture, re-crop or added file silently shifts every caption by one.
> This is precisely the "login screenshot captioned *now your ad is live*" failure
> mode. **Fix: never index-pair. Pair on an explicit semantic key.**

### 2.2 Source screenshot availability — THIS IS THE HARD CONSTRAINT

`store/captures/<device>/<locale>/`, 15 files = 8 unique screens × (light+dark), except
`auth.png` which has no theme variant.

| device | bg | pl | pt | ro | ua |
| --- | --- | --- | --- | --- | --- |
| `iphone` | **full (15)** | **full (15)** | **full (15)** | ⚠️ `auth.png` only | `auth.png` only |
| `ipad` | **full (15)** | **full (15)** | **full (15)** | **full (15)** | full (15) |
| `android-phone` | `auth.png` only | `auth.png` only | `auth.png` only | `auth.png` only | `auth.png` only |
| `android-tablet` | `auth.png` only | `auth.png` only | `auth.png` only | `auth.png` only | `auth.png` only |

> **Insight #2 — RO iPhone does not exist and cannot be self-served.**
> Commit `6e6b1d4` says it outright: *"Add GenerateAd screenshot flows and iPhone
> screenshots for pt/pl/bg"*. RO iPhone was never captured. Re-capturing is blocked
> by two things, both external to this repo:
> 1. `.maestro/setup_for_country.yaml` states the iOS auth sheet is a SpringBoard
>    dialog that **Maestro cannot tap**, and `olx_web_login.yaml` needs the password
>    typed by hand on the Mac keyboard (iOS `type="password"` is absent from the
>    a11y tree).
> 2. The OLX.ro account is suspended (prior over-retry of login).
>
> → **Deliverable scope is therefore: iPhone = bg/pl/pt, iPad = bg/pl/pt/ro.**
> Not a defect in this work; a data gap upstream. See §7.

### 2.3 Resolutions

| Thing | Size |
| --- | --- |
| gen-2 iPhone source | 1206 × 2622 (iPhone 16 Pro, 6.3") |
| gen-2 iPad source | 2064 × 2752 — **portrait** |
| legacy iPhone source | 1290 × 2796 |
| legacy iPad source | 2752 × 2064 — **landscape** |
| existing UA iPhone output | 1284 × 2778 (6.5" slot) |
| existing UA iPad output | 2752 × 2064 (13" landscape) |

> **Insight #3 — the iPad orientation flipped between generations.** Legacy iPad
> sources were landscape, gen-2 are portrait. The old iPad script hard-codes a
> 2752×2064 landscape canvas with a landscape tablet frame. Feeding portrait
> sources into it would letterbox/crop them badly. A new portrait iPad layout is
> required — this is not optional polish.

> **Insight #4 — the existing UA iPhone set is 1284×2778, i.e. the 6.5" slot.**
> Apple's current requirement for new submissions is the 6.9" slot
> (1290×2796 or 1320×2868). New output targets **1290×2796**. UA should eventually
> be regenerated to match; flagged in §7, not done here (user excluded UA).

### 2.4 Rendering toolchain (all verified present on this machine)

SVG string → headless Chrome `--screenshot` → ImageMagick downsample → JPEG q96.

- `magick` → `/opt/homebrew/bin/magick`
- `node` → v22.22.3
- Chrome → `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`
- Font → `composeApp/src/commonMain/composeResources/font/manrope_variable.ttf`,
  embedded via `@font-face { src: url("file://…") }`. **Chrome must be headless
  *and* given a `file://` SVG URL for the local font to load.**
- Source PNGs are inlined as base64 data URIs. An iPad PNG is ~1–3 MB → ~4 MB of
  base64 in the SVG. Works, but SVGs are large; they are intermediate artefacts.

### 2.5 Existing copy source: `store/copy/copy.json`

Shape: `{ <lang>: { screenshots: { <legacy-filename>: {headline[], sub, pills[]} }, fallback: [ …2 blocks… ] } }`

- Languages present: `uk`, `en`, `pl`, `ro`, `bg`, `pt`.
- 7 named blocks + 2 fallback blocks = **9 copy blocks per language**.
- Keys are **legacy gen-1 Android filenames** (`Screenshot_20260519_231837.png`) —
  they do not exist anywhere on disk any more. The file is effectively an
  ordered list wearing filename costumes.
- Only `generate-google-play-screenshots.mjs` reads it. **Both iOS scripts hard-code
  Ukrainian copy inline** — that is why there was never a multi-language iOS run.

> **Insight #5 — the 7 named blocks map onto the flow as:** hero/welcome → new listing →
> AI writing → review → publish → final check → live. (My first pass mistakenly read this
> as "no caption exists for the login screen" — see the correction in Insight #9. The
> Ukrainian iOS script *also* carries a bespoke auth caption,
> `Стартуйте як зручно` / `OLX або гостьовий режим`, hard-coded and Ukrainian-only, which
> is what made the gap look real.)

---

## 3. Delegation log

| # | Agent | Brief | Output |
| --- | --- | --- | --- |
| 1 | cataloguer | View all 15 iPhone `pl` screenshots; record visible UI, truthful claim, forbidden claim, flow order, hero picks, light/dark pick | `notes/agent-iphone-screen-inventory.md` |
| 2 | cataloguer | Same for all 15 iPad `pl` screenshots + verdict on portrait vs landscape presentation | `notes/agent-ipad-screen-inventory.md` |
| 3 | visual QA | Review all 51 rendered outputs for overflow, clipping, illegibility, caption/screen mismatch, duplicates | `notes/agent-visual-qa.md` |
| 4 | technical writer | Build the upload guide mechanically from `scenes.json` + `copy.json`, strings copied verbatim | `store/copy/app-store.md` |

**What delegation was good for:** the two cataloguers found the iPad rotation bug and the
md5 duplication — both invisible from code and both would have shipped. The writer agent
machine-verified all 51 localized strings byte-identical against `copy.json`, which is
exactly the kind of transcription work worth handing off.

**What it was not good for:** every finding that changed a decision needed re-checking
(see pitfall 6). Two of the iPhone cataloguer's "defective" calls were wrong, and the iPad
cataloguer generalised a pl-only bug to all locales.

---

## 4. Screen inventory & caption mapping

### 4.1 The real user flow (both agents agreed independently)

```
auth → generate_ad_top → generate_ad_bottom → analysing_start
     → analysing_progress → result_top → result_bottom → result_publish_dialog
```

> **Insight #6 — the filenames actively mislead.** `generate_ad_*` is the *input* screen
> (photo picker + "Generuj z AI" button), **not** the generated output. `analysing_*`
> comes *after* it. A naive alphabetical or "generate-then-analyse" reading gets the flow
> backwards and produces exactly the caption/screen mismatch we were told to avoid.
> `analysing_start` and `analysing_progress` are the **same screen** at two checklist
> states (step 1 spinning vs steps 1–4 ticked green).

### 4.2 Not every capture is usable

Both cataloguers found genuine defects. **Verified personally where the finding changed a
decision** — the iPhone cataloguer was too harsh on dark mode, so trust-but-verify matters:

| Source | Verdict | Note |
| --- | --- | --- |
| `iphone/*/generate_ad_bottom_{light,dark}` | **dropped** | mid-scroll, content clipped under the status bar; dark variant shows the *empty* photo state with CTA "Najpierw dodaj zdjęcie" — cannot support any AI claim |
| `iphone/*/analysing_progress_dark` | dropped | shows 1/5 done, i.e. a near-duplicate of `analysing_start_dark` |
| `iphone/*/result_top_dark` | dropped | the `Opis` card body is hidden behind the sticky CTA |
| `iphone/*/result_bottom_light` | dropped | success banner ~60% occluded by the CTA → dark used instead |
| `iphone/*/result_publish_dialog_light` | dropped | 2 of 3 thumbnails render as blank peach squares |
| `iphone/*/analysing_start_dark` | **kept** — agent called it weak, it is not | verified by eye: clean, and its checklist literally reads *Tworzenie tytułu / Pisanie opisu / Szacowanie ceny* |
| `iphone/*/result_bottom_dark` | **kept** — agent called it defective, it is not | verified by eye: Details + Location + "everything ready" + green CTA all fully visible |
| `ipad/*/analysing_progress_{light,dark}` | **dropped entirely** | a capture race — see Insight #10. `analysing_start_dark` used instead. |
| `ipad/*/analysing_start_light` | dropped | mislabelled: actually a progress state, caught mid-animation on a half-tick |
| `ipad/*/generate_ad_bottom_*` | dropped | 2 of 3 grid columns empty, CTA clips the hint field |
| `ipad/*/result_top_light` | dropped | price glyph clipped mid-character → dark used instead |
| `ipad/*/result_bottom_dark` | dropped | different scroll offset, no attribute card, and it mis-categorises men's shoes as *Buty damskie* → light used instead |

> **Insight #7 — defects are per-locale, not global.** The `analysing_progress_light` ==
> `result_top_light` duplication exists in pl and pt but not bg or ro. Never conclude
> "this screen is broken" from one locale. `md5` across locales is the cheap check.

> ### Insight #10 — the `analysing_progress` capture is a RACE, and md5 alone won't catch it
>
> This was the most dangerous defect found, and it was found *after* the first full render
> looked fine. `analysing_progress` is captured while the AI is still working. If generation
> finishes first, Maestro screenshots the **result screen** instead. Full iPad matrix:
>
> | locale | unique files /15 | leak |
> | --- | --- | --- |
> | bg | 14 | `analysing_progress_dark` == `result_top_dark` |
> | pl | 14 | `analysing_progress_light` == `result_top_light` |
> | pt | 13 | **both** dark and light leaked |
> | ro | **15** | nothing byte-identical — but `analysing_progress_dark` is *still* the result screen |
> | ua | 14 | `analysing_progress_light` == `result_top_light` |
>
> Romanian is the trap: all 15 files are byte-unique, so an md5 check says "clean", yet the
> screen is wrong. It differs from `result_top_dark` only by a spinner frame. I caught it by
> eye on the contact sheet, then confirmed it numerically.
>
> **The cheap reliable discriminator is whole-image mean + standard deviation:**
>
> | source | mean | stddev |
> | --- | --- | --- |
> | `analysing_start_dark` (all 4 locales) | 0.098 | 0.089 |
> | `analysing_progress_dark` (bg / pt / ro) | 0.269 | 0.239 |
> | `result_top_dark` (all) | 0.269 | 0.239 |
>
> The dark analysing screen is flat and dark; the result screen is dominated by a large
> photo. Two captures of the same screen land within ~0.001 of each other.
>
> **Fix taken:** iPad scene 3 uses `analysing_start_dark`, which is a genuine analysing
> screen in *every* locale (verified numerically above). The iPhone set was separately
> checked and is clean (`analysing_progress_light` sits at 0.92 vs `result_top_light` at 0.64
> in all three locales).
>
> **Guard added:** `findSimilarScenes()` in the generator fingerprints every scene's source
> and refuses to build a locale whose scenes resolve to the same app screen —
> *"Two captions over one screen means one of them is a lie."* Override with
> `--allow-similar`. Verified by deliberately reintroducing the bug: it skipped `ipad/bg`
> and still built `ipad/pl`.
>
> → **Skill rule: any screenshot captured mid-async-operation is a race. Fingerprint the
> sources and diff them before rendering, never after.**

### 4.3 The mapping that shipped

Manifest lives in [`scenes.json`](scenes.json); every scene carries a `why` field stating
the justification that its caption is *true of that exact screen*. Rule adopted: **if you
cannot write the `why` line honestly, drop the scene** — do not reach for a vaguer caption.

| # | iPhone (7) | iPad (6) | Copy block used |
| --- | --- | --- | --- |
| 1 | `auth` | `auth` | hero — "Sell faster with AI" / "Photo in, listing out" |
| 2 | `generate_ad_top_light` | `generate_ad_top_light` | new_listing — "New listing in a minute" |
| 3 | `analysing_start_dark` | `analysing_start_dark` | ai_writes — "AI writes text while you wait" |
| 4 | `analysing_progress_light` | — | fallback:0 — "Create listings faster" |
| 5 | `result_top_light` | `result_top_dark` | review — "Review everything before posting" |
| 6 | `result_bottom_dark` | `result_bottom_light` | publish_tap — "Publish in one tap" |
| 7 | `result_publish_dialog_dark` | `result_publish_dialog_dark` | final_check — "Publish with confidence" |

Themes are deliberately mixed within each set (iPhone: light, light, dark, light, light,
dark, dark).

> **Insight #8 — one copy block is intentionally unused, and that is the whole point.**
> `copy.json` block 7 is "Your listing is live" / "Status and link always at hand".
> **No captured screen shows a published listing** — the furthest state the app reaches is
> the *pre-publish confirmation sheet*. Using that block anywhere would have been the exact
> lie the brief warned about ("login screenshot captioned *now ad is visible for all*").
> `fallback:1` ("Less busywork, more sales") is also unused: nothing it says is false, but
> nothing on any screen makes it *specifically* true.
>
> **Corollary: the caption count is not a target.** 8 usable screens minus defects gave 7
> and 6, not the requested 10. Padding to 10 would have required either reusing a screen
> with a second caption or inventing copy. Fewer, honest screenshots was the right call
> and the brief explicitly allowed it.

> **Insight #9 — `auth` did have a caption after all.** My first reading of `copy.json`
> concluded there was no login-screen copy (recorded as the old Insight #5). Wrong: the
> `hero` block's sub is "Photo in, listing out", and the welcome screen's own on-screen
> subtitle is *"Wrzuć zdjęcie, dostań ogłoszenie — AI robi resztę"* — the same sentence.
> Block 1 was always the welcome-screen block. Check the screen before declaring a gap.

---

## 5. Decisions

| Decision | Rationale |
| --- | --- |
| New generator `generate-store-screenshots.mjs`; the two iOS/iPad generators retired in the follow-up cleanup, the Play Store one kept | Both retired scripts were Ukrainian-only *and* index-paired, so keeping them meant keeping a live copy of the bug this work removed. The Play generator is still in use and still keyed to the legacy filenames. |
| Pair copy by **semantic manifest**, never by index | Root cause of the whole class of caption bugs. See Insight #1. |
| `copy.json` left byte-for-byte unchanged | It is the shared source of truth for the Play Store script. Readable aliases live in `COPY_BLOCKS` in the generator instead of renaming keys. |
| iPhone output **1290 × 2796** | The 6.9" slot Apple requires for new submissions. The shipped UA set is 1284 × 2778 (6.5", now optional). |
| iPad output **2752 × 2064 landscape** | The app genuinely runs landscape on iPad; the captures are landscape once un-rotated; and it matches the already-shipped UA iPad set. |
| iPad source PNGs **rotated in place** | They were simply wrong — sideways with no EXIF tag. Fixing the data fixes every future consumer, not just this generator. Guarded + idempotent + git-reversible. |
| Skip a whole locale rather than emit a partial set | The generator refuses `iphone/ro` (6 of 7 sources missing) instead of silently producing a 1-screenshot set. |
| Generated `ipad-13/ua/` anyway | Not requested, but it costs nothing and the shipped UA iPad set has the text-over-tablet overlap bug. Labelled optional. |
| Pill positions computed, not hard-coded | See Insight #11. |

---

## 5b. Visual QA outcome

Full report: [`notes/agent-visual-qa.md`](notes/agent-visual-qa.md). The reviewer returned
**"Ship? No."** with 4 blockers. Verdict after verifying each myself:

| Reported | Verified? | Action |
| --- | --- | --- |
| **B1** iPad `03-ai-writes` == `04-review` in bg/pt/ro/ua | **Real** — this is Insight #10 | Already fixed before the report landed. The reviewer measured the previous batch. |
| **B2** iPad `01-welcome` is a mid-animation capture, "Try without account" drawn twice, in all 5 locales | **No — rejected** | Opened the source. That is the app's genuine design: an explanatory card (icon + heading + description) with a button below repeating the label. No render artefact, no doubled edge. |
| **B3** iPhone `pt/02-add-photos` has app text over the bezel and the status bar over the photo thumbnails | **Real** | Source defect: the pt capture sits at a different scroll offset than pl/bg, so the header is clipped and the status bar overlaps the thumbnails. No better pt source exists (`generate_ad_top_dark` shows the empty photo state). Shipped with the flaw; pt iPhone re-capture logged in §7. |
| **B4** iPhone `pt/01-welcome` missing hero image, doubled button | **Half** | The scroll offset is real — the pt auth capture cuts off the hero image and the greeting, leaving a stray "." at the top. The "doubled button" is the same false positive as B2. Same resolution as B3. |

Should-fix items **accepted and left as-is, with reasons**:

- *"iPhone `03-ai-writes` and `04-ai-steps` show the same screen twice."* True — same screen,
  two states, two themes (dark spinner vs light green ticks). Both captions are honest and
  the shipped Ukrainian set does the same. Kept for the "you can see progress" beat; a future
  run may prefer to drop it and ship 6+6.
- *"`06-details` says 'publish in one tap' over a form with required fields."* The fields are
  prefilled and the screen itself renders *"Świetnie — wszystko gotowe ✨"* above a single
  publish button. Judged truthful.
- *Status-bar noise*: English `Mon 20 Jul` on Polish-locale iPad shots, and the clock differs
  between frames in a set. Cosmetic, invisible to store visitors, fixed only by re-capture.

Confirmed **clean** by the reviewer across all 51 images: no text overflow (tightest
clearance 55 px), no strikethrough, no clipped iPhone mock, and **no image implies the
listing is published** — all 39 caption/screen pairs checked, furthest state is the
pre-publish confirmation sheet. That last one was the brief's central requirement.

> **Insight #12 — a QA agent's severity labels are not trustworthy; its observations are.**
> Two of four "blockers" were the app's normal UI. But the same agent independently
> re-derived the analysing-screen duplication with RMSE numbers, and caught the pt scroll
> offset that I had missed across two earlier review passes. Read the observations, re-derive
> the severity.

---

## 6. What went wrong / pitfalls for the future skill

1. **The iPad sources were sideways and nobody knew.** All 75 PNGs across all 5 locales.
   `magick identify` reports 2064×2752 and looks plausible, so this is invisible unless you
   actually *view* an image. Fixed at the root (`scripts/normalize-ipad-screenshots.sh`,
   now also called from both iPad capture scripts).
   → **Skill rule: view at least one capture per device before trusting a folder.**

2. **`magick montage` is broken on this machine.** It resolves its default font name to a
   bare `'` and dies with `unable to read font`. Needs BOTH `-font <path-to-ttf>` and
   `-label ""`, even though no text is drawn. Cost ~2 debug cycles.

3. **Decorative strokes struck through the headlines.** The `burst` doodle had two curves at
   y=232/370 — dead centre of the headline's second line — which rendered as a
   strikethrough on screenshots 2 and 7. Anchoring doodles to named `doodleBand` values
   (above the cap height / between pills and device) fixed it.
   → **Skill rule: decoration coordinates must be derived from the text geometry, never
   from canvas fractions.** A ratio that looks right on a 1290×2796 phone puts a stroke
   through the pills on a 2752×2064 tablet.

4. **The old iPad layout's text was overlapped by the tablet mock.** Visible in the shipped
   Ukrainian iPad screenshots 3 and 4: the sub-line and the green badge sit *under* the
   tablet. Cause: text and device placed with independent hard-coded coordinates and no
   collision reasoning. The new layout puts text in a reserved band above the device.

5. **Insight #11 — hard-coded pill positions cannot survive localisation.** The old scripts
   set per-screenshot pill x positions tuned to Ukrainian. Pill width grows with label
   length (`Weryfikacja` = 11 chars vs `OLX` = 3), so a longer Polish or Romanian word
   overlaps the next pill. Pills are now laid out left-to-right from their own measured
   widths. Same for headline size: it is chosen from the **longest line**, with a reserved
   right-hand zone so a long Romanian line cannot run under the star or badge.

6. **Trust-but-verify subagents.** The iPhone cataloguer flagged `analysing_start_dark` and
   `result_bottom_dark` as defective; both are fine and both shipped. The iPad cataloguer
   generalised a pl-only duplicate-file bug to all locales. Both agents were still very
   valuable — they found the rotation bug and the md5 duplication that I would have missed.
   → **Skill rule: subagents catalogue, the orchestrator verifies any finding that changes
   a decision.**

7. **Chrome prints alarming-but-harmless noise** to stderr on every headless invocation
   (`Can't perform OS integration while the browser is shutting down`, allocator warnings).
   Not failures. Filter with `grep -vE "ERROR:|allocator|bytes written"` when reading logs.

8. **Base64-inlined sources make the intermediate SVGs multi-MB** (~2.5 MB per iPad page).
   They are deleted after rendering; do not commit them.

---

## 7. Open items / follow-ups

**Blocking a complete deliverable (needs the user / a device):**

1. **No Romanian iPhone screenshots.** 6 of 7 sources missing; never captured (see §2.2).
   Re-capturing needs a manual OLX login on a simulator, and OLX.ro is suspended.
   Until then the Romanian App Store listing has no iPhone screenshots and will fall back
   to the default localisation's set.
2. **No English screenshot set.** `store/captures/<device>/en/` does not exist, so if the
   store's default localisation is English it has no screenshots either. `copy.json`
   already has full `en` copy — the moment English captures exist, `--locale=en` just works.
2a. **Re-capture the Portuguese iPhone set.** `iphone/pt/auth.png` and
   `iphone/pt/generate_ad_top_light.png` were captured at a different scroll offset than
   pl/bg: the hero image and section headers are cut off and the status bar overlaps the
   photo thumbnails. Shipped anyway (legible and on-message) but visibly less clean.

**Capture-pipeline bugs (worth fixing before the next capture run):**

2b. **The `analysing_progress` screenshot races the AI** (Insight #10). The Maestro flows
   `.maestro/result_screenshots.yaml` / `result_screenshots_dark_only.yaml` should assert the
   progress checklist is actually on screen — and that it is *partially* complete — before
   calling `takeScreenshot`, instead of relying on timing. Until then, do not use
   `analysing_progress` for store assets; `analysing_start` is reliable.

**App bugs surfaced while reviewing screenshots (not fixed here):**

3. `80%%` — doubled percent sign on the analysing-progress hint (iPhone, pl).
4. Price formatted `zł 280` / `zł 240`; Polish convention is `280 zł` (suffix).
5. iPad: men's New Balance 998 categorised as `Buty damskie` (women's shoes) on
   `result_bottom_dark`.
6. iPad: tofu (missing-glyph) boxes on the `auth` screen and the `Profil` nav-rail item.
7. iPad: the back chevron collides with the status-bar clock on all result screens.
8. iPad: most screens are a stretched phone layout — single full-width column, attribute
   rows and the price slider spanning 2752 px with 80–90% blank width. Only
   `generate_ad_top/bottom` are genuinely adaptive (nav rail + 3-across grid).

**Repo hygiene:**

9. `composeApp/.../features/seller/ad/ScreenshotMode.kt` has an uncommitted change flipping
   `screenshotMode` from `false` to `true`. That is the capture-time debug flag; it must not
   be committed as `true`. Left untouched deliberately — it is not this task's change.
10. Consider regenerating the Ukrainian iPhone set at 1290 × 2796 (currently 1284 × 2778,
    the now-optional 6.5" slot) — but the UA iPhone sources are also missing, so this needs
    a capture run too.

Further per-file iPad observations from the cataloguer are in
`.claude/tmp/ipad-screenshots-followups.md`.

## 2026-08-04 — Google Play phone profile added

`android-phone` is now a device profile in `generate-store-screenshots.mjs`
(1080x1920, 6 scenes, sources from `store/captures/android-phone/<locale>/`), replacing
the old `Design/StoreScreenshots/generate-google-play-screenshots.mjs` for phone assets
(that script and its gen-1 sources were deleted on 2026-08-04). Rendered for
pl/ro/bg/pt; ua skipped (never captured). Doc: `store/copy/play-store.md`, generated from
`scenes.json` + `copy.json` rather than hand-written.

Two insights worth keeping:

13. **Do not scale a phone profile from the iPhone one.** 9:16 is far shorter than the
    iPhone's 9:19.5, so proportionally scaled y-values drop the pill row onto the device
    bezel. The vertical rhythm has to be laid out per aspect ratio: text block in the top
    ~25%, pills ending by y=492, bezel from y=560 with 108px of slack below for rotation and
    the drop shadow. `doodleBand.bottom` is null for the same reason as the iPad.

14. **A caption can be falsified by the app's own error state, not just by the wrong screen.**
    The Android result captures have no simulated location, so the CTA reads
    `Publish · 1 to fix`. The iPhone set's "Publish in one tap" over that would be a lie, so
    `android-phone`'s `details` scene uses `fallback:1` ("Less busywork, more sales" /
    "Prepare listings with ready-made suggestions"), which is true of the AI-filled details
    card actually in frame. Check the CTA state, not only the screen identity.

15. **A missing locale may not be missing — check git history** (the gen-1 `Design/Screenshots` folder referenced below was itself deleted on 2026-08-04 during the `store/` restructure)**.**
    `store/captures/android-phone/ua/` held only `auth.png`, but the full Ukrainian Android set
    was sitting in `Design/Screenshots/Screenshot_20260519_*.png`, deleted in 56d53a27 — the
    very files `copy.json`'s keys are named after. Recovered with
    `git show 56d53a27^:<path>`. It is the only capture set that reaches the published-listing
    screen, so the `live` copy block ("Your listing is live") finally has an honest home.
16. **Uneven capture coverage needs two manifest escape hatches, not a lowest common
    denominator.** The recovered ua set is dark-only and has an extra screen. Rather than
    forcing every locale to dark and dropping the extra frame, scenes now support
    `themeByLocale` (per-locale theme override) and `onlyLocales` (restrict a scene to the
    locales that have its source). Both stores accept a different screenshot count per
    language. Without `onlyLocales`, one locale-specific scene would fail the
    missing-sources check for every *other* locale and skip them all.
17. **`store/copy/play-store.md` is emitted by `--doc`,** from `scenes.json` + `copy.json` — the
    fix that §"Trap: store/copy/app-store.md drifts" asked for, applied to the Play doc only so far.
    Doing the same for `store/copy/app-store.md` is still open.
