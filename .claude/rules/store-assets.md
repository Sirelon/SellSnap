---
paths:
  - "store/**"
  - "brand/**"
  - "Design/**"
---

# Store assets and design assets

Everything that ships to App Store Connect / Play Console lives under `store/`; brand source
material that is not a store asset lives under `brand/`.

| Directory | Holds |
| --- | --- |
| `store/copy/` | All listing text, plus `copy.json` — the localized screenshot captions |
| `store/captures/<device>/<locale>/` | Raw Maestro screenshots. Input, never uploaded |
| `store/assets/` | Finished App Store and Play Store images |
| `store/tools/` | `generate-store-screenshots.mjs` and its `scenes.json` manifest |
| `store/docs/` | `README.md`: the layout of the generated assets and how to regenerate them |
| `brand/` | App icon sources, Claude Design prototype files |

Screenshot-to-caption pairing is an explicit manifest, never a filename index. Full workflow,
preflight checks and known source defects: `.claude/skills/app-store-screenshots/`.
