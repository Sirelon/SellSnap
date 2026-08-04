#!/usr/bin/env bash
# Takes a screenshot of the Auth (SellerLanding) screen for every Maestro-supported
# Android locale. No login required — flow ends after the consent dismiss.
#
# Usage:
#   ./scripts/maestro-auth-screenshots.sh
#   DEVICE=emulator-5554 ./scripts/maestro-auth-screenshots.sh
#
# Output: store/captures/auth/<locale>.png (relative to project root)

set -euo pipefail

# All Maestro-supported Android locales (language-COUNTRY)
LOCALES=(
  en-AU de-AT nl-BE pt-BR en-GB bg-BG en-CA hr-HR
  cs-CZ da-DK ar-EG fi-FI fr-FR de-DE el-GR zh-HK
  hu-HU hi-IN id-ID en-IE he-IL it-IT ja-JP ko-KR
  lv-LV de-LI lt-LT nl-NL en-NZ nb-NO fil-PH pl-PL
  pt-PT zh-CN ro-RO ru-RU sr-RS en-SG sk-SK sl-SI
  es-ES sv-SE de-CH zh-TW th-TH tr-TR uk-UA en-US
  vi-VN en-ZA
)

FLOW=".maestro/auth_screenshot.yaml"
OUTPUT_DIR="store/captures/auth"

mkdir -p "$OUTPUT_DIR"

DEVICE_ARG=""
if [ -n "${DEVICE:-}" ]; then
  DEVICE_ARG="--device $DEVICE"
fi

echo "Auth screen screenshots — ${#LOCALES[@]} locales"
echo "Output: $OUTPUT_DIR/"
echo ""

FAILED=()

for locale in "${LOCALES[@]}"; do
  printf "  %-10s " "$locale"
  if maestro test $DEVICE_ARG --locale "$locale" -e LOCALE="$locale" "$FLOW" 2>&1; then
    echo "✓"
  else
    echo "✗"
    FAILED+=("$locale")
  fi
done

echo ""
echo "Done. ${#LOCALES[@]} locales attempted, ${#FAILED[@]} failed."

if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Failed: ${FAILED[*]}"
  exit 1
fi
