#!/usr/bin/env bash
# GenerateAd screenshots for all countries on an iOS simulator.
#
# Login strategy: iOS password fields are hidden from the accessibility tree,
# so password entry is manual. For each country the script opens the OLX login
# form, pre-fills the email, and focuses the password field. You then type the
# password on your Mac keyboard (hardware keyboard → iOS first responder) and
# press Enter. The flow waits up to 5 minutes for you to do this, then
# continues automatically.
#
# Per-country flow: login once (light screenshots) → switch appearance to dark
# → reuse the live session for dark screenshots (no second login needed).
#
# Usage:
#   ./scripts/maestro-generate-ad-screenshots-ios.sh
#   UDID=<ipad-udid> PLATFORM=ipad ORIENTATION=landscape ./scripts/...
#   COUNTRIES="bg" ./scripts/...  (single country, for retries)

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".maestro/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi

: "${OLX_EMAIL:?OLX_EMAIL is required}"
: "${OLX_PASSWORD:?OLX_PASSWORD is required}"

# Every photo flow depends on screenshotMode = true: GenerateAd then seeds the bundled
# test photos itself instead of driving the OS picker. Checks source, so it cannot catch
# "flipped but not rebuilt" — reinstall the app after changing it.
SCREENSHOT_MODE_FILE="composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt"
if ! grep -q "screenshotMode = true" "$SCREENSHOT_MODE_FILE"; then
  echo "ERROR: screenshotMode is false in $SCREENSHOT_MODE_FILE" >&2
  echo "       Set it to true, rebuild + reinstall the app, then re-run." >&2
  exit 1
fi

UDID="${UDID:-C74C95D7-F29C-49D7-A281-E9D8DAAEDD59}"
PLATFORM="${PLATFORM:-iphone}"
ORIENTATION="${ORIENTATION:-portrait}"
FLOW_FULL=".maestro/generate_ad_screenshots.yaml"
FLOW_DARK=".maestro/generate_ad_screenshots_dark_only.yaml"

ALL_COUNTRIES=(
  "pt|pt-PT|pt_PT"
  "pl|pl|pl_PL"
  "bg|bg|bg_BG"
  "ro|ro|ro_RO"
  "ua|uk|uk_UA"
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

FAILED=()

for entry in "${COUNTRY_ENTRIES[@]}"; do
  country="${entry%%|*}"
  rest="${entry#*|}"
  lang="${rest%%|*}"
  locale="${rest##*|}"

  mkdir -p "screenshots/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country  ($locale)"
  echo "════════════════════════════════════════"

  xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")" 2>/dev/null
  xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale" 2>/dev/null
  xcrun simctl ui "$UDID" appearance light
  sleep 1

  echo ""
  echo "  ▶ MANUAL LOGIN: The login form is opening now."
  echo "    Wait for the email field to be pre-filled with: $OLX_EMAIL"
  echo "    Then click the Simulator window, type the password, and press Enter."
  echo "    (Your Mac keyboard types directly into the focused iOS field.)"
  echo ""

  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      -e THEME="light" \
      -e OLX_EMAIL="$OLX_EMAIL" \
      -e OLX_PASSWORD="$OLX_PASSWORD" \
      "$FLOW_FULL"; then
    echo "  ✓ light done"

    # Dark mode: reuse the logged-in session — no second login.
    echo "  Switching to dark appearance..."
    xcrun simctl ui "$UDID" appearance dark
    sleep 1

    if maestro test --udid "$UDID" \
        -e COUNTRY="$country" \
        -e PLATFORM="$PLATFORM" \
        -e ORIENTATION="$ORIENTATION" \
        -e THEME="dark" \
        -e OLX_EMAIL="$OLX_EMAIL" \
        -e OLX_PASSWORD="$OLX_PASSWORD" \
        "$FLOW_DARK"; then
      echo "  ✓ dark done"
    else
      echo "  ✗ dark failed"
      FAILED+=("dark/$country")
    fi
  else
    echo "  ✗ light failed (login may have timed out)"
    FAILED+=("light/$country" "dark/$country")
  fi
done

echo ""
echo "Resetting simulator to light appearance..."
xcrun simctl ui "$UDID" appearance light

echo ""
echo "Screenshots → screenshots/$PLATFORM/<country>/generate_ad_{top,bottom}_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
