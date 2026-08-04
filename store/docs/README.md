# App Store screenshots

Generated App Store assets for **iPhone** and **iPad**, one folder per store localization.

- **What to upload where, and the caption text of every image →
  [`store/copy/app-store.md`](store/copy/app-store.md)**
- How this was built, what broke, what to watch out for → [`PROGRESS.md`](PROGRESS.md)
- Subagent research notes → [`notes/`](notes/)

## Layout

```
iphone-6.9/<locale>/NN-<scene>.jpg    1290x2796   App Store Connect "iPhone 6.9-inch display"
ipad-13/<locale>/NN-<scene>.jpg       2752x2064   App Store Connect "iPad 13-inch display" (landscape)
previews/<device>-<locale>-contact-sheet.jpg      review grids, do NOT upload
```

Files are numbered in upload order. `bg` · `pl` · `pt` · `ro` · `ua` are app locale codes;
`store/copy/app-store.md` maps them to App Store Connect localization names.

iPhone covers **bg, pl, pt** only — the Romanian iPhone source screenshots were never
captured. See `PROGRESS.md` §7.

## Regenerating

```bash
cd store/tools

node generate-store-screenshots.mjs                          # everything
node generate-store-screenshots.mjs --device=iphone
node generate-store-screenshots.mjs --locale=pl,ro --sheet    # + contact sheets
```

Needs headless Google Chrome, ImageMagick (`magick`) and node 18+. Chrome prints harmless
shutdown noise to stderr; filter with `grep -vE "ERROR:|allocator|bytes written"`.

A locale is skipped with a warning if any source screenshot a scene needs is missing — it
will not emit a partial set.

## How a screenshot gets its caption

`scenes.json` is the manifest. It binds each source screen to a copy block **by name**:

```jsonc
{
  "id": "confirm",                       // -> 07-confirm.jpg
  "screen": "result_publish_dialog",     // -> store/captures/iphone/<locale>/result_publish_dialog_dark.png
  "theme": "dark",
  "copy": "final_check",                 // -> copy.json[<lang>].screenshots[...] via COPY_BLOCKS
  "why": "..."                           // why this caption is TRUE of this exact screen
}
```

Two rules keep captions honest:

1. **Never pair by index.** The three older generator scripts pair screenshot to caption by
   sorted filename position, so re-capturing or adding one file shifts every caption by one.
   That is how you end up with "your listing is live" printed over a login screen.
2. **Every scene must have an honest `why`.** If you cannot write one, drop the scene rather
   than reaching for a vaguer caption. Two copy blocks in `copy.json` are deliberately
   unused for exactly this reason — see `PROGRESS.md` §4.3.

Caption text comes from `store/copy/copy.json` and is **not** modified by this pipeline.

## Related

| Path | Purpose |
| --- | --- |
| `store/captures/<device>/<locale>/` | Maestro-captured raw app screenshots (the input) |
| `scripts/maestro-*-ipad.sh`, `scripts/maestro-*-ios.sh` | capture the raw screenshots |
| `scripts/normalize-ipad-screenshots.sh` | un-rotates sideways iPad captures; idempotent, called by the iPad capture scripts |
| `store/copy/copy.json` | localized captions (shared, key names are frozen) |
| `store/assets/` | rendered output: `app-store/{iphone-6.9,ipad-13}/`, `play-store/phone/` |
| `.claude/skills/app-store-screenshots/` | the skill: workflow, preflight checks, `reference.md` with geometry and known source defects |
