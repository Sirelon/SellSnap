# Skill: SellSnap Screenshots

Take localized screenshots of any SellSnap screen across all platforms and countries.

## When to use
User asks to take screenshots of a screen (e.g. "GenerateAd", "auth screen", "home") for all countries or specific platforms.

## Quick reference

**Active countries:** `ua`, `pt`, `pl`, `ro`, `bg` — all 5 supported on every platform

**Credentials:** stored in `.maestro/.env` (OLX_EMAIL / OLX_PASSWORD)

**Devices:**
- iPhone portrait: UDID `C74C95D7-F29C-49D7-A281-E9D8DAAEDD59`
- iPad landscape: UDID `1663ACA3-D833-471F-A56F-7095872F61A6` + `ORIENTATION=landscape`
- Android phone + tablet: resizable emulator `emulator-5554` (scripts default to this)
  — tablet mode: `wm size 1920x1200 / density 240` (handled automatically by the scripts)

## screenshotMode (do this first, revert it after)

`composeApp/.../features/seller/ad/ScreenshotMode.kt` holds `internal var screenshotMode`,
committed as `false`. **Every photo flow now requires `true`** (GenerateAd + result). It is a
plain source constant, so each session is:

1. flip to `true`
2. rebuild and reinstall the iOS app (`IOS_TARGET=<udid> run-ios`)
3. run the flows
4. **flip back to `false` before committing** — committing `true` ships the publish bypass

It enables two things: seeded test photos in GenerateAd (no picker) and the publish-confirm
bypass + direct back-navigation in PreviewAdScreen. AI generation is **not** faked — every run
makes real OpenAI calls (~1–2 min per generation, ×2 per country for light + dark).

The runner scripts now fail fast if the source still says `false`, but they cannot detect
"flipped but not rebuilt" — always reinstall after flipping.

## iOS login protocol (critical)

iOS hides `type="password"` fields from the accessibility tree — automation cannot type
passwords. The login flow fills the email, presses Tab, then waits up to 5 minutes for a
manual entry.

**In practice you usually will not need to type anything.** The OLX web session lives in the
shared `ASWebAuthenticationSession` cookie store, which Maestro's `clearState` does *not*
clear, so OLX normally auto-redirects and `home_root` appears on its own. Manual entry is only
needed when that cookie store is empty.

**Your job when running iPhone scripts:**
1. Before each country, tell the user the login form is opening and that if the form appears
   they should click the Simulator window, type the password from `.maestro/.env`, and press
   Enter. Do not paste the password into chat.
2. The flow waits automatically. No need to poll or ask "did you log in?".
3. Login happens once per country. Dark mode reuses the session (no second login).

### iPad login is different — two-phase manual process

On iPad, `ASWebAuthenticationSession` shows a system-level auth-consent dialog rendered by
**SpringBoard**, not the app process. Maestro's XCUITest runner is sandboxed to the app process
and **cannot tap SpringBoard dialogs**. Sending `tapOn` at the Cancel/Continue buttons reports
COMPLETED but does nothing — the dialog stays on screen until it times out.

The iPad result-screenshot script therefore uses a two-phase approach:

**Phase 1 (`setup_for_country.yaml`):** Maestro clears state, navigates through onboarding and
the country picker, and taps the OLX Continue button — which triggers the auth sheet — then
**exits immediately**. The shell script then pauses.

**Phase 2 (manual pause):** The script fires a macOS notification + voice alert and prints:
> Tap 'Dalej' (or locale equivalent) in the grey dialog, then type the OLX password and press
> Return in the Simulator. Then press Enter here to continue.

Once you press Enter, Maestro relaunches the app and takes screenshots from the logged-in
home screen using `result_screenshots_dark_only.yaml` (which works for both light and dark).

**This script MUST be run interactively in your own terminal** — background tasks cannot read
`stdin`, so the `read` pause will hang silently.

Localized Continue button text (auth sheet): pl=**Dalej** · pt=Continuar · ro=Continuați ·
bg=Продължи · ua=Продовжити. These are also the Cancel button counterparts used by
`olx_web_login.yaml` to detect the sheet: pl=Anuluj · pt=Cancelar · ro=Anulare · bg=Отказ ·
ua=Скасувати.

**NEVER retry a failed OLX login more than once** — the RO account was banned this way.
Before treating a failure as a login failure, check the Maestro log for the step that actually
failed: a scroll or assertion timeout further down the flow looks identical from the outside,
and the result script used to mislabel every failure as a login timeout.

## iOS system dialogs — never guess localized strings

