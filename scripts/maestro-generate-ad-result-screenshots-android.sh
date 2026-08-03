#!/usr/bin/env bash
# Result/Analysing screenshots for all countries on Android (phone or tablet).
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
#              Light run triggers a real OpenAI call (~1-2 min). Dark reuses
#              the same session for another OpenAI call.
#
# Usage:
#   ./scripts/maestro-generate-ad-result-screenshots-android.sh
#   PLATFORM=android-tablet ./scripts/maestro-generate-ad-result-screenshots-android.sh
#   COUNTRIES="ua" ./scripts/...   (partial run / retry)

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
FLOW_SCREENSHOTS=".maestro/result_screenshots_dark_only.yaml"

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

# Mock GPS is an emulator-console command, so on hardware the location card
# resolves to wherever the phone actually is instead of the country capital.
if [[ "$DEVICE" != emulator-* && "${ALLOW_REAL_GPS:-0}" != "1" ]]; then
  echo "ERROR: $DEVICE is not an emulator — 'adb emu geo fix' is unavailable, so the" >&2
  echo "       location card would show the phone's real position instead of each" >&2
  echo "       country's capital. Re-run with ALLOW_REAL_GPS=1 to accept that." >&2
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

# code|locale|lat,lon (capitals so location card resolves to a real city name)
ALL_COUNTRIES=(
  "ua|uk-UA|50.4501,30.5234"    # Kyiv
  "pt|pt-PT|38.7223,-9.1393"    # Lisbon
  "pl|pl-PL|52.2297,21.0122"    # Warsaw
  "ro|ro-RO|44.4268,26.1025"    # Bucharest
  "bg|bg-BG|42.6977,23.3219"    # Sofia
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
  rest="${entry#*|}"
  locale="${rest%%|*}"
  coords="${rest#*|}"

  mkdir -p "screenshots/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country  ($locale)  $PLATFORM"
  echo "════════════════════════════════════════"

  # Phase 1: clear state + navigate to OLX login page
  echo "  [Phase 1] Launching app and navigating to OLX login..."
  if ! maestro test --device "$DEVICE" \
      -e COUNTRY="$country" \
      "$FLOW_SETUP" 2>&1; then
    echo "  ✗ setup failed — skipping $country"
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  # Set locale after clearState
  if ! set_app_locale "$locale"; then
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  # Set mock GPS location (emu geo fix: longitude first, then latitude).
  # Physical devices got the ALLOW_REAL_GPS warning during preflight.
  lat="${coords%%,*}"
  lon="${coords##*,}"
  if [[ "$DEVICE" == emulator-* ]]; then
    adb -s "$DEVICE" emu geo fix "$lon" "$lat"
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

  # Phase 2 — light screenshots (~1-2 min for OpenAI)
  echo "  Setting light mode..."
  adb -s "$DEVICE" shell cmd uimode night no
  sleep 1

  echo "  [light] Screenshots (~1-2 min for OpenAI)..."
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

  # Dark screenshots — reuse same session, another OpenAI call
  echo "  Setting dark mode..."
  adb -s "$DEVICE" shell cmd uimode night yes
  sleep 1

  echo "  [dark] Screenshots (~1-2 min for OpenAI)..."
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
echo "Screenshots saved to screenshots/$PLATFORM/<country>/"
echo "  analysing_start_{light,dark}.png"
echo "  result_top_{light,dark}.png  result_bottom_{light,dark}.png"
echo "  result_publish_dialog_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
