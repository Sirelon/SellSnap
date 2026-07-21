#!/usr/bin/env bash
# Auth-screen screenshots for SellSnap's supported countries on an iOS simulator.
# Locale is set via xcrun simctl before each run.
#
# Usage:
#   ./scripts/maestro-auth-screenshots-ios.sh                        # phone portrait
#   UDID=<ipad-udid> PLATFORM=ipad ORIENTATION=landscape ./scripts/...

set -euo pipefail

cd "$(dirname "$0")/.."

UDID="${UDID:-C74C95D7-F29C-49D7-A281-E9D8DAAEDD59}"
PLATFORM="${PLATFORM:-iphone}"
ORIENTATION="${ORIENTATION:-portrait}"
FLOW=".maestro/auth_screenshot.yaml"

# Supported countries: "country_code|AppleLanguages|AppleLocale"
COUNTRIES=(
  "pt|pt-PT|pt_PT"
  "ro|ro|ro_RO"
  "pl|pl|pl_PL"
  "ua|uk|uk_UA"
  "bg|bg|bg_BG"
)

echo "Auth screen screenshots — ${#COUNTRIES[@]} countries ($PLATFORM, $ORIENTATION)"
echo "Simulator: $UDID"
echo ""

FAILED=()

for entry in "${COUNTRIES[@]}"; do
  country="${entry%%|*}"
  rest="${entry#*|}"
  lang="${rest%%|*}"
  locale="${rest##*|}"

  mkdir -p "screenshots/$PLATFORM/$country"
  printf "  %s (%s) " "$country" "$locale"

  xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")" 2>/dev/null
  xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale" 2>/dev/null

  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      "$FLOW" >/dev/null 2>&1; then
    echo "✓"
  else
    echo "✗"
    FAILED+=("$country")
  fi
done

echo ""
echo "Screenshots saved to screenshots/$PLATFORM/<country>/auth.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
