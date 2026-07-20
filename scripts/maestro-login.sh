#!/usr/bin/env bash
# Runs the OLX login Maestro flow for a given country.
#
# Usage:
#   ./scripts/maestro-login.sh [country]      # country defaults to $COUNTRY or "ua"
#
# Credentials (OLX_EMAIL / OLX_PASSWORD) come from .maestro/.env (gitignored) or the
# environment and are passed to Maestro via -e. They are never written to the repo.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.maestro/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

# CLI arg wins over $COUNTRY; default "ua". Lowercased because Maestro id matching
# is case-sensitive and the country test-ids use lowercase codes (country_row_ua).
COUNTRY="${1:-${COUNTRY:-ua}}"
COUNTRY="$(printf '%s' "$COUNTRY" | tr '[:upper:]' '[:lower:]')"

if [[ -z "${OLX_EMAIL:-}" || -z "${OLX_PASSWORD:-}" ]]; then
  echo "ERROR: OLX_EMAIL and OLX_PASSWORD must be set (in .maestro/.env or the environment)." >&2
  exit 1
fi

echo "Running OLX login flow for COUNTRY=$COUNTRY ..."

# Optionally pin a device (needed when more than one is connected): DEVICE=emulator-5554
DEVICE_ARGS=()
if [[ -n "${DEVICE:-}" ]]; then
  DEVICE_ARGS=(--device "$DEVICE")
fi

exec maestro "${DEVICE_ARGS[@]}" test "$ROOT_DIR/.maestro/login.yaml" \
  -e COUNTRY="$COUNTRY" \
  -e OLX_EMAIL="$OLX_EMAIL" \
  -e OLX_PASSWORD="$OLX_PASSWORD"
