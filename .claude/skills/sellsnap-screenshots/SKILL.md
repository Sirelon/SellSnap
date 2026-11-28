---
name: sellsnap-screenshots
description: >-
  Make SellSnap store screenshots end to end: render app screens headless from demo data (no device,
  no login), frame and caption them, check every caption against its screen, upload to App Store
  Connect and Google Play. Use for the full set, one screen ("redo the messages screenshot"), a new
  caption or language, a new device slot, or "upload the screenshots".
---

# SellSnap store screenshots

Four stages, each one command. Any stage runs alone, for one scene, device or language.

| Stage | Command (repo root) | Writes |
| --- | --- | --- |
| Render | `STORE_RENDER=1 ./gradlew :composeApp:jvmTest --tests '*StoreScenesTest*' --rerun -q` | `store/captures/<device>/<lang>/<scene>_<theme>.png` |
| Compose | `node store/tools/generate-store-screenshots.mjs --sheet` | `store/assets/<store>/<target>/<lang>/NN-<id>.jpg`, `store/previews/` |
| Review | your eyes on the contact sheets | fixes in scenes, demo data or captions |
| Upload | `LANG=en_US.UTF-8 ruby store/tools/upload-screenshots.rb app-store\|play [--apply] [--lang=uk]` | the store consoles |

Narrow the render with comma lists: `STORE_RENDER_SCENES`, `STORE_RENDER_DEVICES`, `STORE_RENDER_LANGS`,
`STORE_RENDER_THEMES`. Narrow the compositor with `--target`, `--lang`, `--scene`.

Files:

| Path | Role |
| --- | --- |
| `composeApp/src/jvmTest/.../storerender/StoreRender.kt` | devices, languages, the renderer, demo-photo loading |
| `storerender/StoreScenes.kt` | the scene registry (`storeScenes`) and the test; one scene = one real screen with demo state |
| `storerender/PreviewScenes.kt`, `SellerScenes.kt` | scene groups; `StoreDemo.kt` and `SellerDemo.kt` hold per-language demo content |
| `storerender/StoreChrome.kt` | `AppChrome`: the app's tab navigation (bottom bar on phones, rail on tablets) around a top-level screen |
| `composeApp/src/jvmTest/resources/store-demo/` | demo photos and their `LICENSES.md` |
| `store/tools/scenes.json` | **the manifest**: which scene, theme and caption fills each store slot, with a `why` |
| `store/copy/copy.json` | captions per language, keyed by caption name |
| `store/tools/generate-store-screenshots.mjs` | targets (sizes, frames, store languages) and the HTML layout |
| `store/tools/upload-screenshots.rb` | App Store Connect + Google Play upload, dry run by default |

Store rules, sizes, slot types and console state: [reference.md](reference.md). Read it before you change
a size, add a slot or upload.

## Steps

### 1. Scope

Name the scenes, devices and languages, and whether captions change. A caption is truthful only for
the screen under it: a new screen needs a caption, a changed screen needs its caption re-read.
Done when the request reads "scenes × devices × languages, captions changed or not".

### 2. Render

Run the render for the scope. A new scene is a `StoreScene` in a group file: the real screen composable
with demo state, never a view model (Adding a scene, below).
Done when every PNG of the scope exists and is newer than your last code change.

### 3. Look at every capture

Downscale (`magick in.png -resize 600x out.png`) and open each capture of the scope, light and dark.
Each one shows real photos (a grey box means a photo name is missing in `store-demo/`), no spinner or
shimmer, nothing cut or overlapping, the right language and the market's currency.
Done when every capture of the scope passes, or its defect is written down with the cause.

### 4. Captions

A new or reworded caption is a sentence the owner approves before translation (AGENTS.md rule 6): post
`key → English / Ukrainian`, wait, then translate all store languages (en uk pl ro bg pt kk) in one
`localize` run on `store/copy/copy.json`. Ukrainian captions say «ШІ», as the app does. A caption sells
the saved effort ("Your listing writes itself"), never waiting. Reuse an existing caption when it is
true of the screen: it is already approved and translated.
Done when every `copy` key in `scenes.json` exists in every store language of `copy.json`.

### 5. Compose

Edit `scenes.json` (order = store order; the first three show in App Store search), then run the
compositor. It stops a language with a missing capture and flags two slots with the same capture.
Done when it exits 0 with no `Problems` line.

### 6. Review, one language first

Open `store/previews/<target>-uk.jpg` (Ukrainian: the language the owner reads) and judge every image
against its caption: the caption is true of what the screen shows (a "Pending" status under "your
listing is live" is a lie), the headline fits in two lines, nothing overlaps the device, the status bar
reads well. Fix and re-run until the whole Ukrainian set is right. Then open the sheets of the longest
languages (kk, ro, pt, bg) for wrapping and overflow.
Done when every image of every target passes in uk, and the long languages have no clipped caption.

### 7. Upload

Dry run first; it prints every set it would replace. App Store writes only into an editable version
(not in review, not on sale): when the latest is locked, the upload waits for the next version, which
`sellsnap-release` creates. iPhone Duo images have no API slot: the owner drags
`store/assets/app-store/iphone-duo-*/<lang>/` into App Store Connect by hand. Play `--apply` commits the
edit, which publishes the listing change at once: the owner's request to upload is the approval.
Done when the dry run matches the plan and `--apply` reports every set replaced, or the blocker is
reported with what the owner must do.

## Adding a scene

- Render the screen's stateless composable (`...Content`, `...Screen`). When it is `private`, make it
  `internal`; when the stateless one does not exist, extract it from the `...Route` mechanically. Those
  are the only app-code changes a scene may need.
- The harness wraps every scene in `AppTheme`, `LocalInspectionMode = true` (Coil then loads through
  the demo handler synchronously, looping effects stop) and a status-bar / home-bar strip, because the
  JVM has no window insets. Photos: `demoPhoto("bike")` for URLs, `demoFile(name)` for picked files.
- A top-level tab screen (New listing, My Ads, Messages) sits inside `AppChrome(tab)` with
  `fullBleedBottom = true`, as the app shows it with its navigation. Pushed screens (preview,
  published, a phone conversation) have none. Wide windows show Messages as list + conversation
  side by side (`messages_two_pane`), mirroring `ListDetailSceneStrategy` in `App.kt`.
- A composable that calls `koinInject()` (e.g. `CopyPill` wants `Analytics`) needs a Koin app around
  the scene with a silent fake; `PreviewScenes.kt` has one.
- Content taller than the frame cannot scroll in a still: pre-scroll with a passed `ScrollState`, or
  scale the scene through `LocalDensity` (as `published` does) and say so in its `why`.
- Demo content: every person fictional (placeholder seller `ScreenshotPlaceholderAccount`), each
  language natural for its market (Kazakh for kk, never russian), prices in the market currency
  (`StoreLang.country`) at a realistic used price. A listing's title names what its photo shows (a
  compact Pentax is never titled as a Canon DSLR). A new photo is CC0 / public domain, looks like a
  seller's own photo, and gets a row in `store-demo/LICENSES.md`.
- Gradle: one task at a time; at most two agents build at once (AGENTS.md).
