---
name: app-store-screenshots
description: Turn captured app screenshots into branded, caption-framed App Store / Google Play assets where every caption provably describes the screen it sits on. Use when the user wants to build, refresh, regenerate or add a locale to store listing screenshots — "store screenshots", "App Store screenshots", "iPad screenshots", "screenshots for the listing", "add Romanian screenshots", "regenerate the store images", "the caption doesn't match the screenshot". This is the COMPOSITING half of the pipeline; capturing the raw screenshots on a device is covered by the sellsnap-screenshots / maestro-screenshots / store-screenshots skills.
---

# App Store screenshots — compositing captures into caption-framed assets

Composites raw per-locale app screenshots into store-ready images: branded gradient, headline,
sub-line, two chips, tick badge, device mock. Reads localized copy from
`Design/StoreScreenshots/copy.json`.

**The whole point of this skill is one rule.** Everything else is detail.

> ## Never pair a screenshot to its caption by index.
>
> Bind them by name, in a reviewable manifest, with a written justification per pair.
>
> Index pairing (`copy[i]` against a sorted file list) is how you ship a login screenshot
> captioned *"your listing is live"*. It looks fine the day you write it, because capture
> order happens to match flow order — then one re-capture, rename or added file shifts every
> caption by one and nothing errors.

## Files

| Path | Role |
| --- | --- |
| `Design/AppStoreScreenshots/generate-app-store-screenshots.mjs` | the generator |
| `Design/AppStoreScreenshots/scenes.json` | **the manifest** — screen → copy block, per device |
| `Design/AppStoreScreenshots/PROGRESS.md` | full history, 12 numbered insights, follow-ups |
| `Design/AppStoreScreenshots/APP_STORE_TEXT.md` | upload guide: file → caption → localization |
| `Design/StoreScreenshots/copy.json` | localized copy. **Shared with the Play generator — do not rename its keys** |
| `screenshots/<device>/<locale>/*.png` | the raw captures (input) |
| `scripts/normalize-ipad-screenshots.sh` | un-rotates sideways iPad captures; idempotent |
| `reference.md` (next to this file) | device geometry, thresholds, known source defects |

```bash
cd Design/AppStoreScreenshots
node generate-app-store-screenshots.mjs                        # everything available
node generate-app-store-screenshots.mjs --device=ipad --locale=pl --sheet
```

Needs headless Chrome, ImageMagick, node 18+. Chrome spams harmless shutdown noise —
pipe through `grep -vE "ERROR:|allocator|bytes written"`.

## Preflight — do this before rendering anything

Skipping these is how bad assets ship. Each one caught a real bug.

1. **Look at one capture per device with your own eyes.** Not `identify` — actually view it.
   `identify` reported the iPad captures as plausible 2064x2752 portraits; every one was
   landscape UI rotated 90° CCW with no EXIF tag.
2. **Fingerprint every source a scene will use and diff them.** Two scenes resolving to the
   same app screen is the worst outcome: two store images that look identical with different
   captions, so one is a lie. The generator's `findSimilarScenes()` does this and refuses to
   build the locale. Do not bypass it without looking.
3. **Check per-locale, never from one locale.** Defects are not global. A duplicate-file bug
   hit `pl` and `pt` but not `bg` or `ro`; a capture race hit `bg`, `pt`, `ro`, `ua` but not
   `pl`. Run `md5` across locales for the same filename.
4. **md5 is necessary but not sufficient.** In `ro` all 15 files were byte-unique yet
   `analysing_progress_dark` was still the result screen — it differed from `result_top_dark`
   by one spinner frame. Use the mean+stddev fingerprint (see `reference.md`).
5. **Read the flow from the screens, not the filenames.** Here `generate_ad_*` is the *input*
   screen and `analysing_*` comes *after* it. Alphabetical order gets it backwards.

## Adding or changing a scene

Edit `scenes.json`. Each entry:

```jsonc
{
  "id": "confirm",                      // -> 07-confirm.jpg
  "screen": "result_publish_dialog",    // -> screenshots/<device>/<locale>/<screen>_<theme>.png
  "theme": "dark",                      // omit if the screen has no theme variants
  "copy": "final_check",                // COPY_BLOCKS alias in the generator, or "fallback:N"
  "doodle": "burst",                    // circle | frame | burst
  "pillIconColors": ["#FBBF24", "#1B8E5A"],
  "why": "..."                          // see below
}
```

**The `why` field is load-bearing, not documentation.** It must state why the caption is true
of that exact screen. If you cannot write it honestly, **drop the scene** — do not reach for a
vaguer caption to make a slot work. Scene count is not a target: 8 usable screens minus
defects gave 7 (iPhone) and 6 (iPad) against a request for 10, and that was correct.

