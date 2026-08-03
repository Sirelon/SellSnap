#!/usr/bin/env bash
# Result/Analysing screenshots for all countries on an iOS simulator.
# chmod +x scripts/maestro-generate-ad-result-screenshots-ios.sh
#
# Login strategy: mirrors maestro-generate-ad-screenshots-ios.sh.
# Each country logs in fresh (clearState) via result_screenshots.yaml so
# OpenAI generates content in the correct language.
# iOS password fields are hidden from a11y — the script pre-fills the email,
# focuses the password field, and waits for you to type the password on your
# Mac keyboard (hardware keyboard → iOS first responder) and press Enter.
# Dark mode reuses the live session (no second login) via result_screenshots_dark_only.yaml.
#
# Per-country email credentials: set OLX_EMAIL_PT, OLX_EMAIL_PL, OLX_EMAIL_BG
# in .maestro/.env (or export them). Falls back to OLX_EMAIL if not set.
#
# Usage:
#   ./scripts/maestro-generate-ad-result-screenshots-ios.sh
#   UDID=<udid> PLATFORM=iphone ./scripts/...
#   COUNTRIES="bg" ./scripts/...  (partial run / retry)

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".maestro/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
else
  # Not fatal: per-country emails may also come from the environment.
  echo "NOTE: $ENV_FILE not found — create it with" >&2
  echo "        cp .maestro/.env.example .maestro/.env" >&2
fi

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
APP_ID="com.sirelon.sellsnap"
FLOW_FULL=".maestro/result_screenshots.yaml"
FLOW_DARK=".maestro/result_screenshots_dark_only.yaml"

# code|AppleLanguages|AppleLocale|lat,lon
# Coordinates are each country's capital, so the Location card resolves to a real
# city via the OLX reverse-geocode instead of "couldn't find your location".
ALL_COUNTRIES=(
  "pt|pt-PT|pt_PT|38.7223,-9.1393"   # Lisbon
  "pl|pl|pl_PL|52.2297,21.0122"      # Warsaw
  "bg|bg|bg_BG|42.6977,23.3219"      # Sofia
  "ro|ro|ro_RO|44.4268,26.1025"      # Bucharest
  "ua|uk|uk_UA|50.4501,30.5234"      # Kyiv
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
  rest="${entry#*|}"            # lang|locale|coords
  lang="${rest%%|*}"
  rest="${rest#*|}"             # locale|coords
  locale="${rest%%|*}"
  coords="${rest#*|}"

  # Resolve per-country email: OLX_EMAIL_PT / OLX_EMAIL_PL / OLX_EMAIL_BG → OLX_EMAIL
  # `${country^^}` is bash 4+; macOS ships bash 3.2, so uppercase via tr.
  country_upper="$(printf '%s' "$country" | tr '[:lower:]' '[:upper:]')"
  email_var="OLX_EMAIL_${country_upper}"
  olx_email="${!email_var:-${OLX_EMAIL:-}}"
  if [[ -z "$olx_email" ]]; then
    echo "ERROR: neither ${email_var} nor OLX_EMAIL is set. Skipping $country."
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  mkdir -p "screenshots/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country  ($locale)  location: $coords"
  echo "════════════════════════════════════════"

  xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")" 2>/dev/null
  xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale" 2>/dev/null

  # Simulated capital-city fix + pre-granted permission. PreviewAdScreen's
  # LocationPermissionsBlock auto-requests on composition and fetches on grant, so
  # both are needed or the card falls back to "couldn't find your location".
  # Granted via simctl rather than by tapping the system dialog to stay
  # locale-independent — matching a localized button is what broke the auth sheet.
  xcrun simctl location "$UDID" set "$coords"
  xcrun simctl privacy "$UDID" grant location "$APP_ID" 2>/dev/null

  xcrun simctl ui "$UDID" appearance light
  sleep 1

  echo ""
  echo "  ▶ MANUAL LOGIN: The login form is opening now."
  echo "    Email will be pre-filled with: $olx_email"
  echo "    Click the Simulator window, type the password, and press Enter."
  echo ""

  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e THEME="light" \
      -e OLX_EMAIL="$olx_email" \
      -e OLX_PASSWORD="${OLX_PASSWORD:-}" \
      "$FLOW_FULL"; then
    echo "  ✓ light done"

    # Dark mode: reuse the logged-in session — no second login.
    echo "  Switching to dark appearance..."
    xcrun simctl ui "$UDID" appearance dark
    sleep 1

    if maestro test --udid "$UDID" \
        -e COUNTRY="$country" \
        -e PLATFORM="$PLATFORM" \
        -e THEME="dark" \
        -e OLX_EMAIL="$olx_email" \
      -e OLX_PASSWORD="${OLX_PASSWORD:-}" \
        "$FLOW_DARK"; then
      echo "  ✓ dark done"
    else
      echo "  ✗ dark failed"
      FAILED+=("dark/$country")
    fi
  else
    echo "  ✗ light failed — see the Maestro debug dir printed above for the failing step"
    FAILED+=("light/$country" "dark/$country")
  fi
done

echo ""
echo "Resetting simulator to light appearance..."
xcrun simctl ui "$UDID" appearance light

echo ""
echo "Screenshots → screenshots/$PLATFORM/<country>/"
echo "  analysing_start_{light,dark}.png"
echo "  result_top_{light,dark}.png"
echo "  result_bottom_{light,dark}.png"
echo "  result_publish_dialog_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
