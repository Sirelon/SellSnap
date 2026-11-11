# Reference: SellSnap screenshot captures

Companion to [SKILL.md](SKILL.md). Read the section that matches the symptom or the platform.

## Flows and scripts: what the names hide

- The `*_dark_only` flows capture both themes. iPad `result`, Android `result` and the iPhone dark pass all use `result_screenshots_dark_only.yaml`. `generate_ad_screenshots_dark_only.yaml` serves iPhone and iPad the same way.
- Only the iPhone light pass (`result_screenshots.yaml`) takes `analysing_progress_light`. It takes the shot only while the analysing screen is still visible. A locale that generates too fast has no such file. The store manifest reports that as a missing source.
- `analysing_start_*` is also taken only while the analysing screen is visible.
- iPad `generate_ad` uses `generate_ad_screenshots_no_login.yaml`, which calls `launchApp` without `clearState`. It needs an existing session on the iPad.
- Android scripts run `setup_for_country_android.yaml` (clears state, walks onboarding, country picker, "Continue with OLX"), set the app locale, pause for the owner, then run the capture flow for both themes.
- `generate_ad_screenshots_android.yaml` has no script. `config.yaml` lists it. It calls `olx_web_login.yaml`, which types the password (see the next section).
- `config.yaml` lists the flows that `maestro test .maestro/` runs. Subflows under `.maestro/subflows/` stay out, because a subflow run alone fails.
- `maestro-auth-screenshots.sh` (the many-locale loop) is broken: it passes `--locale`, which only `maestro cloud` accepts, and it writes to a path the flow no longer uses. Use the `-ios` and `-android` auth scripts.

## Login hand-off

OLX bot detection blocks automated login. The account gets banned.

- **Android.** `olx_web_login.yaml` types `OLX_PASSWORD` into the Chrome Custom Tab form when the `username` or `email` id is visible. Run only the Android scripts, which use the two-phase setup. Keep `login.yaml`, `generate_ad_screenshots_android.yaml` and `scripts/maestro-login.sh` off Android.
- **iOS password fields are hidden from the accessibility tree.** Automation cannot type them. The flow fills the email, presses Tab, and waits up to 5 minutes for the owner to type the password and press Enter.- **iPhone** logs in per country through `login.yaml`. Dark mode reuses the session.
- **iPad** shows the auth-consent dialog as a SpringBoard window. Maestro's runner is sandboxed to the app process and cannot tap SpringBoard. A `tapOn` at Cancel or Continue reports COMPLETED and does nothing. The iPad `result` script runs in two phases. Phase 1 (`setup_for_country.yaml`) clears state, walks to the country picker, taps "Continue with OLX" and exits. The script then pauses, plays a notification and a voice alert, and prints the Continue label for the locale. The owner taps Continue in the grey dialog, types the password, presses Return, then presses Enter in the terminal. Phase 2 runs from the logged-in home screen.
- **Android** works the same way in two phases: the Custom Tab opens, the owner types the password and presses Return, then presses Enter in the terminal once SellSnap's home screen shows.
- Auth-sheet Continue labels: pl=Dalej, pt=Continuar, ro=Continuați, bg=Продължи, ua=Продовжити. Cancel labels, used to detect the sheet: pl=Anuluj, pt=Cancelar, ro=Anulare, bg=Отказ, ua=Скасувати.
- **Read dialog strings from the runtime and never guess them.** Two outages came from guessed text (pl is "Dalej", not "Kontynuuj"):
  ```bash
  RR="$(xcrun simctl getenv <udid> SIMULATOR_ROOT)"
  plutil -p "$RR/System/Library/Frameworks/AuthenticationServices.framework/pl.lproj/Localizable.strings" | grep -E '"(Continue|Cancel)" =>'
  ```
- Anchor selectors to exact words (`^dalej$`), never to prefixes. `continu.*` also matches the app's own "Continue · olx.pt" button. Prefer bypassing a dialog over tapping it: `xcrun simctl privacy <udid> grant location <bundle-id>`.
- After a failed login, read the Maestro log for the step that failed. A scroll or assertion timeout further down the flow looks the same from outside.
- If `home_root` timed out, ask the owner what he saw. The Maestro viewer at http://127.0.0.1:9999/ shows the last screenshot. A form on the Register tab instead of Login means the flow needs investigation. Re-run only that country (`COUNTRIES="xx"`).
- One OLX account serves every country. The account carries no market. The UI language comes from the device locale (`composeResources/values-<lang>`). The AI text language comes from the picked country (`OpenAIClient` uses `country.language`). `OLX_EMAIL_<CC>` is an optional per-country override in the iPhone and iPad `result` scripts. It is unset in `.maestro/.env`, so `OLX_EMAIL` applies.
- Credentials live in `.maestro/.env` (gitignored). `.maestro/.env.example` is the template. Never write them into a tracked file.

