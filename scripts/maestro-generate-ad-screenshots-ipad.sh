#!/usr/bin/env bash
# GenerateAd screenshots for iPad — no login (reuses existing session).
# Captures light + dark for each country in landscape orientation.
#
# Precondition: app is installed with screenshotMode=true and user is already
# logged in on the simulator (the no-login flow reuses the live session).
#
# Usage:
#   ./scripts/maestro-generate-ad-screenshots-ipad.sh
#   UDID=<other-ipad-udid> ./scripts/maestro-generate-ad-screenshots-ipad.sh
#   COUNTRIES="pl pt" ./scripts/...   (partial run / retry)

set -euo pipefail

cd "$(dirname "$0")/.."

SCREENSHOT_MODE_FILE="composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt"
if ! grep -q "screenshotMode = true" "$SCREENSHOT_MODE_FILE"; then
  echo "ERROR: screenshotMode is false in $SCREENSHOT_MODE_FILE" >&2
  echo "       Set it to true, rebuild + reinstall the app, then re-run." >&2
  exit 1
fi

UDID="${UDID:-1663ACA3-D833-471F-A56F-7095872F61A6}"  # iPad Pro 13-inch (M5)
PLATFORM="ipad"
ORIENTATION="landscape"
FLOW_LIGHT=".maestro/generate_ad_screenshots_no_login.yaml"
FLOW_DARK=".maestro/generate_ad_screenshots_dark_only.yaml"

# Ordered: UA first, then PL, PT, RO, BG
ALL_COUNTRIES=(
  "ua|uk|uk_UA"
  "pl|pl|pl_PL"
  "pt|pt-PT|pt_PT"
  "ro|ro|ro_RO"
  "bg|bg|bg_BG"
)

# Allow overriding with COUNTRIES="bg pl" for partial runs
COUNTRY_ENTRIES=()
if [[ -n "${COUNTRIES:-}" ]]; then
  for entry in "${ALL_COUNTRIES[@]}"; do
    code="${entry%%|*}"
    for c in $COUNTRIES; do
      [[ "$code" == "$c" ]] && COUNTRY_ENTRIES+=("$entry")
    done
  done
else
  COUNTRY_ENTRIES=("${ALL_COUNTRIES[@]}")
fi


FAILED=()

for entry in "${COUNTRY_ENTRIES[@]+"${COUNTRY_ENTRIES[@]}"}"; do
  country="${entry%%|*}"
  rest="${entry#*|}"
  lang="${rest%%|*}"
  locale="${rest##*|}"

  mkdir -p "screenshots/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country  ($locale)  [iPad, landscape]"
  echo "════════════════════════════════════════"

  xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")" 2>/dev/null
  xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale" 2>/dev/null
  xcrun simctl ui "$UDID" appearance light
  sleep 1

  echo "  ▶ light..."
  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      -e THEME="light" \
      "$FLOW_LIGHT"; then
    echo "  ✓ light done"
  else
    echo "  ✗ light failed"
    FAILED+=("light/$country")
    continue
  fi

  echo "  Switching to dark appearance..."
  xcrun simctl ui "$UDID" appearance dark
  sleep 1

  echo "  ▶ dark..."
  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      -e THEME="dark" \
      "$FLOW_DARK"; then
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
echo "Normalising orientation..."
# simctl writes the RAW FRAMEBUFFER of a landscape iPad: a 2064x2752 portrait PNG whose UI
# is rotated 90° CCW, with no EXIF orientation tag. Every consumer sees it sideways. Un-rotate
# here so screenshots/ipad/** is always true landscape. Idempotent.
./scripts/normalize-ipad-screenshots.sh

echo ""
echo "Screenshots → screenshots/$PLATFORM/<country>/generate_ad_{top,bottom}_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
