#!/usr/bin/env bash
# Auth-screen screenshots for SellSnap's supported countries on an iOS simulator.
# Captures both light and dark theme variants.
#
# Usage:
#   ./scripts/maestro-auth-screenshots-ios.sh                          # phone portrait
#   UDID=<ipad-udid> PLATFORM=ipad ORIENTATION=landscape ./scripts/... # iPad landscape
#   THEMES=dark ./scripts/maestro-auth-screenshots-ios.sh              # dark only

set -euo pipefail

cd "$(dirname "$0")/.."

UDID="${UDID:-C74C95D7-F29C-49D7-A281-E9D8DAAEDD59}"
PLATFORM="${PLATFORM:-iphone}"
ORIENTATION="${ORIENTATION:-portrait}"
THEMES="${THEMES:-light dark}"
FLOW=".maestro/auth_screenshot.yaml"

# Supported countries: "country_code|AppleLanguages|AppleLocale"
COUNTRIES=(
  "pt|pt-PT|pt_PT"
  "ro|ro|ro_RO"
  "pl|pl|pl_PL"
  "ua|uk|uk_UA"
  "bg|bg|bg_BG"
)

FAILED=()

for theme in $THEMES; do
  echo "Setting $theme appearance on simulator..."
  xcrun simctl ui "$UDID" appearance "$theme"
  sleep 1

  echo ""
  echo "Theme: $theme — ${#COUNTRIES[@]} countries ($PLATFORM, $ORIENTATION)"
  echo "Simulator: $UDID"
  echo ""

  for entry in "${COUNTRIES[@]}"; do
    country="${entry%%|*}"
    rest="${entry#*|}"
    lang="${rest%%|*}"
    locale="${rest##*|}"

    mkdir -p "store/captures/$PLATFORM/$country"
    printf "  %s (%s) " "$country" "$locale"

    xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")" 2>/dev/null
    xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale" 2>/dev/null

    if maestro test --udid "$UDID" \
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

# Reset simulator to light appearance
echo ""
echo "Resetting simulator to light appearance..."
xcrun simctl ui "$UDID" appearance light

echo ""
echo "Screenshots saved to store/captures/$PLATFORM/<country>/auth_<theme>.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
