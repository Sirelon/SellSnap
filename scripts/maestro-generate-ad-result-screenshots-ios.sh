#!/usr/bin/env bash
# Result/Analysing screenshots for all countries on an iOS simulator.
# chmod +x scripts/maestro-generate-ad-result-screenshots-ios.sh
#
# No login required — runs with the currently logged-in session.
# Requires: app built with screenshotMode = true (see ScreenshotMode.kt).
# Requires: test photos already in gallery (added once per simulator boot).
#
# Usage:
#   ./scripts/maestro-generate-ad-result-screenshots-ios.sh
#   UDID=<udid> PLATFORM=iphone ./scripts/maestro-generate-ad-result-screenshots-ios.sh
#   COUNTRIES="bg" ./scripts/maestro-generate-ad-result-screenshots-ios.sh  (partial run)

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".maestro/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi

UDID="${UDID:-C74C95D7-F29C-49D7-A281-E9D8DAAEDD59}"
PLATFORM="${PLATFORM:-iphone}"
FLOW=".maestro/result_screenshots.yaml"

# ro excluded — account suspended
ALL_COUNTRIES=(
  "pt|pt-PT|pt_PT"
  "pl|pl|pl_PL"
  "bg|bg|bg_BG"
)

# Allow overriding with COUNTRIES="bg pl" for partial runs
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

# Add test photos once — they persist across launchApp on iOS.
echo "Adding test photos to simulator gallery..."
DEVICE="$UDID" ./scripts/maestro-add-media.sh

FAILED=()

for entry in "${COUNTRY_ENTRIES[@]}"; do
  country="${entry%%|*}"
  # lang/locale not used — no locale switching (stays logged in with current locale)

  mkdir -p "screenshots/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country"
  echo "════════════════════════════════════════"

  # Light mode
  xcrun simctl ui "$UDID" appearance light
  sleep 1

  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e THEME="light" \
      "$FLOW"; then
    echo "  ✓ light done"
  else
    echo "  ✗ light failed"
    FAILED+=("light/$country")
  fi

  # Dark mode
  xcrun simctl ui "$UDID" appearance dark
  sleep 1

  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e THEME="dark" \
      "$FLOW"; then
    echo "  ✓ dark done"
  else
    echo "  ✗ dark failed"
    FAILED+=("dark/$country")
  fi
done

echo ""
echo "Resetting simulator to light appearance..."
xcrun simctl ui "$UDID" appearance light

echo ""
echo "Screenshots → screenshots/$PLATFORM/<country>/"
echo "  analysing_start_{light,dark}.png"
echo "  analysing_progress_{light,dark}.png"
echo "  result_top_{light,dark}.png"
echo "  result_bottom_{light,dark}.png"
echo "  result_publish_dialog_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
