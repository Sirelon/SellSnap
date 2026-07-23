#!/usr/bin/env bash
# GenerateAd screenshots for all countries on Android.
# Captures the photos-attached screen at top and bottom scroll positions in both themes.
#
# Usage:
#   OLX_EMAIL=me@x.com OLX_PASSWORD=secret ./scripts/maestro-generate-ad-screenshots-android.sh
#   PLATFORM=android-tablet OLX_EMAIL=... OLX_PASSWORD=... ./scripts/maestro-generate-ad-screenshots-android.sh
#   THEMES=dark OLX_EMAIL=... OLX_PASSWORD=... ./scripts/maestro-generate-ad-screenshots-android.sh

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".maestro/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi

: "${OLX_EMAIL:?OLX_EMAIL is required}"
: "${OLX_PASSWORD:?OLX_PASSWORD is required}"

DEVICE="${DEVICE:-emulator-5554}"
PLATFORM="${PLATFORM:-android-phone}"
THEMES="${THEMES:-light dark}"
CLEAR_FLOW=".maestro/clear_state_only.yaml"
FLOW=".maestro/generate_ad_screenshots_android.yaml"

COUNTRIES=(
  "pt|pt-PT"
  "ro|ro-RO"
  "pl|pl-PL"
  "bg|bg-BG"
)

# Tablet: resize emulator to landscape tablet dimensions via wm override.
if [ "$PLATFORM" = "android-tablet" ]; then
  echo "Resizing emulator to tablet landscape (1920x1200, 240dpi)..."
  adb -s "$DEVICE" shell wm size 1920x1200
  adb -s "$DEVICE" shell wm density 240
  sleep 2
else
  adb -s "$DEVICE" shell wm size reset 2>/dev/null || true
  adb -s "$DEVICE" shell wm density reset 2>/dev/null || true
fi
# wm size handles landscape for tablet; no rotation needed in the flow
ORIENTATION=portrait

# Add test photos once — they survive pm clear (app data ≠ system gallery).
echo "Adding test photos to device gallery..."
./scripts/maestro-add-media.sh

FAILED=()

for theme in $THEMES; do
  if [ "$theme" = "dark" ]; then
    echo "Setting dark mode on device..."
    adb -s "$DEVICE" shell cmd uimode night yes
  else
    echo "Setting light mode on device..."
    adb -s "$DEVICE" shell cmd uimode night no
  fi
  sleep 1

  echo ""
  echo "Theme: $theme — ${#COUNTRIES[@]} countries ($PLATFORM, $ORIENTATION)"
  echo "Device: $DEVICE"
  echo ""

  for entry in "${COUNTRIES[@]}"; do
    country="${entry%%|*}"
    locale="${entry##*|}"

    mkdir -p "screenshots/$PLATFORM/$country"
    printf "  %s (%s) " "$country" "$locale"

    # Step 1: clear app state via Maestro (safe pm clear)
    maestro test --device "$DEVICE" "$CLEAR_FLOW" >/dev/null 2>&1

    # Step 2: set per-app locale now that data is cleared
    adb -s "$DEVICE" shell cmd locale set-app-locales com.sirelon.sellsnap --locales "$locale" >/dev/null 2>&1

    # Step 3: login + attach photos + screenshots (no clearState in this flow)
    if maestro test --device "$DEVICE" \
        -e COUNTRY="$country" \
        -e PLATFORM="$PLATFORM" \
        -e ORIENTATION="$ORIENTATION" \
        -e THEME="$theme" \
        -e OLX_EMAIL="$OLX_EMAIL" \
        -e OLX_PASSWORD="$OLX_PASSWORD" \
        "$FLOW" >/dev/null 2>&1; then
      echo "✓"
    else
      echo "✗"
      FAILED+=("${theme}/${country}")
    fi
  done
done

# Reset emulator and restore light mode
if [ "$PLATFORM" = "android-tablet" ]; then
  echo ""
  echo "Resetting emulator to default dimensions..."
  adb -s "$DEVICE" shell wm size reset
  adb -s "$DEVICE" shell wm density reset
fi

echo ""
echo "Resetting to light mode..."
adb -s "$DEVICE" shell cmd uimode night no

echo ""
echo "Screenshots saved to screenshots/$PLATFORM/<country>/generate_ad_{top,bottom}_<theme>.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
