#!/bin/bash
set -e
cd "$(dirname "$0")/.."

VERSION_PROPERTIES="version.properties"
SCREENSHOT_MODE_FILE="composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/ScreenshotMode.kt"
# Play Console's actual configured store-listing locales - confirmed via
# download_from_play_store, not the full set of in-app-supported languages.
# It has no en-US or ru-RU listing at all (no title, nothing), so pushing a
# changelog for either fails the whole edit. Adding those as real Play Store
# listings is a separate, deliberate decision - not implied by this list.
ANDROID_LOCALES=(uk pl-PL ro bg pt-PT kk)
RELEASE_METADATA_DIR=".claude/tmp/release-metadata"
IOS_RELEASE_NOTES_VERSION_FILE="$RELEASE_METADATA_DIR/ios/RELEASE_NOTES_VERSION"

echo "Compiling..."
./gradlew :composeApp:compileAndroidMain -q

# Capture-time debug flag: seeds bundled photos and skips the publish confirmation.
# It must never ship enabled.
if ! grep -q "screenshotMode = false" "$SCREENSHOT_MODE_FILE"; then
  echo "ERROR: screenshotMode must be false before shipping ($SCREENSHOT_MODE_FILE)." >&2
  exit 1
fi

# version.properties is the single source of truth for VERSION_NAME/VERSION_CODE.
# Android reads it directly; iOS syncs it via the Fastlane sync_version lane.
CURRENT=$(grep '^VERSION_CODE=' "$VERSION_PROPERTIES" | cut -d= -f2)
NEW=$((CURRENT + 1))

# What's New must exist for every Play Store locale under the NEW build number,
# and the iOS release notes must be marked current for it - otherwise a language
# would silently ship with no changelog. Generate these with the sellsnap-release
# skill before running this script directly.
for locale in "${ANDROID_LOCALES[@]}"; do
  changelog="$RELEASE_METADATA_DIR/android/$locale/changelogs/$NEW.txt"
  if [ ! -s "$changelog" ]; then
    echo "ERROR: missing What's New for $locale at $changelog." >&2
    exit 1
  fi
done

if [ "$(cat "$IOS_RELEASE_NOTES_VERSION_FILE" 2>/dev/null)" != "$NEW" ]; then
  echo "ERROR: iOS release notes are not marked current for build $NEW ($IOS_RELEASE_NOTES_VERSION_FILE)." >&2
  exit 1
fi

sed -i '' "s/^VERSION_CODE=.*/VERSION_CODE=$NEW/" "$VERSION_PROPERTIES"

# Optionally bump marketing version: ./scripts/ship.sh 1.4
if [ -n "$1" ]; then
  sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$1/" "$VERSION_PROPERTIES"
  echo "Version: $1, build: $CURRENT → $NEW"
else
  echo "Build: $CURRENT → $NEW"
fi

bundle exec fastlane android beta
bundle exec fastlane ios beta

# Apple carries neither "What's New" nor promotional text into a new App Store version, so
# a version created by hand in App Store Connect always comes up with both fields empty and
# no build selected. Preparing the draft here keeps that from being manual work every
# release: it creates/updates the version, resupplies both fields for all 5 locales from
# .claude/tmp/release-metadata/ios/, and attaches the build just uploaded. It does NOT
# submit for review - that stays a deliberate click in App Store Connect.
#
# Non-fatal on purpose: both binaries are already uploaded by this point, and the one
# expected failure here is a version currently in review, which cannot be edited. Nothing
# about that should make the whole ship look failed.
if ! bundle exec fastlane ios release; then
  echo
  echo "WARNING: TestFlight and Play uploads succeeded, but preparing the App Store draft failed." >&2
  echo "Check whether that version is already in review; if it is, there is nothing to do here." >&2
fi
