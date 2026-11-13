#!/bin/bash
# App Store screenshots for every app language, captured on a connected iPhone.
#
#   iosApp/scripts/store-screenshots.sh <device-udid> [lang:locale ...]
#
# 1. Builds the UI tests once (team 3AYA8D7LW2) and runs StoreScreenshotsUITests per language.
# 2. Frames the captures with brand/store/screenshots/compose_ios.py (captions: ios-captions.json).
# 3. Writes JPEGs to iosApp/fastlane/screenshots/<App Store locale>/NN-name.jpg.
# Upload them with the App Store Connect API (appScreenshotSets, display type APP_IPHONE_67).
# Everything temporary lives in $WORK and is deleted at the end (DerivedData included).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEVICE="${1:?device UDID (xcrun xctrace list devices)}"; shift || true
LANGS=("$@")
[ ${#LANGS[@]} -eq 0 ] && LANGS=(vi:vi_VN en:en_US de:de_DE es:es_ES fr:fr_FR hi:hi_IN id:id_ID it:it_IT ja:ja_JP ko:ko_KR nl:nl_NL pl:pl_PL pt-BR:pt_BR sv:sv_SE tr:tr_TR)
WORK="$(mktemp -d /tmp/tsp-store-shots.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
cd "$ROOT"

xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "platform=iOS,id=$DEVICE" \
  -derivedDataPath "$WORK/dd" DEVELOPMENT_TEAM=3AYA8D7LW2 -allowProvisioningUpdates build-for-testing -quiet

for pair in "${LANGS[@]}"; do
  L="${pair%%:*}"; LOC="${pair##*:}"; R="$WORK/res-$L.xcresult"
  mkdir -p "$WORK/att-$L" "$WORK/raw/$L"
  for try in 1 2; do
    TEST_RUNNER_STORE_LANG="$L" TEST_RUNNER_STORE_LOCALE="$LOC" xcodebuild -project iosApp/iosApp.xcodeproj \
      -scheme TSPodcast -destination "platform=iOS,id=$DEVICE" -derivedDataPath "$WORK/dd" -resultBundlePath "$R" \
      -only-testing:TSPodcastUITests/StoreScreenshotsUITests test-without-building > "$WORK/log-$L.txt" 2>&1 && break
    rm -rf "$R"
  done
  [ -d "$R" ] || { echo "$L: capture failed, see the log above"; tail -20 "$WORK/log-$L.txt"; continue; }
  xcrun xcresulttool export attachments --path "$R" --output-path "$WORK/att-$L" >/dev/null
  python3 -I - "$WORK" "$L" <<'EOF'
import json, shutil, sys
work, lang = sys.argv[1], sys.argv[2]
for test in json.load(open(f"{work}/att-{lang}/manifest.json")):
    for a in test.get("attachments", []):
        name = a.get("suggestedHumanReadableName", "")
        for screen in ("01-home", "02-detail", "03-player", "04-search"):
            if name.startswith(screen):
                shutil.copy(f"{work}/att-{lang}/{a['exportedFileName']}", f"{work}/raw/{lang}/{screen}.png")
EOF
  echo "$L: $(ls "$WORK/raw/$L" | wc -l | tr -d ' ') screens"
done

python3 -I brand/store/screenshots/compose_ios.py "$WORK/raw" "$WORK/out"
OUT=iosApp/fastlane/screenshots
for dir in "$WORK"/out/*/; do
  locale="$(basename "$dir")"; mkdir -p "$OUT/$locale"
  for png in "$dir"*.png; do
    sips -s format jpeg -s formatOptions 88 "$png" --out "$OUT/$locale/$(basename "${png%.png}").jpg" >/dev/null
  done
done
echo "screenshots in $OUT"
