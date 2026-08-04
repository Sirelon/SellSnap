#!/usr/bin/env bash
# Fix the orientation of the Maestro-captured iPad screenshots.
#
# THE BUG: the iPad app runs in landscape, but `xcrun simctl io … screenshot` on a
# landscape iPad simulator writes the RAW FRAMEBUFFER — a 2064x2752 portrait PNG whose
# contents are rotated 90° CCW. There is no EXIF orientation tag, so every consumer
# (App Store Connect, ImageMagick, the SVG compositor, a human double-clicking the file)
# sees a sideways image. All 75 files committed in 6e6b1d4 are affected.
#
# THE FIX: rotate 270° (== 90° counter-clockwise) so the status bar returns to the top
# and the image becomes its true 2752x2064 landscape.
#
# IDEMPOTENT: only touches files that are still portrait (width < height). Correctly
# oriented landscape files are skipped, so running this twice is safe.
#
# Usage: scripts/normalize-ipad-screenshots.sh [--dry-run]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IPAD_DIR="$ROOT/store/captures/ipad"
DRY_RUN=false
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=true

if [[ ! -d "$IPAD_DIR" ]]; then
  echo "No $IPAD_DIR — nothing to do." >&2
  exit 0
fi

rotated=0
skipped=0

while IFS= read -r -d '' file; do
  read -r w h <<<"$(magick identify -format "%w %h" "$file")"
  if (( w < h )); then
    if $DRY_RUN; then
      echo "would rotate  ${file#"$ROOT"/}  (${w}x${h} -> ${h}x${w})"
    else
      magick "$file" -rotate 270 -strip "$file"
      echo "rotated       ${file#"$ROOT"/}  (${w}x${h} -> ${h}x${w})"
    fi
    rotated=$((rotated + 1))
  else
    skipped=$((skipped + 1))
  fi
done < <(find "$IPAD_DIR" -name '*.png' -print0)

echo
echo "rotated: $rotated   already landscape: $skipped"
