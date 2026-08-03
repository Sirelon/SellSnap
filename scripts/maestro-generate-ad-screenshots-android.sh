#!/usr/bin/env bash
# GenerateAd screenshots for all countries on Android (phone or tablet).
#
# *** MUST BE RUN INTERACTIVELY IN A TERMINAL — NOT AS A BACKGROUND TASK ***
# OLX bot-detection blocks automated login. Each country pauses after opening
# the Chrome Custom Tab so you can type the OLX password manually.
#
# Login strategy (two phases per country):
#   Phase 1 — Maestro clears state, walks through onboarding/consent/country
#              picker, and taps Continue with OLX. Chrome Custom Tab opens.
#   Phase 2 — Script pauses. Type your OLX password in the emulator and press
#              Return. Then press Enter in this terminal to take screenshots.
#
# Usage:
#   ./scripts/maestro-generate-ad-screenshots-android.sh
#   PLATFORM=android-tablet ./scripts/maestro-generate-ad-screenshots-android.sh
#   COUNTRIES="ua pt" ./scripts/...   (partial run / retry)

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".maestro/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found. Create it from the template:" >&2
  echo "         cp .maestro/.env.example .maestro/.env   # then fill OLX_EMAIL / OLX_PASSWORD" >&2
  exit 1
fi
set -a; source "$ENV_FILE"; set +a

: "${OLX_EMAIL:?set OLX_EMAIL in .maestro/.env (see .maestro/.env.example)}"

SCREENSHOT_MODE_FILE="composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt"
if ! grep -q "screenshotMode = true" "$SCREENSHOT_MODE_FILE"; then
  echo "ERROR: screenshotMode is false in $SCREENSHOT_MODE_FILE" >&2
  echo "       Set it to true, rebuild + reinstall the app, then re-run." >&2
  exit 1
fi

DEVICE="${DEVICE:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [[ -z "$DEVICE" ]]; then
  echo "ERROR: no adb device attached (and DEVICE is not set)." >&2
  exit 1
fi
PLATFORM="${PLATFORM:-android-phone}"
ORIENTATION="portrait"
FLOW_SETUP=".maestro/setup_for_country_android.yaml"
FLOW_SCREENSHOTS=".maestro/generate_ad_screenshots_android_no_login.yaml"

# Per-app locales (cmd locale) only exist on API 33+. Below that the call fails
# with "Can't find service: locale" and every country would be captured in
# whatever language the device resolves — silently, since the flows are id-based.
API_LEVEL="$(adb -s "$DEVICE" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$API_LEVEL" -lt 33 ]]; then
  echo "ERROR: per-app locales need API 33+ ($DEVICE is API $API_LEVEL)." >&2
  echo "       Use an API 33+ emulator for multi-locale runs, or run a single country" >&2
  echo "       with the device language set by hand." >&2
  exit 1
fi

# Sets the app locale and verifies it stuck: cmd locale reports failure only
# through its exit code, and a wrong locale is invisible until the screenshots
# are reviewed.
set_app_locale() {
  local tag="$1" actual
  adb -s "$DEVICE" shell cmd locale set-app-locales com.sirelon.sellsnap --locales "$tag" >/dev/null 2>&1
  actual="$(adb -s "$DEVICE" shell cmd locale get-app-locales com.sirelon.sellsnap 2>/dev/null | tr -d '\r')"
  case "$actual" in
    *"$tag"*) return 0 ;;
    *) echo "  ✗ locale $tag not applied (got: ${actual:-none})" >&2; return 1 ;;
  esac
}

ALL_COUNTRIES=(
  "ua|uk-UA"
  "pt|pt-PT"
  "ro|ro-RO"
  "pl|pl-PL"
  "bg|bg-BG"
)

if [[ -n "${COUNTRIES:-}" ]]; then
  COUNTRY_ENTRIES=()
  for entry in "${ALL_COUNTRIES[@]}"; do
    code="${entry%%|*}"
    for c in $COUNTRIES; do
      [[ "$code" == "$c" ]] && COUNTRY_ENTRIES+=("$entry")
    done
  done
