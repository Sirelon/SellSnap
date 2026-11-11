# Skill: Maestro Auth-Screen Screenshots

Take localized auth-screen screenshots across platforms (iPhone, iPad, Android phone, Android tablet) using Maestro for UI automation. This skill encodes every non-obvious insight discovered in practice — read it fully before writing any file.

---

## When to invoke

User asks to take screenshots of a screen (auth, onboarding, etc.) across locales/countries/platforms using Maestro.

---

## Folder structure

```
screenshots/
├── iphone/         <country>/auth_light.png   portrait
│                   <country>/auth_dark.png
├── ipad/           <country>/auth_light.png   landscape
│                   <country>/auth_dark.png
├── android-phone/  <country>/auth_light.png   portrait
│                   <country>/auth_dark.png
└── android-tablet/ <country>/auth_light.png   landscape (via wm size, no rotation)
                    <country>/auth_dark.png
```

Use country codes (pt, ro, pl, ua, bg…), not locale codes, as folder names.
The `THEME` env var (`light` or `dark`) is always passed from the script.

---

## Critical Maestro gotchas

### `takeScreenshot` always appends `.png`
Do NOT include `.png` in the path — Maestro adds it automatically.
```yaml
# Wrong:
- takeScreenshot:
    path: "screenshots/${PLATFORM}/${COUNTRY}/auth_${THEME}.png"

# Correct:
- takeScreenshot:
    path: "screenshots/${PLATFORM}/${COUNTRY}/auth_${THEME}"
```

### `env:` block in flow YAML overrides CLI `-e` args
If you declare defaults in the flow header, they shadow CLI-passed values.
```yaml
# Wrong — LOCALE default blocks the CLI value:
env:
  LOCALE: default

# Correct — omit the env block entirely; rely on CLI -e:
# (no env: block)
```

### `--locale` flag does not exist for local `maestro test`
It only works with `maestro cloud`. For local runs, set locale via platform tools (see below).

### `setOrientation` needs `waitForAnimationToEnd`
```yaml
- setOrientation: LANDSCAPE_LEFT
- waitForAnimationToEnd   # without this, screenshot captures mid-rotation
```

### Conditional orientation in a flow
```yaml
- runFlow:
    when:
      true: ${ORIENTATION == 'landscape'}
    commands:
      - setOrientation: LANDSCAPE_LEFT
      - waitForAnimationToEnd
```

---

## Flow file templates

### `.maestro/auth_screenshot.yaml` (iOS / shared)
```yaml
appId: com.example.app
name: Auth Screen Screenshot
tags:
  - screenshot
---
- runFlow: subflows/launch_through_consent.yaml

- runFlow:
    when:
      true: ${ORIENTATION == 'landscape'}
    commands:
      - setOrientation: LANDSCAPE_LEFT
      - waitForAnimationToEnd

- takeScreenshot:
    path: "screenshots/${PLATFORM}/${COUNTRY}/auth_${THEME}"
```

### `.maestro/auth_screenshot_android.yaml`
Android needs a separate flow because locale must be injected between `clearState` and `launchApp` — see Android locale section below.
```yaml
appId: com.example.app
name: Auth Screen Screenshot (Android)
tags:
  - screenshot
---
# clearState + locale are handled by the script before this flow runs.
- runFlow: subflows/launch_through_consent_no_clear.yaml

- takeScreenshot:
    path: "screenshots/${PLATFORM}/${COUNTRY}/auth_${THEME}"
```

### `.maestro/clear_state_only.yaml`
```yaml
appId: com.example.app
---
- clearState
```

### `.maestro/subflows/launch_through_consent_no_clear.yaml`
Copy of `launch_through_consent.yaml` with `clearState` removed — used after the script has already cleared state and set locale.

---

## Dark / light theme

Both scripts loop over `light dark` by default. Override with `THEMES=dark` to capture only one.

### Android dark mode toggle
```bash
# Enable dark mode
adb -s "$DEVICE" shell cmd uimode night yes
sleep 1   # let the system redraw

# Disable (restore light)
adb -s "$DEVICE" shell cmd uimode night no
```

### iOS dark mode toggle
```bash
xcrun simctl ui "$UDID" appearance dark
sleep 1
xcrun simctl ui "$UDID" appearance light   # restore after run
```

Pass `-e THEME="$theme"` to every Maestro invocation so the flow saves to the right path.

---

## iOS locale setup

### How it works
Set locale via `xcrun simctl spawn` before each Maestro run. The flow's `clearState` + `launchApp` sequence restarts the app which picks up the new locale.

```bash
xcrun simctl spawn "$UDID" defaults write -g AppleLanguages '("fr")'
xcrun simctl spawn "$UDID" defaults write -g AppleLocale "fr_FR"
# Then run maestro — the app reads locale fresh on launch
```

