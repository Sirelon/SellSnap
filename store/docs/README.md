# Store screenshots

Generated App Store assets for **iPhone** and **iPad**, and Google Play phone assets, one folder per store localization.

- **What to upload where, and the caption text of every image:**
  [`app-store.md`](../copy/app-store.md) (App Store, hand-maintained),
  [`play-store.md`](../copy/play-store.md) (Google Play, generated).
- Workflow, preflight checks, geometry and known source defects:
  [`.claude/skills/app-store-screenshots/`](../../.claude/skills/app-store-screenshots/).

## Layout

Paths relative to `store/assets/`:

```
app-store/iphone-6.9/<locale>/NN-<scene>.jpg    1290x2796   App Store Connect "iPhone 6.9-inch display"
app-store/ipad-13/<locale>/NN-<scene>.jpg       2752x2064   App Store Connect "iPad 13-inch display" (landscape)
play-store/phone/<locale>/NN-<scene>.jpg        1080x1920   Google Play phone screenshots
```

Contact sheets (`--sheet`) land in `store/previews/<device>-<locale>-contact-sheet.jpg`. They are review grids, never uploads.

Files are numbered in upload order. `bg` · `pl` · `pt` · `ro` · `ua` are app locale codes; the two upload guides map them to store localization names.

Coverage:

- iPhone: bg, pl, pt, ro. Ukrainian has no iPhone captures.
- iPad: bg, pl, pt, ro, ua.
- Play phone: bg, pl, pt, ro, ua.
- No locale has English captures.

## Regenerating

```bash
cd store/tools

node generate-store-screenshots.mjs                          # everything
node generate-store-screenshots.mjs --device=iphone
node generate-store-screenshots.mjs --locale=pl,ro --sheet    # + contact sheets
node generate-store-screenshots.mjs --device=android-phone --doc   # also rewrites play-store.md
```

Needs headless Google Chrome, ImageMagick (`magick`) and node 18+. Chrome prints harmless shutdown noise to stderr; filter with `grep -vE "ERROR:|allocator|bytes written"`.

A locale is skipped with a warning if any source screenshot a scene needs is missing. The generator never emits a partial set.

## How a screenshot gets its caption

`store/tools/scenes.json` is the manifest. It binds each source screen to a copy block **by name**:

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

1. **Pair by name, never by index.** Pairing by sorted filename position shifts every caption by one when a file is re-captured or added, and prints "your listing is live" over a login screen.
2. **Every scene has an honest `why`.** If you cannot write one, drop the scene rather than reaching for a vaguer caption.

Caption text comes from `store/copy/copy.json` and the pipeline never modifies it.

## Related

| Path | Purpose |
| --- | --- |
| `store/captures/<device>/<locale>/` | Maestro-captured raw app screenshots (the input) |
| `scripts/maestro-generate-ad-*-ios.sh`, `scripts/maestro-generate-ad-*-ipad.sh` | capture the raw screenshots |
| `scripts/normalize-ipad-screenshots.sh` | un-rotates sideways iPad captures; idempotent, called by the iPad capture scripts |
| `store/copy/copy.json` | localized captions (shared, key names are frozen) |
| `store/assets/` | rendered output: `app-store/{iphone-6.9,ipad-13}/`, `play-store/phone/`, `play-store/feature-graphic/` |
