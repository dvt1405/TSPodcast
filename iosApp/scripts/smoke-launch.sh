#!/bin/sh
# Smoke check lúc launch (M4 §4.8, luật test của owner: không UI test cho tới pha QC cuối).
#
#   iosApp/scripts/smoke-launch.sh <udid> <đường dẫn TSPodcast.app> [--check-db]
#
# Boot simulator nếu cần, cài app, launch tss.t.podcast, chờ 15 s rồi kiểm:
#   (1) process còn trong `launchctl list` của simulator;
#   (2) không có crash report TSPodcast mới trong ~/Library/Logs/DiagnosticReports;
#   (3) với --check-db: file Library/Application Support/TSPodcastDatabase.db có trong data container.
# In "SMOKE PASS" và thoát 0, ngược lại in lý do + "SMOKE FAIL" và thoát 1.
# Không chạm UI, không chụp màn hình, không in secret.
set -u

BUNDLE_ID="tss.t.podcast"
WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-15}"
REPORTS_DIR="$HOME/Library/Logs/DiagnosticReports"

if [ $# -lt 2 ]; then
  echo "usage: $0 <udid> <path to TSPodcast.app> [--check-db]" >&2
  exit 2
fi
UDID="$1"
APP_PATH="$2"
CHECK_DB=0
if [ "${3:-}" = "--check-db" ]; then CHECK_DB=1; fi

fail() {
  echo "smoke: $1"
  echo "SMOKE FAIL"
  exit 1
}

[ -d "$APP_PATH" ] || fail "app bundle not found: $APP_PATH"

echo "smoke: booting $UDID (if needed)"
xcrun simctl bootstatus "$UDID" -b >/dev/null 2>&1 || fail "could not boot simulator $UDID"

MARKER="$(mktemp -t tspodcast-smoke)"
# Crash report mới phải mới hơn marker; lùi 1 s để không trùng giây với report cũ.
sleep 1

echo "smoke: installing $(basename "$APP_PATH")"
xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
xcrun simctl install "$UDID" "$APP_PATH" || fail "simctl install failed"

echo "smoke: launching $BUNDLE_ID"
xcrun simctl launch "$UDID" "$BUNDLE_ID" >/dev/null || fail "simctl launch failed"

echo "smoke: waiting ${WAIT_SECONDS}s"
sleep "$WAIT_SECONDS"

STATUS=0
if xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep "$BUNDLE_ID" >/dev/null; then
  echo "smoke: process running"
else
  echo "smoke: process $BUNDLE_ID is not running after ${WAIT_SECONDS}s"
  STATUS=1
fi

CRASHES="$(find "$REPORTS_DIR" -iname '*TSPodcast*' -newer "$MARKER" 2>/dev/null)"
if [ -n "$CRASHES" ]; then
  echo "smoke: new crash report(s):"
  echo "$CRASHES"
  STATUS=1
else
  echo "smoke: no new crash report"
fi

if [ "$CHECK_DB" -eq 1 ]; then
  DATA="$(xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" data 2>/dev/null)"
  DB="$DATA/Library/Application Support/TSPodcastDatabase.db"
  if [ -n "$DATA" ] && [ -f "$DB" ]; then
    echo "smoke: database file exists"
  else
    echo "smoke: database file missing: Library/Application Support/TSPodcastDatabase.db"
    STATUS=1
  fi
fi

rm -f "$MARKER"
if [ "$STATUS" -eq 0 ]; then
  echo "SMOKE PASS"
  exit 0
fi
echo "SMOKE FAIL"
exit 1