Two separate outages came from guessed button text. When a flow must tap an iOS system dialog,
read the real strings out of the runtime instead:

```bash
RR="$(xcrun simctl getenv <udid> SIMULATOR_ROOT)"
plutil -p "$RR/System/Library/Frameworks/AuthenticationServices.framework/pl.lproj/Localizable.strings" \
  | grep -E '"(Continue|Cancel)" =>'
```

Verified for the auth-consent sheet (key `Continue`): en=Continue · pt=Continuar ·
ro=Continuați · pl=**Dalej** (not "Kontynuuj") · bg=Продължи · uk=Продовжити.

Anchor selectors to exact words (`^dalej$`), never prefixes — `continu.*` also matches the
app's own "Continue · olx.pt" button. Prefer bypassing the dialog entirely where possible, e.g.
`xcrun simctl privacy <udid> grant location <bundle-id>` instead of tapping Allow.

## Script paths

```bash
# iPhone — GenerateAd screens
./scripts/maestro-generate-ad-screenshots-ios.sh

# iPhone — Result / Analysing screens (needs screenshotMode = true)
./scripts/maestro-generate-ad-result-screenshots-ios.sh

# iPad (landscape, all 5 countries) — GenerateAd screens (no login, fully automated)
./scripts/maestro-generate-ad-screenshots-ipad.sh

# iPad (landscape, all 5 countries) — Result / Analysing screens
# *** INTERACTIVE — run from your own terminal, NOT as a Claude background task ***
# The script pauses once per country for manual auth-sheet tap + OLX login.
./scripts/maestro-generate-ad-result-screenshots-ipad.sh

# Single-country retry (works for all scripts above)
COUNTRIES="bg" ./scripts/maestro-generate-ad-result-screenshots-ipad.sh

# Android phone — GenerateAd screens (all 5 countries, fully automated)
./scripts/maestro-generate-ad-screenshots-android.sh

# Android tablet — GenerateAd screens (resizes emulator, resets after)
PLATFORM=android-tablet ./scripts/maestro-generate-ad-screenshots-android.sh

# Android phone — Result / Analysing screens (fully automated, ~2 min/country for OpenAI)
./scripts/maestro-generate-ad-result-screenshots-android.sh

# Android tablet — Result / Analysing screens
PLATFORM=android-tablet ./scripts/maestro-generate-ad-result-screenshots-android.sh

# Single-country retry
COUNTRIES="ua" ./scripts/maestro-generate-ad-result-screenshots-android.sh
COUNTRIES="bg" PLATFORM=android-tablet ./scripts/maestro-generate-ad-result-screenshots-android.sh
```

## Single-country retry

```bash
COUNTRIES="bg" ./scripts/maestro-generate-ad-screenshots-ios.sh
COUNTRIES="pt" ./scripts/maestro-generate-ad-result-screenshots-ipad.sh
```

Validate on one country before spending all logins — the scripts continue to the next
country on failure, so a broken flow will burn every login before you can react.

## Verify the output, never trust the exit code

Flows use `optional: true` in places, so a green exit can still hide a bad capture. Check:

```bash
cd screenshots/iphone
for c in pt pl bg; do echo "$c: $(ls $c | grep -cE 'analysing|result')"; done   # expect 10
# result_top with a loaded photo is ~1.9 MB; a blank gradient is ~300 KB
for c in pt pl bg; do for t in light dark; do
  echo "$c/$t $(stat -f%z $c/result_top_$t.png)"; done; done
# top and bottom must differ, and the three countries must differ from each other
for c in pt pl bg; do for t in light dark; do
  [ "$(md5 -q $c/result_top_$t.png)" = "$(md5 -q $c/result_bottom_$t.png)" ] \
    && echo "$c/$t SCROLL FAILED"; done; done
```

Then actually open one screenshot per country and confirm the language — a whole set once
shipped in Polish across all three folders while every step reported success.

## Photos are seeded, not picked (no manual step)

`screenshotMode = true` makes `GenerateAdViewModel` seed the three bundled photos from
`composeResources/files/` when the GenerateAd screen opens, pushing them through the normal
prepare → persist → upload path. No picker is opened on any platform, so the device gallery no
longer needs populating and iPad needs no manual clicking.

Why the picker is not automated: on iPad, iOS 26 presents `PHPickerViewController` as a popover
that exposes **nothing** to the accessibility tree — no `PXGGridLayout-Info`, no cells, only a
`PopoverDismissRegion` (verified on iPad Pro 13-inch, iOS 26.5, on two separate builds). It is
reachable by coordinate tap, but that silently selects the wrong photos whenever the popover
reflows (e.g. on rotation), so it was rejected. On iPhone the picker is a full-screen sheet and
*is* accessible, but seeding is used there too so all platforms share one path.

