---
name: sellsnap-screenshots
description: Capture raw, localized screenshots of SellSnap screens with Maestro on the iOS simulator, the iPad simulator and the Android emulator, per country, light and dark. The output is the input of the `app-store-screenshots` skill. Use for "take screenshots of <screen>", "capture the auth screen for all countries", "re-capture the iPad set", "screenshots for a new country".
---

# SellSnap screenshots

Output: `store/captures/<device>/<country>/<screen>_<theme>.png`.
Devices: `iphone`, `ipad` (landscape), `android-phone`, `android-tablet` (landscape).
Countries: `ua pt pl ro bg`. Themes: `light dark`.
The `app-store-screenshots` skill composites these files into store assets.
Quirks, the login hand-off in detail and troubleshooting: [reference.md](reference.md).

## Screens and scripts

| Screen | Files per country and theme | Needs |
| --- | --- | --- |
| `auth` | `auth` | nothing: no login, no `screenshotMode` |
| `generate_ad` | `generate_ad_top`, `generate_ad_bottom` | login, `screenshotMode` |
| `result` | `analysing_start`, `analysing_progress` (iPhone light only), `result_top`, `result_bottom`, `result_publish_dialog` | login, `screenshotMode`, App Check token, two real OpenAI generations per country |

Scripts, run from the repo root:

- `auth`: `maestro-auth-screenshots-ios.sh` (iPad: pass `UDID`, `PLATFORM=ipad`, `ORIENTATION=landscape`), `maestro-auth-screenshots-android.sh` (tablet: `PLATFORM=android-tablet`).
- `generate_ad`: `maestro-generate-ad-screenshots-{ios,ipad,android}.sh`.
- `result`: `maestro-generate-ad-result-screenshots-{ios,ipad,android}.sh`.
- Every script takes `COUNTRIES="bg pl"` for a partial run. A screen with no script needs a new flow: see reference.md.

## Steps

### 1. Scope the run

Name the screens, the devices and the countries. List what exists: `ls store/captures/<device>/<country>`.
Done when the request reads "screen × device × countries" and each cell maps to a script above.

### 2. Boot one device per platform

- **Boot only one iOS simulator.** Maestro starts one driver per device. The drivers contend for one port. Hierarchy and screenshot calls then silently return the other device's data.
- Get the UDID from `xcrun simctl list devices` and pass `UDID=<udid>`. The default UDIDs inside the scripts do not exist on this machine.
- iPhone captures are 1206x2622. iPad captures come from an iPad Pro 13-inch (M5). Check the pixel size with `xcrun simctl io <udid> screenshot`.
- **Android runs need API 33+.** Per-country language uses `cmd locale set-app-locales`, which does not exist below 33. The scripts refuse to start. Pass `DEVICE=<serial>` when more than one device is attached.

Done when `xcrun simctl list devices booted` lists at most one simulator, and an Android target answers `adb -s <serial> shell getprop ro.build.version.sdk` with 33 or more.

### 3. Prepare the app (`generate_ad` and `result` only)

1. **`screenshotMode` is committed as `false`.** Set it to `true` in `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt`. It seeds the three bundled photos and bypasses the publish confirmation. `scripts/ship.sh` refuses to release while it is `true`.
2. Rebuild and reinstall on the target: `IOS_TARGET=<udid> run-ios`, `ANDROID_SERIAL=<serial> run-android`. The scripts read the source file only, so they cannot detect "flipped but not rebuilt".
3. `result` only: **a debug build without a registered App Check token cannot generate listings.** With the fixed per-machine tokens (`local.properties` and `iosApp/Configuration/AppCheckDebugToken.local.xcconfig`) this is a one-time setup. Without them every `clearState` mints a new token, and the `appcheck-debug-token` skill must run before the flow.

Done when `grep -n "screenshotMode = true"` matches the file and the install finished after that edit.

### 4. Brief the owner and hand off the login

Every script that logs in costs the owner one OLX login per country. Dark mode reuses the session. The full matrix is 35 logins, so run only the cells from step 1.

- **The owner types the OLX password** in the simulator or emulator. The agent never types it, never reads it into chat, and never retries a failed login. One failed login: stop and report. Repeated attempts got the RO account banned.
- Scripts with `read -rp` (iPad `result`, every Android script) must run in the owner's own terminal, because a background task cannot answer the prompt. The iPhone scripts need no stdin: the flow waits up to 5 minutes for the home screen. Run those in the background and tell the owner when the form opens.
- iPad `generate_ad` does not log in. It reuses the session on the iPad, so run iPad `result` first or log in by hand.
- Before each country, tell the owner what he will see and what to do. Usually he types nothing, because the OLX web session survives `clearState`.

Done when the owner knows the script, the countries, and where he types the password.

### 5. Probe one country

Run the script with `COUNTRIES=<one country>`. The scripts continue to the next country after a failure. A broken flow then burns every login before you can react.
Done when every file of that country exists (step 7 lists them) and one opened screenshot shows that country's language.

### 6. Run the remaining countries

Run the script without `COUNTRIES`, or with the remaining ones. Read the final `Failed:` line. Re-run each failed country alone.
Done when the script exits 0, or every failed country has been re-run once and reported.

### 7. Verify the output

A green exit does not prove a good capture. Flows use `optional: true`, so a bad capture can pass.

```bash
cd store/captures/<device>
ls <cc> | grep -cE 'analysing|result'                     # 9 on iphone, 8 elsewhere
md5 -q <cc>/*.png | sort | uniq -d                        # no output: no two files identical
md5 -q */result_top_light.png | sort | uniq -d            # no output: no two countries identical
ls -l */result_top_*.png                                   # above 1 MB: photo loaded; about 300 KB: blank gradient
```

Open one screenshot per country and read the language. A whole set once shipped in Polish with every step green.
Done when the counts match, both `uniq -d` runs print nothing, and one screenshot per country is in the right language.

### 8. Restore and hand off

1. Set `screenshotMode` back to `false`. Done when `git diff --stat -- <ScreenshotMode.kt path>` prints nothing. Never commit `true`.
2. `auth` on iPad: run `scripts/normalize-ipad-screenshots.sh`. The other iPad scripts run it themselves.
3. `auth` feeds the store set as `auth.png`, without a theme. The flow writes `auth_light.png` and `auth_dark.png`. Copy the wanted one to `auth.png`.
4. Name the changed folders to the owner. Commit only when he asks.

Done when the file is `false`, the iPad files are landscape, and the owner has the folder list.