## iOS simulator

- Locale: the scripts write `AppleLanguages` (BCP-47 with dashes: `pt-PT`, `uk`) and `AppleLocale` (underscores: `pt_PT`, `uk_UA`) with `xcrun simctl spawn <udid> defaults write -g`. The app reads them on launch.
- Theme: `xcrun simctl ui <udid> appearance light|dark`, followed by `sleep 1`. The scripts reset to light at the end.
- The `result` scripts set a capital-city GPS fix (`xcrun simctl location`) and pre-grant location (`simctl privacy`). The preview screen requests location on composition. Without both, the location card says "couldn't find your location". The fix stays set after the run.
- Location is a required field. With a location set, the ad validates and the publish button is green ("Publish on OLX"). Without one it is orange ("Publish · 1 to fix"). Drop the `lat,lon` column in a script's country table to capture the error state.
- **iPad raw framebuffer.** `simctl io … screenshot` on a landscape iPad writes a 2064x2752 portrait PNG with the UI rotated 90 degrees counter-clockwise and no EXIF tag. `scripts/normalize-ipad-screenshots.sh` rotates it to 2752x2064. It is idempotent and only touches portrait files.
- Capture identity: a screenshot's pixel size differs per model. `xcrun simctl io <udid> screenshot` tells you which device you are talking to.

## Android emulator

- **`pm clear` wipes the per-app locale.** `clearState` does the same. Set the locale after the clear: clear, then `cmd locale set-app-locales com.sirelon.sellsnap --locales <tag>`, then launch. The command is `set-app-locales` with an "s". The scripts verify with `get-app-locales`, because a wrong locale is invisible until review.
- Tags: pt-PT, ro-RO, pl-PL, uk-UA (ua), bg-BG.
- Clear through Maestro (`clear_state_only.yaml`), never with a raw `adb shell pm clear` from a shell script: it can crash the emulator's adb connection.
- `setprop persist.sys.locale` needs a zygote restart (about 30 s per locale). `adb root` in a per-locale loop restarts adbd and drops the connection. Neither is a way to switch locale.
- Theme: `adb shell cmd uimode night yes|no`, then `sleep 1`. The scripts reset to light.
- Tablet is a resized emulator: `wm size 1920x1200` and `wm density 240`. Phone `generate_ad` and `result` runs force `wm size 1080x1920` and `wm density 420`. Both scripts reset on exit. **Never rotate the device** for the tablet: `setOrientation` rotates and conflicts with `wm size`. The scripts pass `ORIENTATION=portrait` on purpose.
- The auth script's phone mode resets to the AVD's native size instead of forcing 1080x1920. The committed `android-phone` set mixes sizes: `auth` is 1080x2400, the other files are 1080x2340. The store generator sizes its phone screen for the 1080x2340 files, so `auth.png` loses about 2% of its height there.
- A physical device keeps its native panel. The `result` scripts refuse a physical device unless `ALLOW_REAL_GPS=1`, because `adb emu geo fix` (longitude first, then latitude) exists on emulators only.

## Maestro flow gotchas

- `takeScreenshot` appends `.png`. Leave it out of `path:`.
- A flow-level `env:` default shadows the `-e` value from the CLI. Declare no `env:` block. A `COUNTRY` default silently pins the country.
- `setOrientation: LANDSCAPE_LEFT` needs `waitForAnimationToEnd` after it. Without the wait the screenshot catches the rotation. The flows gate it on `${ORIENTATION == 'landscape'}`.
- In Maestro 2.5.1, `env` was undefined inside `evalScript` and `runScript`. Use YAML template variables and guard whole blocks with `when: true: ${...}`. Maestro 2.6.0 is installed; the flows use no `evalScript`, so this is unchecked on 2.6.0.
- Cyrillic text inside `when: visible: text:` breaks the Maestro parser. `olx_web_login.yaml` branches on `COUNTRY` for BG instead.
- Selector rules (`testTag` ids, dialogs, `launchApp` permissions): `.claude/rules/maestro.md`. The ids reach Maestro because `MainActivity` sets `testTagsAsResourceId`.
- `clearState` wipes Coil's disk cache, so carousel images are a cold fetch. `ImageShimmer` runs an infinite transition, so `waitForAnimationToEnd` never settles on a loading image. Wait on `async_image_loaded`.
- The `result_bottom` scroll targets `preview_ad_location_card` at 60% visibility with a 40 s timeout. A blind `swipe` silently no-ops when a child eats the drag. The sticky publish button covers the card's lower edge. The BG content is the tallest.
- A scrolled screenshot needs anchors: `testTag` on the first and last items of the list.
- `subflows/wait_for_photos.yaml` waits for `generate_ad_photo_remove` at `index: 2`. One such node exists per loaded photo, so it proves all three arrived.
- macOS ships bash 3.2: no `${var^^}`, `${var,,}`, `declare -A` or `mapfile` in the scripts.

