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
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi

: "${OLX_EMAIL:?OLX_EMAIL is required}"

SCREENSHOT_MODE_FILE="composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt"
if ! grep -q "screenshotMode = true" "$SCREENSHOT_MODE_FILE"; then
  echo "ERROR: screenshotMode is false in $SCREENSHOT_MODE_FILE" >&2
  echo "       Set it to true, rebuild + reinstall the app, then re-run." >&2
  exit 1
fi

DEVICE="${DEVICE:-emulator-5554}"
PLATFORM="${PLATFORM:-android-phone}"
ORIENTATION="portrait"
FLOW_SETUP=".maestro/setup_for_country_android.yaml"
FLOW_SCREENSHOTS=".maestro/result_screenshots_dark_only.yaml"

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

# Phone: portrait. Tablet: landscape at lower density.
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
  adb -s "$DEVICE" shell cmd locale set-app-locales com.sirelon.sellsnap --locales "$locale" >/dev/null 2>&1 || true

  # Set mock GPS location (emu geo fix: longitude first, then latitude)
  lat="${coords%%,*}"
  lon="${coords##*,}"
  adb -s "$DEVICE" emu geo fix "$lon" "$lat" 2>/dev/null || true

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
echo "Resetting emulator to default dimensions and light mode..."
adb -s "$DEVICE" shell wm size reset
adb -s "$DEVICE" shell wm density reset
adb -s "$DEVICE" shell cmd uimode night no

echo ""
echo "Screenshots saved to screenshots/$PLATFORM/<country>/"
echo "  analysing_start_{light,dark}.png  analysing_progress_{light,dark}.png"
echo "  result_top_{light,dark}.png  result_bottom_{light,dark}.png"
echo "  result_publish_dialog_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
