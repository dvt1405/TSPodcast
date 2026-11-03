#!/bin/bash
set -e

APP_ID="1:522103746332:android:561c9f81fd5a7cc501d4d9"
RELEASE_NOTES="${RELEASE_NOTES:-"Release build with stable features"}"
GROUPS="${GROUPS:-"TSPodcastRelease"}"

echo "Building release AAB..."
./gradlew bundleRelease

AAB_PATH=$(find app/release -name "*_R-release.aab" | sort -V | tail -1)

if [ -z "$AAB_PATH" ]; then
    echo "Error: AAB file not found in app/release/"
    exit 1
fi

echo "Found AAB: $AAB_PATH"
echo "Uploading to Firebase App Distribution..."

firebase appdistribution:distribute "$AAB_PATH" \
    --app "$APP_ID" \
    --release-notes "$RELEASE_NOTES" \
    --groups "$GROUPS"

echo "Upload complete."