## Photos

`screenshotMode = true` makes `GenerateAdViewModel` seed the three bundled photos (`composeResources/files/screenshot_photo_{1,2,3}.jpg`) through the normal prepare, persist and upload path. No flow opens the OS picker.
Reason: on iPad, iOS 26 presents `PHPickerViewController` as a popover that exposes nothing to the accessibility tree, only a `PopoverDismissRegion`. A coordinate tap selects the wrong photos when the popover reflows.
To exercise the real picker by hand: `scripts/maestro-add-media.sh` pushes `.maestro/assets/*` into the gallery (`DEVICE=<udid>` uses `simctl addmedia`). `addMedia` works with `maestro test` only, not in Maestro Studio. Picker ids, if a flow ever needs them: Android 33/34 `com.google.android.providers.media.module:id/icon_thumbnail`, Android 30 `com.google.android.documentsui:id/thumbnail`, iOS 18 `PXGGridLayout-Info` (iOS 17 and older: text `Photo, .*`). Mark picker taps `optional: true`, because the wrong-version id fails silently. The confirm button reads "Add N" on Android 13+.

## Add a country

1. The app exposes `country_row_<code>` in the country picker and has a `values-<lang>` resource folder for the language.
2. Add a row to the country table in every script you will run. iOS rows hold `AppleLanguages` and `AppleLocale`, Android rows the locale tag. `result` rows add the capital's `lat,lon`.
3. Add the Continue label to the `cont_label` case in the iPad `result` script. Add the Cancel label to `olx_web_login.yaml`. Read both from the runtime with the `plutil` command above.
4. Check the OLX login form of the new country. `olx_web_login.yaml` has per-country branches (RO, BG, PL, PT forms, each with its own labels) and a per-locale cookie and "agree" text set. Extend them for the new form.
5. Capture one country first (SKILL.md step 5). Then follow the `app-store-screenshots` runbook "add a locale" for the manifest mapping.

## New screen

Add a flow in `.maestro/`: id-based selectors, a `takeScreenshot` path of `store/captures/${PLATFORM}/${COUNTRY}/<name>_${THEME}`, and a runner script modelled on the nearest existing one. Put a `testTag` on everything the flow taps or waits for. Run the flow once without `takeScreenshot` to confirm it reaches the state.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `maestro hierarchy`, Maestro MCP or screenshots show the wrong device; working testTags look missing | Two iOS simulators booted, so two drivers fight over one port | `pkill -9 -f "simulator-server ios"; pkill -9 -f maestro-driver-iosUITests-Runner`. Drive one device. Check identity with `xcrun simctl io <udid> screenshot` |
| A testTag looks absent while `home_root` resolves | Stale build | Reinstall from current source, then conclude |
| Script exits "screenshotMode is false" | Source says `false` | Flip, rebuild, reinstall |
| "Can't find service: locale", or a whole set in one language | Android below API 33 | Use an API 33+ emulator |
| Generation fails, the proxy answers 401 | No registered App Check token | `appcheck-debug-token` skill |
| `result_top` is a blank gradient (about 300 KB) | Carousel image not loaded | Re-run the country; the `async_image_loaded` wait is optional and times out after 30 s |
| `result_bottom` identical to `result_top` | Scroll did not move | Check the `preview_ad_location_card` anchor and the 40 s timeout |
| An `analysing_*` file identical to a result file | Generation finished before the shot | Re-run the country; the flows guard the shot, so a fast generation leaves the file missing instead |
| iPad file is sideways | Raw framebuffer | `scripts/normalize-ipad-screenshots.sh` |
| iPad login hangs on the grey dialog | Maestro cannot tap SpringBoard | The owner taps Continue |
| Whole set in the wrong language | Locale not applied | Open one file per country (SKILL.md step 7) |
| Script default `UDID` fails | The UDIDs `C74C95D7-F29C-49D7-A281-E9D8DAAEDD59` (iPhone) and `1663ACA3-D833-471F-A56F-7095872F61A6` (iPad Pro 13-inch M5) are absent from `xcrun simctl list devices` on this machine | Pass `UDID=<udid>` |
