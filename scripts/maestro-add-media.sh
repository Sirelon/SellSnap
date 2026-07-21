#!/usr/bin/env bash
# Pushes images/videos into the device's photo gallery via Maestro addMedia.
#
# Usage:
#   ./scripts/maestro-add-media.sh                        # all files in .maestro/assets/
#   ./scripts/maestro-add-media.sh path/to/a.jpg b.png    # explicit files
#
# Supported formats: png, jpg, jpeg, gif, mp4
# DEVICE env var pins a specific device/emulator (optional).

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="$ROOT_DIR/.maestro/assets"

# Collect files: CLI args take priority, otherwise scan the assets dir.
if [[ $# -gt 0 ]]; then
  FILES=("$@")
else
  mapfile -t FILES < <(
    find "$ASSETS_DIR" -maxdepth 1 -type f \
      \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" \
         -o -iname "*.gif" -o -iname "*.mp4" \) \
      | sort
  )
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

# Build the addMedia entries (absolute paths work regardless of tmp file location).
MEDIA_LIST=""
for f in "${FILES[@]}"; do
  # Resolve to absolute path.
  abs="$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"
  MEDIA_LIST+="    - \"${abs}\""$'\n'
done

# Write a temporary flow YAML.
TMPFLOW="$(mktemp /tmp/maestro-add-media-XXXXXX.yaml)"
trap 'rm -f "$TMPFLOW"' EXIT

cat > "$TMPFLOW" <<YAML
appId: com.sirelon.sellsnap
name: Add Media to Device
---
- addMedia:
${MEDIA_LIST}YAML

DEVICE_ARGS=()
if [[ -n "${DEVICE:-}" ]]; then
  DEVICE_ARGS=(--device "$DEVICE")
fi

exec maestro "${DEVICE_ARGS[@]}" test "$TMPFLOW"
