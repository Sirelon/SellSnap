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

DEVICE="${DEVICE:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [[ -z "$DEVICE" ]]; then
  echo "ERROR: no adb device attached (and DEVICE is not set)." >&2
  exit 1
fi
PLATFORM="${PLATFORM:-android-phone}"
THEMES="${THEMES:-light dark}"
CLEAR_FLOW=".maestro/clear_state_only.yaml"
FLOW=".maestro/auth_screenshot_android.yaml"

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
    *) echo "✗ locale $tag not applied (got: ${actual:-none})" >&2; return 1 ;;
  esac
}

COUNTRIES=(
  "pt|pt-PT"
  "ro|ro-RO"
  "pl|pl-PL"
  "ua|uk-UA"
  "bg|bg-BG"
)

# Tablet: resize emulator to landscape tablet dimensions via wm override.
# Phone: ensure emulator is at default size. Emulator only — overriding a physical
# panel would mix aspect ratios into one store folder.
if [[ "$DEVICE" != emulator-* ]]; then
  echo "Physical device $DEVICE — keeping the native panel size and density."
elif [ "$PLATFORM" = "android-tablet" ]; then
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
    if ! set_app_locale "$locale"; then
      FAILED+=("${theme}/${country}")
      continue
    fi

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
if [[ "$DEVICE" == emulator-* && "$PLATFORM" == "android-tablet" ]]; then
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
