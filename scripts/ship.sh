#!/bin/bash
set -e
cd "$(dirname "$0")/.."

ANDROID_BUILD="androidApp/build.gradle.kts"
IOS_CONFIG="iosApp/Configuration/Config.xcconfig"

# Read current build number and increment
CURRENT=$(grep -o 'versionCode = [0-9]*' "$ANDROID_BUILD" | grep -o '[0-9]*')
NEW=$((CURRENT + 1))

sed -i '' "s/versionCode = $CURRENT/versionCode = $NEW/" "$ANDROID_BUILD"
sed -i '' "s/CURRENT_PROJECT_VERSION=$CURRENT/CURRENT_PROJECT_VERSION=$NEW/" "$IOS_CONFIG"

# Optionally bump marketing version: ./scripts/ship.sh 1.4
if [ -n "$1" ]; then
  sed -i '' "s/versionName = \"[^\"]*\"/versionName = \"$1\"/" "$ANDROID_BUILD"
  sed -i '' "s/MARKETING_VERSION=.*/MARKETING_VERSION=$1/" "$IOS_CONFIG"
  echo "Version: $1, build: $CURRENT → $NEW"
else
  echo "Build: $CURRENT → $NEW"
fi

bundle exec fastlane android beta
bundle exec fastlane ios beta
