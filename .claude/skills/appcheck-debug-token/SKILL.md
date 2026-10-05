---
name: appcheck-debug-token
description: >-
  Register a Firebase App Check debug token for a SellSnap debug build running on an Android
  emulator or iOS simulator, so the OpenAI proxy stops answering 401 and listing generation
  works. Reads the token from logcat or the simulator log, registers it through the App Check
  API, and verifies it. Use when the user says "register the debug token", "App Check 401",
  "generation fails on the emulator/simulator", when a Maestro screenshot run fails at
  generation, or after any app-data wipe (uninstall, `clearState`) on a dev device.
---

# App Check debug tokens

Release builds attest with Play Integrity / App Attest and never need this. A **debug** build
cannot, so on first launch its debug provider mints a random token, stores it in app data and
logs it once. The `openai` Cloud Function rejects App Check tokens minted from an unregistered
debug token, so every new install on an emulator or simulator needs one registration.

The token lives in app data: an uninstall, a `pm clear`, or a Maestro `clearState` makes a new
one. Eight `.maestro` flows clear state, so a screenshot run on a fresh device needs this skill
first, and again after each wipe.

Everything below goes through `scripts/appcheck_debug_token.py` (gcloud auth; run from
anywhere). Never type the exchange URL into zsh by hand: `$APP_ID:exchangeDebugToken` is parsed
as a modifier and silently 404s.

## Fixed tokens (the normal case on a dev machine)

Debug builds use a fixed token when one is configured, so wipes and `clearState` change
nothing. Each machine registers its two tokens once:

1. `uuidgen | tr 'A-Z' 'a-z'` twice, then `… register --app android --token <a> --name "Fixed
   token: Android debug builds (<machine>)"` and the same with `--app ios`.
2. Android: `APP_CHECK_DEBUG_TOKEN=<a>` in `local.properties` (read by `androidApp/build.gradle.kts`
   into `BuildConfig`, fed to the provider by `AppCheckDebugSecretRegistrar`).
3. iOS: `APP_CHECK_DEBUG_TOKEN_DEBUG = <b>` in `iosApp/Configuration/AppCheckDebugToken.local.xcconfig`
   (gitignored; `Config.xcconfig` includes it and `iOSApp.swift` passes it to the SDK).
4. Rebuild. `android-token` must print `<a>`; the Xcode console must print `<b>`.

To rotate, register a new UUID, replace it in the two files, delete the old one. Both files are
gitignored: the token is a key to the proxy. The per-install flow below is only for a device
built without these files.

## Android

1. Install and launch a debug build on the emulator:
   `./gradlew :androidApp:assembleDebug && adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk && adb -s emulator-5554 shell am start -n com.sirelon.sellsnap/.MainActivity`
2. `python3 .claude/skills/appcheck-debug-token/scripts/appcheck_debug_token.py android-token`
   prints the token (logged at debug level under the provider's full class name when
   `MainActivity` installs the factory; if it is missing, force-stop and relaunch the app).
3. `… register --app android --token <uuid> --name "<AVD name> emulator (<machine>)"`
   registers and then verifies with a free `exchangeDebugToken` call.

## iOS

1. Run a Debug build on the simulator (Xcode, or the `sellsnap-screenshots` kit).
2. `… ios-token --udid <udid>` greps the simulator log for "App Check Debug Token". If it finds
   nothing, read the line from the Xcode console; it is printed once per install.
3. `… register --app ios --token <uuid> --name "<simulator> (<machine>)"`.

## Housekeeping

- A fixed token only applies to builds made after it was added to the local file; an older
  install on the device keeps its random token until reinstalled.

- `… list` shows what is registered; `… delete --app <android|ios> --name "<label>"` removes
  stale ones. Each token is a key to the proxy, so delete tokens for devices you no longer use.
- Verification is free. A real generation call through the proxy costs a few gpt-4.1 tokens;
  do not loop on it.
- If registration succeeds but the app still gets 401, the app on the device predates the
  registration or its data was wiped since: run `android-token` / `ios-token` again and compare.
