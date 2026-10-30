#!/bin/sh
# Build phase "Crashlytics dSYM" (M4 §4.3): upload dSYM lên Crashlytics bằng script `run` của
# firebase-ios-sdk. Chỉ chạy cho Release (bản phát hành; Debug không cần symbol trên Crashlytics và không
# gửi gì ra ngoài lúc build hằng ngày). Bỏ qua (thoát 0) khi app không có GoogleService-Info.plist hoặc khi
# không tìm thấy script, để build không fail khi chưa có Firebase.
set -u

if [ "${CONFIGURATION:-}" != "Release" ]; then
  echo "crashlytics: $CONFIGURATION build, skip dSYM upload"
  exit 0
fi

PLIST="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/GoogleService-Info.plist"
if [ ! -f "$PLIST" ]; then
  echo "crashlytics: no GoogleService-Info.plist in the app, skip dSYM upload"
  exit 0
fi

RUN_SCRIPT="${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"
if [ ! -x "$RUN_SCRIPT" ]; then
  echo "warning: crashlytics: $RUN_SCRIPT not found, skip dSYM upload"
  exit 0
fi

"$RUN_SCRIPT" || echo "warning: crashlytics: dSYM upload failed (build continues)"
exit 0