`subflows/wait_for_photos.yaml` waits on `generate_ad_photo_remove` with `index: 2` — one such
node exists per loaded photo, so it proves all three arrived, locale-independently.

## Known traps

- **Only ever boot ONE iOS simulator for a Maestro run.** Maestro starts one
  `simulator-server ios --id <udid>` per device and they fight over the same driver port. The
  loser is silently ignored: `maestro hierarchy`, the Maestro MCP tools and screenshots all
  return the **other** device's data even with a correct `--udid`. This makes working testTags
  look missing and sends you chasing phantom app bugs. Fix:
  `pkill -9 -f "simulator-server ios"; pkill -9 -f maestro-driver-iosUITests-Runner` and drive
  one device at a time. Sanity-check identity via `xcrun simctl io <udid> screenshot` (native
  pixel size differs per model).

- **A stale build looks exactly like a missing testTag.** An iPad build a few hours out of date
  reported `generate_ad_gallery_button`, `generate_ad_photos_grid` and `generate_ad_prompt` as
  absent while `home_root` resolved fine. Reinstalling from current source fixed all of them.
  Rebuild before concluding a tag is broken.

- **`scripts/maestro-add-media.sh` is no longer part of the screenshot path** (photos are
  seeded in-app). It is kept for manually exercising the real picker → upload path, which the
  screenshot flows deliberately no longer cover.

- **`clearState` wipes Coil's disk cache** (`NSTemporaryDirectory()/coil3_disk_cache`), so
  carousel images are always a cold fetch from Supabase. Never sync on
  `waitForAnimationToEnd` — `ImageShimmer` runs an infinite transition while loading, so the
  wait can never settle and just expires. Wait on the `async_image_loaded` tag instead.
- **macOS ships bash 3.2.** No `${var^^}`, `${var,,}`, `declare -A`, or `mapfile` in these
  scripts.
- **A single OLX account is used for every country.** The account carries no market: UI
  language comes from the device locale (`composeResources/values-<lang>`) and the AI text from
  the picked country (`OpenAIClient` uses `country.language`). `OLX_EMAIL_PT/PL/BG` lookups in
  the result script are dead code that always falls back to `OLX_EMAIL`.
- **Location is a required field.** With a simulated location set, ads validate fully and the
  CTA renders green ("Publish on OLX") instead of orange ("Publish · 1 to fix"). Drop the
  `lat,lon` column in the script's country table to get the error state back.

## Android login is manual (same reason as iOS)

OLX bot-detection blocks automated Chrome Custom Tab login — the account gets banned.
Both Android scripts use the same two-phase approach as iPad:

**Phase 1:** Maestro clears state, walks through onboarding → country picker → taps
"Continue with OLX" → Chrome Custom Tab opens. Script pauses.

**Phase 2:** You type the OLX password in the emulator and press Return. When you land
on the SellSnap home screen, press Enter in the terminal. Maestro takes screenshots.

One login per country (5 total). Dark mode reuses the session — no second login.

## Run order (to minimize total logins)

1. iPhone (5 manual logins; **run interactively from your terminal**)
2. iPad GenerateAd — `maestro-generate-ad-screenshots-ipad.sh` (no login, fully automated)
3. iPad Result/Analysing — `maestro-generate-ad-result-screenshots-ipad.sh`
   (5 manual logins; **run interactively from your terminal**)
4. Android phone GenerateAd — `maestro-generate-ad-screenshots-android.sh`
   (5 manual logins; **run interactively from your terminal**)
5. Android phone Result/Analysing — `maestro-generate-ad-result-screenshots-android.sh`
   (5 manual logins; **run interactively from your terminal**)
6. Android tablet GenerateAd — `PLATFORM=android-tablet maestro-generate-ad-screenshots-android.sh`
7. Android tablet Result/Analysing — `PLATFORM=android-tablet maestro-generate-ad-result-screenshots-android.sh`

Steps 4–7 use the same resizable emulator (`emulator-5554`). The scripts handle wm size
resize/reset automatically — no manual adb commands needed between runs.

## What to do if login fails

- Check the Maestro viewer at http://127.0.0.1:9999/ for the last screenshot
- If the form shows the wrong tab (Register vs Login), the flow needs investigation
- If `home_root` timed out: ask user what they saw and re-run that country only (`COUNTRIES="xx"`)
- NEVER attempt a third login if two have already failed for the same country
