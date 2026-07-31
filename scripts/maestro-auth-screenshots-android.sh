#!/usr/bin/env bash
# Auth-screen screenshots for SellSnap's supported countries on Android.
# Captures both light and dark theme variants.
#
# Usage:
#   ./scripts/maestro-auth-screenshots-android.sh                      # phone portrait
#   PLATFORM=android-tablet ./scripts/maestro-auth-screenshots-android.sh  # tablet landscape
#   THEMES=dark ./scripts/maestro-auth-screenshots-android.sh           # dark only

set -euo pipefail

cd "$(dirname "$0")/.."

DEVICE="${DEVICE:-emulator-5554}"
PLATFORM="${PLATFORM:-android-phone}"
THEMES="${THEMES:-light dark}"
CLEAR_FLOW=".maestro/clear_state_only.yaml"
FLOW=".maestro/auth_screenshot_android.yaml"

COUNTRIES=(
  "pt|pt-PT"
  "ro|ro-RO"
  "pl|pl-PL"
  "ua|uk-UA"
  "bg|bg-BG"
)

# Tablet: resize emulator to landscape tablet dimensions via wm override.
# Phone: ensure emulator is at default size.
if [ "$PLATFORM" = "android-tablet" ]; then
  echo "Resizing emulator to tablet landscape (1920x1200, 240dpi)..."
  adb -s "$DEVICE" shell wm size 1920x1200
  adb -s "$DEVICE" shell wm density 240
  sleep 2
else
  adb -s "$DEVICE" shell wm size reset 2>/dev/null || true
  adb -s "$DEVICE" shell wm density reset 2>/dev/null || true
fi
# wm size handles landscape for tablet; no device rotation needed
ORIENTATION=portrait

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

    # Step 1: clear state via Maestro (safe pm clear)
    maestro test --device "$DEVICE" "$CLEAR_FLOW" >/dev/null 2>&1

    # Step 2: set per-app locale now that data is cleared
    adb -s "$DEVICE" shell cmd locale set-app-locales com.sirelon.sellsnap --locales "$locale" >/dev/null 2>&1

    # Step 3: launch + onboarding + screenshot (no clearState in this flow)
    if maestro test --device "$DEVICE" \
        -e COUNTRY="$country" \
        -e PLATFORM="$PLATFORM" \
        -e ORIENTATION="$ORIENTATION" \
        -e THEME="$theme" \
        "$FLOW" >/dev/null 2>&1; then
      echo "✓"
    else
      echo "✗"
      FAILED+=("${theme}/${country}")
    fi
  done
done

# Reset emulator to phone dimensions and light mode after run
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
echo "Screenshots saved to screenshots/$PLATFORM/<country>/auth_<theme>.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
