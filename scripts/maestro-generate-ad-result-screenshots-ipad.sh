#!/usr/bin/env bash
# Result/Analysing screenshots for iPad — landscape orientation.
#
# *** MUST BE RUN INTERACTIVELY IN A TERMINAL — NOT AS A BACKGROUND TASK ***
# The script pauses after triggering the iOS auth sheet and waits for you
# to manually tap Continue + enter the OLX password in the Simulator.
#
# Login strategy (two phases):
#   Phase 1 — Maestro clears state, walks through onboarding/consent/country
#              picker, and taps the "Continue with OLX" button. The iOS system
#              auth sheet then appears (Maestro cannot tap SpringBoard dialogs).
#   Phase 2 — Script pauses and speaks instructions. You tap the auth dialog,
#              type the OLX password in the Simulator, press Return, then press
#              Enter in this terminal to resume. Maestro then takes screenshots
#              from the already-logged-in home screen.
#
# Per-country email credentials: set OLX_EMAIL_PT, OLX_EMAIL_PL, OLX_EMAIL_BG,
# OLX_EMAIL_RO, OLX_EMAIL_UA in .maestro/.env (or export them). Falls back to OLX_EMAIL.
#
# Usage:
#   ./scripts/maestro-generate-ad-result-screenshots-ipad.sh
#   COUNTRIES="ua" ./scripts/...   (single country / retry)

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

SCREENSHOT_MODE_FILE="composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt"
if ! grep -q "screenshotMode = true" "$SCREENSHOT_MODE_FILE"; then
  echo "ERROR: screenshotMode is false in $SCREENSHOT_MODE_FILE" >&2
  echo "       Set it to true, rebuild + reinstall the app, then re-run." >&2
  exit 1
fi

UDID="${UDID:-1663ACA3-D833-471F-A56F-7095872F61A6}"  # iPad Pro 13-inch (M5)
PLATFORM="ipad"
ORIENTATION="landscape"
APP_ID="com.sirelon.sellsnap"
FLOW_SETUP=".maestro/setup_for_country.yaml"
FLOW_SCREENSHOTS=".maestro/result_screenshots_dark_only.yaml"

# Ordered: UA first, then PL, PT, RO, BG
# code|AppleLanguages|AppleLocale|lat,lon
ALL_COUNTRIES=(
  "ua|uk|uk_UA|50.4501,30.5234"    # Kyiv
  "pl|pl|pl_PL|52.2297,21.0122"    # Warsaw
  "pt|pt-PT|pt_PT|38.7223,-9.1393" # Lisbon
  "ro|ro|ro_RO|44.4268,26.1025"    # Bucharest
  "bg|bg|bg_BG|42.6977,23.3219"    # Sofia
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

FAILED=()

for entry in "${COUNTRY_ENTRIES[@]}"; do
  country="${entry%%|*}"
  rest="${entry#*|}"
  lang="${rest%%|*}"
  rest="${rest#*|}"
  locale="${rest%%|*}"
  coords="${rest#*|}"

  country_upper="$(printf '%s' "$country" | tr '[:lower:]' '[:upper:]')"
  email_var="OLX_EMAIL_${country_upper}"
  olx_email="${!email_var:-${OLX_EMAIL:-}}"
  if [[ -z "$olx_email" ]]; then
    echo "ERROR: neither ${email_var} nor OLX_EMAIL is set. Skipping $country."
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  # Localized Continue button text for the iOS system auth sheet
  case "$country" in
    ua) cont_label="Продовжити" ;;
    pl) cont_label="Dalej" ;;
    pt) cont_label="Continuar" ;;
    ro) cont_label="Continuați" ;;
    bg) cont_label="Продължи" ;;
    *)  cont_label="Continue" ;;
  esac

  mkdir -p "store/captures/$PLATFORM/$country"

  echo ""
  echo "════════════════════════════════════════"
  echo "  Country: $country  ($locale)  [iPad, landscape]"
  echo "════════════════════════════════════════"

  xcrun simctl spawn "$UDID" defaults write -g AppleLanguages "(\"$lang\")" 2>/dev/null
  xcrun simctl spawn "$UDID" defaults write -g AppleLocale "$locale" 2>/dev/null
  xcrun simctl location "$UDID" set "$coords"
  xcrun simctl privacy "$UDID" grant location "$APP_ID" 2>/dev/null
  xcrun simctl ui "$UDID" appearance light
  sleep 1

  # Phase 1: clear state + navigate to auth sheet
  echo ""
  echo "  [Phase 1] Launching app and navigating to OLX auth..."
  if ! maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      "$FLOW_SETUP"; then
    echo "  ✗ setup failed — skipping $country"
    FAILED+=("light/$country" "dark/$country")
    continue
  fi

  # Bring Simulator to front
  open -a Simulator 2>/dev/null || true
  osascript -e "display notification \"Tap '$cont_label' in the grey dialog, then type the OLX password + Return\" with title \"ACTION: $country_upper login\" sound name \"Glass\"" 2>/dev/null || true
  say "Action for $country_upper: tap $cont_label in the grey dialog, then type the OLX password and press Return." 2>/dev/null || true

  echo ""
  echo "  ╔══════════════════════════════════════════════════════╗"
  echo "  ║  MANUAL LOGIN for $country_upper:                                ║"
  echo "  ║  1. Grey auth dialog is on screen — tap '$cont_label'      ║"
  echo "  ║  2. OLX login form opens — email: $olx_email"
  echo "  ║     Click Simulator, type password, press Return.   ║"
  echo "  ╚══════════════════════════════════════════════════════╝"
  echo ""
  read -rp "  >>> Press Enter here AFTER you are on the SellSnap home screen: "

  # Phase 2: take screenshots from the logged-in home screen
  echo ""
  echo "  [Phase 2] Light screenshots..."
  if maestro test --udid "$UDID" \
      -e COUNTRY="$country" \
      -e PLATFORM="$PLATFORM" \
      -e ORIENTATION="$ORIENTATION" \
      -e THEME="light" \
      "$FLOW_SCREENSHOTS"; then
    echo "  ✓ light done"

    echo "  Switching to dark appearance..."
    xcrun simctl ui "$UDID" appearance dark
    sleep 1

    echo "  [Phase 2] Dark screenshots..."
    if maestro test --udid "$UDID" \
        -e COUNTRY="$country" \
        -e PLATFORM="$PLATFORM" \
        -e ORIENTATION="$ORIENTATION" \
        -e THEME="dark" \
        "$FLOW_SCREENSHOTS"; then
      echo "  ✓ dark done"
    else
      echo "  ✗ dark failed"
      FAILED+=("dark/$country")
    fi
  else
    echo "  ✗ light failed"
    FAILED+=("light/$country" "dark/$country")
  fi
done

echo ""
echo "Resetting simulator to light appearance..."
xcrun simctl ui "$UDID" appearance light

echo ""
echo "Normalising orientation..."
# simctl writes the RAW FRAMEBUFFER of a landscape iPad: a 2064x2752 portrait PNG whose UI
# is rotated 90° CCW, with no EXIF orientation tag. Every consumer sees it sideways. Un-rotate
# here so store/captures/ipad/** is always true landscape. Idempotent.
./scripts/normalize-ipad-screenshots.sh

echo ""
echo "Screenshots → store/captures/$PLATFORM/<country>/"
echo "  analysing_start_{light,dark}.png"
echo "  result_top_{light,dark}.png"
echo "  result_bottom_{light,dark}.png"
echo "  result_publish_dialog_{light,dark}.png"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