Two copy blocks are deliberately unused: `live` ("Your listing is live") because no capture
shows a published listing — the app's furthest state is the pre-publish confirmation sheet —
and `fallback:1` because nothing on any screen makes it *specifically* true.

## Runbook: add a locale

The most common request ("add Romanian screenshots"). Nothing in the generator needs editing.

1. Confirm the captures exist: `ls screenshots/{iphone,ipad}/<locale>/` — expect 15 files.
   If it holds only `auth.png`, the set was never captured; that is a capture task, not this
   skill. Stop and say so.
2. iPad only: `scripts/normalize-ipad-screenshots.sh` (safe to re-run).
3. Confirm `LOCALE_TO_LANG` has the folder → `copy.json` key mapping, and that `copy.json` has
   that language. Add the mapping if missing; **do not invent copy** — if the language is
   absent, that is a translation task to raise with the user.
4. Run the preflight checks above on the new locale specifically.
5. `node generate-app-store-screenshots.mjs --locale=<locale> --sheet`
6. View the contact sheet. Then add the locale's rows to `APP_STORE_TEXT.md` (see the trap
   below).

## Trap: `APP_STORE_TEXT.md` drifts

It is **hand-maintained**, and its "App screen shown" column duplicates `scenes.json`. Change a
scene's `screen` or `theme` and every row for that scene across every locale silently goes
stale — this happened once already (five iPad rows) and was only caught by grepping.

After any manifest edit, grep the doc for the old screen name before considering the work done.
Better: teach the generator a `--doc` flag that emits the tables from `scenes.json` + `copy.json`
so drift becomes impossible. Not done yet; worth doing on the next substantial change.

## Rules for the visual layer

- **Derive decoration coordinates from text geometry, never from canvas fractions.** A ratio
  tuned on a 1290x2796 phone puts a stroke through the pill row on a 2752x2064 tablet.
  Profiles declare a `doodleBand` (`top` above the headline cap height, `bottom` between pills
  and device, or `null` to omit).
- **Compute pill positions from measured label widths.** Hard-coded x positions tuned to one
  language overlap in another — `Weryfikacja` is 11 characters, `OLX` is 3.
- **Size the headline from the longest line**, and reserve a right-hand zone so a long
  Romanian line cannot run under the star or tick badge.
- **Keep the device mock's screen cutout at the source aspect ratio** so `slice` crops nothing.
- **Leave room under the device** for rotation (a ±2° tilt drops a corner ~33px) plus the drop
  shadow, or the shadow truncates against the canvas edge.
- Mixing light and dark themes across a set is good; pick per screen on legibility, not
  symmetry.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| `magick: unable to read font ''` from `montage` | ImageMagick's default font config is broken here. Needs **both** `-font <path-to-ttf>` and `-label ""`, even with no text drawn. |
| Locale skipped, "missing N sources" | Correct behaviour — it will not emit a partial set. Either capture the missing screens or remove the scene. |
| Locale skipped, "duplicate screens" | Two scenes resolve to the same app screen. Fix the manifest; investigate the capture. |
| Caption text absent / wrong language | `LOCALE_TO_LANG` maps folder → `copy.json` key and they differ (`ua` → `uk`). |
| iPad assets look sideways | Run `scripts/normalize-ipad-screenshots.sh`. It is idempotent and only touches portrait files. |
| Headline overlaps a star or badge | Increase that profile's `text.headlineRightReserve`. |
| Font renders as a fallback | The `@font-face` `file://` URL only resolves when Chrome loads the SVG from a `file://` path. |

## Working with subagents on this

Delegating pays off for the fan-out — cataloguing 30 source screens, QA-ing 50 outputs,
transcribing localized strings. Two agents found the rotation bug and a duplicate-file bug
that were invisible from the code.

**But re-verify any finding that changes a decision.** In practice a majority of severity
calls were wrong: two "defective" dark screenshots were fine and shipped, a one-locale bug was
generalised to all locales, and two of four reported "blockers" were the app's normal UI
mistaken for render artefacts. Read the observations; re-derive the severity yourself.

## Definition of done

- Every locale folder has the same file count; `identify` confirms one exact size per device.
- No two images in a folder show the same app screen.
- No caption claims a state later than what its screen shows — check the whole set for
  "published"/"live" wording specifically.
- `APP_STORE_TEXT.md` regenerated or hand-checked against `scenes.json`; stale rows after a
  scene swap are easy to miss.
- No `.svg` or `.raw.png` left behind.
- New insights appended to `PROGRESS.md`; app bugs spotted in the screenshots logged to
  `.claude/tmp/<topic>-followups.md` rather than fixed inline.
