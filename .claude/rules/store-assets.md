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
| `store/captures/<device>/<lang>/` | Headless renders of app screens (`StoreScenesTest`). Input, never uploaded, not in git |
| `store/assets/` | Finished App Store and Play Store images; the screenshot sets are generated on demand and not in git, the feature graphics are tracked |
| `store/tools/` | The compositor, its `scenes.json` manifest, and the upload script |
| `store/docs/` | `README.md`: the layout of the generated assets |
| `brand/` | App icon sources, Claude Design prototype files |

Screenshot-to-caption pairing is an explicit manifest with a `why` per slot, never a filename
index. Workflow, store rules and sizes: the `sellsnap-screenshots` skill.
