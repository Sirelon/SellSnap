---
name: app-store-screenshots
description: Turn captured app screenshots into branded, caption-framed App Store / Google Play assets where every caption provably describes the screen it sits on. Use when the user wants to build, refresh, regenerate or add a locale to store listing screenshots — "store screenshots", "App Store screenshots", "iPad screenshots", "screenshots for the listing", "add Romanian screenshots", "regenerate the store images", "the caption doesn't match the screenshot". This is the COMPOSITING half of the pipeline; capturing the raw screenshots on a device is the sellsnap-screenshots skill (`.claude/skills/`).
---

# App Store screenshots — compositing captures into caption-framed assets

Composites raw per-locale app screenshots into store-ready images: branded gradient, headline,
sub-line, two chips, tick badge, device mock. Reads localized copy from
`store/copy/copy.json`.

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
| `store/tools/generate-store-screenshots.mjs` | the generator |
| `store/tools/scenes.json` | **the manifest** — screen → copy block, per device |
| `store/docs/README.md` | layout of `store/assets/`, what to upload where |
| `store/copy/app-store.md` | App Store upload guide: file → caption → localization (hand-maintained) |
| `store/copy/play-store.md` | Play upload guide, emitted by `--doc` |
| `store/copy/copy.json` | localized copy. **Do not rename its keys** — every translation is keyed by them |
| `store/captures/<device>/<locale>/*.png` | the raw captures (input) |
| `scripts/normalize-ipad-screenshots.sh` | un-rotates sideways iPad captures; idempotent |
| `reference.md` (next to this file) | device geometry, thresholds, caption-truth rules, known source defects; a new durable insight goes here |

```bash
cd store/tools
node generate-store-screenshots.mjs                        # everything available
node generate-store-screenshots.mjs --device=ipad --locale=pl --sheet
node generate-store-screenshots.mjs --device=android-phone --sheet --doc   # also rewrites play-store.md
```

Needs headless Chrome, ImageMagick, node 18+. Chrome spams harmless shutdown noise —
pipe through `grep -vE "ERROR:|allocator|bytes written"`.

## Preflight — do this before rendering anything

Skipping these is how bad assets ship.

1. **Look at one capture per device with your own eyes.** Not `identify`: `identify` reports a
   plausible size for a sideways capture (raw iPad framebuffers are 2064x2752 portrait with no
   EXIF tag and landscape UI rotated 90° CCW).
2. **Fingerprint every source a scene will use and diff them.** Two scenes resolving to the
   same app screen is the worst outcome: two store images that look identical with different
   captions, so one is a lie. The generator's `findSimilarScenes()` does this and refuses to
   build the locale. Do not bypass it without looking.
3. **Check per-locale, never from one locale.** Defects differ per locale (the leak table in
   `reference.md`). Run `md5` across locales for the same filename.
4. **md5 is necessary but not sufficient.** Two byte-unique files can show the same screen
   one spinner frame apart. Use the mean+stddev fingerprint (see `reference.md`).
5. **Read the flow from the screens, not the filenames.** `generate_ad_*` is the *input*
   screen and `analysing_*` comes *after* it. Alphabetical order gets it backwards.

## Adding or changing a scene

Edit `scenes.json`. Each entry:

```jsonc
{
  "id": "confirm",                      // -> 07-confirm.jpg
  "screen": "result_publish_dialog",    // -> store/captures/<device>/<locale>/<screen>_<theme>.png
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
defects gave 7 (iPhone) and 6 (iPad) against a request for 10, and that was correct. Padding
would mean a second caption on one screen or invented copy.

`live` ("Your listing is live") belongs to the one capture that shows a published listing
(Android phone `ua`, `published`). Every other capture ends at the pre-publish confirmation
sheet, so no other scene may use it. Each copy block's allowed screens are in `reference.md`
(Truthful captions).

## Runbook: add a locale

The most common request ("add Romanian screenshots"). Nothing in the generator needs editing.

1. Confirm the captures exist: `ls store/captures/{iphone,ipad}/<locale>/` — expect 15 files.
   If it holds only `auth.png`, the set was never captured; that is a capture task, not this
   skill. Stop and say so.
2. iPad only: `scripts/normalize-ipad-screenshots.sh` (safe to re-run).
3. Confirm `LOCALE_TO_LANG` has the folder → `copy.json` key mapping, and that `copy.json` has
   that language. Add the mapping if missing; **do not invent copy** — if the language is
   absent, that is a translation task to raise with the user.
4. Run the preflight checks above on the new locale specifically.
5. `node generate-store-screenshots.mjs --locale=<locale> --sheet`
6. View the contact sheet. App Store locale: add its rows to `store/copy/app-store.md` (see the
   trap below). Android phone locale: re-run with `--doc`.

## Trap: `store/copy/app-store.md` drifts

`--doc` emits only `store/copy/play-store.md` (Android phone locales). `app-store.md` is
**hand-maintained**, and its "App screen shown" column duplicates `scenes.json`. Change a
scene's `screen` or `theme` and every row for that scene across every locale silently goes stale.

After any iPhone or iPad manifest edit, grep `app-store.md` for the old screen name before
considering the work done. After an Android phone edit, run `--doc` instead.

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

Delegating pays off for the fan-out: cataloguing source screens, QA-ing outputs, transcribing
localized strings. Agents see defects invisible from the code (sideways captures, duplicate files).

**The orchestrator re-verifies any finding that changes a decision.** Read an agent's
observations and re-derive its severity. Typical false calls: the app's normal UI mistaken for a
render artefact (the doubled "try without account" card and button), a one-locale bug
generalised to all locales, a fine dark screenshot called defective.

## Definition of done

- Every locale folder has the same file count; `identify` confirms one exact size per device.
- No two images in a folder show the same app screen.
- No caption claims a state later than what its screen shows — check the whole set for
  "published"/"live" wording specifically.
- `store/copy/app-store.md` hand-checked against `scenes.json` (iPhone, iPad); `play-store.md`
  regenerated with `--doc` (Android phone).
- No `.svg` or `.raw.png` left behind.
- A new durable insight is added to `reference.md` as a current-state rule; app bugs spotted in
  the screenshots go to `.claude/tmp/<topic>-followups.md` rather than being fixed inline.