### Locale format
- `AppleLanguages`: BCP-47 with dashes — e.g. `pt-PT`, `uk`, `zh-Hans`
- `AppleLocale`: underscore form — e.g. `pt_PT`, `uk_UA`, `zh_CN`
- Conversion: `lang_tag="${locale//_/-}"` and `locale_id="${locale//-/_}"`

### Common country → locale mapping
| Country | AppleLanguages | AppleLocale |
|---------|---------------|-------------|
| pt      | pt-PT         | pt_PT       |
| ro      | ro            | ro_RO       |
| pl      | pl            | pl_PL       |
| ua      | uk            | uk_UA       |
| bg      | bg            | bg_BG       |

### iOS script template
```bash
UDID="${UDID:-<simulator-udid>}"
PLATFORM="${PLATFORM:-iphone}"
ORIENTATION="${ORIENTATION:-portrait}"
THEMES="${THEMES:-light dark}"

for theme in $THEMES; do
  xcrun simctl ui "$UDID" appearance "$theme"
  sleep 1
  for entry in "pt|pt-PT|pt_PT" "ro|ro|ro_RO" "pl|pl|pl_PL" "ua|uk|uk_UA" "bg|bg|bg_BG"; do
    country="${entry%%|*}"; rest="${entry#*|}"; lang="${rest%%|*}"; locale="${rest##*|}"
    mkdir -p "screenshots/$PLATFORM/$country"
    xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")"
    xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale"
    maestro test --udid "$UDID" \
      -e COUNTRY="$country" -e PLATFORM="$PLATFORM" -e ORIENTATION="$ORIENTATION" \
      -e THEME="$theme" \
      .maestro/auth_screenshot.yaml
  done
done
xcrun simctl ui "$UDID" appearance light  # reset
```

### Building and installing the iOS app
```bash
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination "id=<UDID>" \
  -configuration Debug \
  -derivedDataPath /tmp/app-build \
  build

xcrun simctl install <UDID> /tmp/app-build/Build/Products/Debug-iphonesimulator/<AppName>.app
```

---

## Android locale setup

