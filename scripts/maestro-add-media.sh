#!/usr/bin/env bash
# Pushes images/videos into the device's photo gallery.
#
# For iOS simulators: uses `xcrun simctl addmedia` directly — faster and does not
# require the Maestro XCTest driver (which can time out on iOS 26+).
# For Android / unknown: falls back to Maestro's addMedia flow.
#
# Usage:
#   ./scripts/maestro-add-media.sh                        # all files in .maestro/assets/
#   ./scripts/maestro-add-media.sh path/to/a.jpg b.png    # explicit files
#
# Supported formats: png, jpg, jpeg, gif, mp4
# DEVICE env var pins a specific iOS simulator UDID or Android serial (optional).

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="$ROOT_DIR/.maestro/assets"

# Collect files: CLI args take priority, otherwise scan the assets dir.
if [[ $# -gt 0 ]]; then
  FILES=("$@")
else
  FILES=()
  while IFS= read -r f; do
    FILES+=("$f")
  done < <(find "$ASSETS_DIR" -maxdepth 1 -type f \
    \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" \
       -o -iname "*.gif" -o -iname "*.mp4" \) | sort)
fi

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "No media files found." >&2
  echo "Place images in .maestro/assets/  or pass file paths as arguments." >&2
  exit 1
fi

echo "Adding ${#FILES[@]} file(s) to device gallery:"
for f in "${FILES[@]}"; do
  echo "  $(basename "$f")"
done

# Resolve absolute paths.
ABS_FILES=()
for f in "${FILES[@]}"; do
  ABS_FILES+=("$(cd "$(dirname "$f")" && pwd)/$(basename "$f")")
done

# iOS simulator: use simctl directly — no Maestro XCTest driver needed.
# Detect by checking if DEVICE looks like a simulator UDID (8-4-4-4-12 hex).
if [[ "${DEVICE:-}" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]]; then
  exec xcrun simctl addmedia "$DEVICE" "${ABS_FILES[@]}"
fi

# Android / default device: fall back to Maestro addMedia flow.
MEDIA_LIST=""
for abs in "${ABS_FILES[@]}"; do
  MEDIA_LIST+="    - \"${abs}\""$'\n'
done

TMPFLOW="$(mktemp /tmp/maestro-add-media-XXXXXX.yaml)"
trap 'rm -f "$TMPFLOW"' EXIT

cat > "$TMPFLOW" <<YAML
appId: com.sirelon.sellsnap
name: Add Media to Device
---
- addMedia:
${MEDIA_LIST}YAML

if [[ -n "${DEVICE:-}" ]]; then
  exec maestro --device "$DEVICE" test "$TMPFLOW"
else
  exec maestro test "$TMPFLOW"
fi