else
  COUNTRY_ENTRIES=("${ALL_COUNTRIES[@]}")
fi

# Phone: portrait. Tablet: landscape at lower density. Emulator only — overriding
# a physical panel would mix aspect ratios into one store folder (an A50 captures
# 1080x2340 natively, the emulator set is 1080x2400).
if [[ "$DEVICE" == emulator-* ]]; then
  if [ "$PLATFORM" = "android-tablet" ]; then
    echo "Resizing emulator to tablet landscape (1920x1200, 240dpi)..."
    adb -s "$DEVICE" shell wm size 1920x1200
    adb -s "$DEVICE" shell wm density 240
    sleep 2
  else
    echo "Resizing emulator to phone portrait (1080x1920, 420dpi)..."
    adb -s "$DEVICE" shell wm size 1080x1920
    adb -s "$DEVICE" shell wm density 420
    sleep 2
  fi
else
  echo "Physical device $DEVICE — keeping the native panel size and density."
fi

FAILED=()

for entry in "${COUNTRY_ENTRIES[@]}"; do
  country="${entry%%|*}"
  locale="${entry##*|}"

  mkdir -p "screenshots/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country  ($locale)  $PLATFORM"
  echo "════════════════════════════════════════"

  # Set app locale before Phase 1 (clearState in the flow wipes locale, so set it after)
  # Phase 1: clear state + navigate to OLX login page
  echo "  [Phase 1] Launching app and navigating to OLX login..."
  if ! maestro test --device "$DEVICE" \
      -e COUNTRY="$country" \
      "$FLOW_SETUP" 2>&1; then
    echo "  ✗ setup failed — skipping $country"
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  # Set locale after clearState (pm clear wipes app locale too)
  if ! set_app_locale "$locale"; then
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  osascript -e "display notification \"Type OLX password in emulator for $country\" with title \"ACTION: Android $country login\" sound name \"Glass\"" 2>/dev/null || true
  say "Action for $country: type your OLX password in the emulator and press Return." 2>/dev/null || true

  country_upper="$(printf '%s' "$country" | tr '[:lower:]' '[:upper:]')"
  echo ""
  echo "  ╔══════════════════════════════════════════════════════╗"
  echo "  ║  MANUAL LOGIN for ${country_upper}:                              ║"
  echo "  ║  Chrome Custom Tab is open — email: $OLX_EMAIL"
  echo "  ║  Type password in emulator, press Return.            ║"
  echo "  ╚══════════════════════════════════════════════════════╝"
  echo ""
  read -rp "  >>> Press Enter here AFTER you are on the SellSnap home screen: "

  # Phase 2 — light screenshots (session already active)
  echo "  Setting light mode..."
  adb -s "$DEVICE" shell cmd uimode night no
  sleep 1

  echo "  [light] Screenshots..."
  if maestro test --device "$DEVICE" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      -e THEME="light" \
      "$FLOW_SCREENSHOTS" 2>&1; then
    echo "  ✓ light done"
  else
    echo "  ✗ light failed"
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  # Dark screenshots — reuse the same session, no second login
  echo "  Setting dark mode..."
  adb -s "$DEVICE" shell cmd uimode night yes
  sleep 1

  echo "  [dark] Screenshots..."
  if maestro test --device "$DEVICE" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      -e THEME="dark" \
      "$FLOW_SCREENSHOTS" 2>&1; then
    echo "  ✓ dark done"
  else
    echo "  ✗ dark failed"
    FAILED+=("dark/$country")
  fi
done

echo ""
if [[ "$DEVICE" == emulator-* ]]; then
  echo "Resetting emulator to default dimensions and light mode..."
  adb -s "$DEVICE" shell wm size reset
  adb -s "$DEVICE" shell wm density reset
else
  echo "Resetting to light mode..."
fi
adb -s "$DEVICE" shell cmd uimode night no

echo ""
echo "Screenshots saved to screenshots/$PLATFORM/<country>/generate_ad_{top,bottom}_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