### The core problem
`pm clear` (which Maestro's `clearState` uses) wipes the per-app locale set by `cmd locale set-app-locales`. You cannot set locale before `clearState` — it gets wiped.

### The solution: two Maestro invocations
```
maestro(clear_state_only.yaml)   →   adb set-app-locales   →   maestro(launch_no_clear + screenshot)
```

```bash
# Step 1: clear via Maestro (safe — avoids raw adb pm clear which can crash emulator)
maestro test --device "$DEVICE" .maestro/clear_state_only.yaml

# Step 2: set locale now that data is cleared
adb -s "$DEVICE" shell cmd locale set-app-locales com.example.app --locales ro-RO

# Step 3: launch + screenshot (no clearState in this flow)
maestro test --device "$DEVICE" \
  -e COUNTRY=ro -e PLATFORM=android-phone -e THEME=light \
  .maestro/auth_screenshot_android.yaml
```

### Key `cmd locale` facts
- Command: `set-app-locales` (with **s** — not `set-app-locale`)
- Requires API 33+ (Android 13)
- Syntax: `cmd locale set-app-locales <package> --locales <locale>`
- Example: `adb shell cmd locale set-app-locales com.example.app --locales uk-UA`

### What NOT to do
- **Do NOT** run `adb shell pm clear` directly from a shell script — it can crash the emulator adb connection
- **Do NOT** use `setprop persist.sys.locale` — requires zygote restart to take effect, very slow (~30s/locale)
- **Do NOT** use `adb root` in a per-locale loop — it restarts adbd and causes brief disconnects

### Android common country → locale mapping
| Country | Locale  |
|---------|---------|
| pt      | pt-PT   |
| ro      | ro-RO   |
| pl      | pl-PL   |
| ua      | uk-UA   |
| bg      | bg-BG   |

---

## Android tablet via resizable emulator

Do NOT rotate the device. Just override the display dimensions — the wider-than-tall aspect ratio is landscape enough for screenshots.

```bash
# Before the loop:
adb -s "$DEVICE" shell wm size 1920x1200
adb -s "$DEVICE" shell wm density 240
sleep 2  # let the system settle

# After the loop:
adb -s "$DEVICE" shell wm size reset
adb -s "$DEVICE" shell wm density reset
```

Do NOT pass `ORIENTATION=landscape` to the Android flow — `setOrientation` in Maestro rotates the device which conflicts with the `wm size` approach. Pass `ORIENTATION=portrait` (or omit it) and let the dimensions do the work.

---

## Adding test media to the device gallery

Use `scripts/maestro-add-media.sh` to push images into the device's photo gallery before flows that require picking photos (e.g. listing creation).

### Shell script (auto-scans `.maestro/assets/`)
```bash
# Add all files in .maestro/assets/  (png, jpg, jpeg, gif, mp4)
./scripts/maestro-add-media.sh

# Add specific files
./scripts/maestro-add-media.sh path/to/a.jpg path/to/b.png

# Pin a device
DEVICE=emulator-5554 ./scripts/maestro-add-media.sh
```

The script generates a temporary YAML on the fly with absolute paths and runs `maestro test` — no permanent flow file needed for the dynamic case.

### Inline subflow (fixed asset list)
Reuse from another flow when you need gallery seeding as a setup step:
```yaml
- runFlow: subflows/add_media.yaml
```
`add_media.yaml` references `../assets/photo_1.jpg`, `photo_2.jpg`, `photo_3.jpg` — update the list when assets change.

### `addMedia` notes
- Supported formats: `png`, `jpg`, `jpeg`, `gif`, `mp4`
- Does not require the app to be running; runs before `launchApp` is fine
- Paths in YAML are relative to the flow file; absolute paths are safer when generating YAML from a script
- **Does NOT work in Maestro Studio** — only works with `maestro test` (CLI). Pre-load photos externally via `maestro-add-media.sh` before opening Studio if you need to test gallery-picker flows interactively.

---

## Selecting photos from the system photo picker

When a flow taps the app's gallery button, the system photo picker opens outside the app. Use platform-specific resource IDs — never coordinates, which break across device sizes.

### Resource IDs by platform / OS version

```yaml
# Android SDK 33/34 (modern Media Provider)
id: 'com.google.android.providers.media.module:id/icon_thumbnail'

# Android SDK 30 fallback (DocumentsUI)
id: 'com.google.android.documentsui:id/thumbnail'

# iOS 18+ (new grid layout)
id: 'PXGGridLayout-Info'

# iOS ≤17 (accessibility label regex)
text: 'Photo, .*'
```

Use `index: N` (0-based) to select the Nth photo. After `addMedia`, freshly added photos appear first (index 0, 1, 2 …).
Mark every picker tap `optional: true` — the wrong-version ID fails silently and the correct one lands.

### Full pattern (3 photos, both platforms)

```yaml
- runFlow:
    when:
      platform: Android
    commands:
      - tapOn:
          id: 'com.google.android.providers.media.module:id/icon_thumbnail'
          index: 0
          optional: true
      - tapOn:
          id: 'com.google.android.documentsui:id/thumbnail'
          index: 0
          optional: true
      - tapOn:
          id: 'com.google.android.providers.media.module:id/icon_thumbnail'
          index: 1
          optional: true
      - tapOn:
          id: 'com.google.android.documentsui:id/thumbnail'
          index: 1
          optional: true
      - tapOn:
          id: 'com.google.android.providers.media.module:id/icon_thumbnail'
          index: 2
          optional: true
      - tapOn:
          id: 'com.google.android.documentsui:id/thumbnail'
          index: 2
          optional: true

- runFlow:
    when:
      platform: iOS
    commands:
      - tapOn:
          id: 'PXGGridLayout-Info'
          index: 0
          optional: true
      - tapOn:
          text: 'Photo, .*'
          index: 0
          optional: true
      - tapOn:
          id: 'PXGGridLayout-Info'
          index: 1
          optional: true
      - tapOn:
          text: 'Photo, .*'
          index: 1
          optional: true
      - tapOn:
          id: 'PXGGridLayout-Info'
          index: 2
          optional: true
      - tapOn:
          text: 'Photo, .*'
          index: 2
          optional: true

# Confirm multi-selection.
# Android 13+ button text is "Add N" (N = count); earlier versions = "Add" or "Done".
- tapOn:
    text: "Add.*"     # Maestro text: supports regex; matches "Add", "Add 3", "Add (3)"
    optional: true
- tapOn:
    text: "Done"
    optional: true
```

### Dynamic count with a repeat loop

When the number of photos is parameterised (e.g. `count` env var), use an `evalScript` counter:

```yaml
- evalScript: ${output.counter = 0}
- repeat:
    times: ${count}
    commands:
      # open picker per-photo (for apps that accept one file at a time)
      - tapOn: "GalleryButton"
      - runFlow:
          when:
            platform: Android
          commands:
            - tapOn:
                id: 'com.google.android.providers.media.module:id/icon_thumbnail'
                index: ${output.counter}
                optional: true
            - tapOn:
                id: 'com.google.android.documentsui:id/thumbnail'
                index: ${output.counter}
                optional: true
      - runFlow:
          when:
            platform: iOS
          commands:
            - tapOn:
                id: 'PXGGridLayout-Info'
                index: ${output.counter}
                optional: true
            - tapOn:
                text: 'Photo, .*'
                index: ${output.counter}
                optional: true
      - evalScript: ${output.counter = output.counter + 1}
```

> **Gotcha:** `FAST_MODE` and `count` are only available as YAML template vars (`${FAST_MODE}`, `${count}`), **not** as `env.FAST_MODE` inside `evalScript`/`runScript` JS contexts (in Maestro 2.5.1, `env` is undefined there). Guard entire blocks with `when: true: ${count > 0}` rather than checking inside JS.

---

## Content-screen screenshot flows

Auth-screen flows are stateless — they always start fresh. For screens that need **logged-in state + app content** (e.g. the main create-listing screen with photos attached), compose the flow from a login subflow and a content-preparation subflow.

### Pattern

```yaml
appId: com.example.app
tags:
  - screenshot
  - photos
# COUNTRY, OLX_EMAIL, OLX_PASSWORD required at runtime — same as login.yaml.
# Do NOT declare env defaults here (they shadow -e overrides — see gotcha above).
---
# 1. Full login → home screen. Env vars flow through automatically.
- runFlow: login.yaml

# 2. Attach photos, navigate to content state, scroll to bottom.
- runFlow: subflows/attach_photos_from_gallery.yaml
```

### `attach_photos_from_gallery.yaml` subflow template

```yaml
appId: com.example.app
---
- runFlow: add_media.yaml               # push test photos to gallery first

- tapOn:
    id: "gallery_button_test_tag"       # in-app gallery button

- waitForAnimationToEnd:
    timeout: 3000

# … picker taps (see "Selecting photos" section above) …

# Confirm
- tapOn:
    text: "Add.*"
    optional: true
- tapOn:
    text: "Done"
    optional: true

# Wait for photos to appear in the app
- extendedWaitUntil:
    visible:
      id: "photos_grid_test_tag"
    timeout: 15000

# Scroll to bottom for the bottom screenshot
- scrollUntilVisible:
    element:
      id: "last_item_test_tag"
    direction: DOWN
    timeout: 10000
```

### Scroll to top / bottom for screenshots

```yaml
# Scroll to TOP (for top-of-screen screenshot)
- scrollUntilVisible:
    element:
      id: "first_visible_test_tag"
    direction: UP
    timeout: 5000

# Scroll to BOTTOM (for bottom-of-screen screenshot)
- scrollUntilVisible:
    element:
      id: "last_item_test_tag"
    direction: DOWN
    timeout: 10000
```

Add `testTag(…)` to the first and last items in the scrollable list so `scrollUntilVisible` has reliable anchors. The test tags need `testTagsAsResourceId = true` in the Android host activity.

---

## `config.yaml` — prevent subflows from running standalone

```yaml
# .maestro/config.yaml
flows:
  - login.yaml
  - guest.yaml
  # add screenshot flows here if you want them in the default suite
```

Without this, `maestro test .maestro/` will try to run subflows as top-level flows and fail.

---

## Checklist for a new project

- [ ] Find iOS bundle ID (`grep PRODUCT_BUNDLE_IDENTIFIER iosApp/Configuration/Config.xcconfig`)
- [ ] Find Android package name (`applicationId` in `build.gradle`)
- [ ] Build and install the iOS app on the target simulator
- [ ] Verify Android app is installed (`adb shell pm list packages | grep <pkg>`)
- [ ] Create `launch_through_consent.yaml` (or equivalent) that ends on the target screen
- [ ] Create `launch_through_consent_no_clear.yaml` (same, no `clearState`)
- [ ] Create `clear_state_only.yaml`
- [ ] Create `auth_screenshot.yaml` (iOS) and `auth_screenshot_android.yaml` (Android)
- [ ] Test single locale + single theme before running all countries
- [ ] Verify locale is actually applied (take a screenshot, read the UI text)
- [ ] Verify dark mode is applied (check background color in screenshot)

### Additional checklist for content-screen flows

- [ ] Add `testTag(…)` to every element the flow needs to tap or wait for (gallery button, photos grid, first/last scroll anchor)
- [ ] Confirm `testTagsAsResourceId = true` is set in the Android host activity
- [ ] Place test photos in `.maestro/assets/` and reference them in `add_media.yaml`
- [ ] Verify picker photo order matches `index: 0/1/2` (newest photos should be first after `addMedia`)
- [ ] Test the picker ID on the target Android API level — modern emulators use `media.module`, older use `documentsui`
- [ ] Run the flow once without screenshots to confirm the content state is reached correctly before adding `takeScreenshot` calls
