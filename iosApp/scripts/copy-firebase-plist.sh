#!/bin/sh
# Build phase "Copy GoogleService-Info.plist" (M4 §4.3).
#
# Firebase là tuỳ chọn: file iosApp/Config/Firebase/<Debug|Release>/GoogleService-Info.plist do owner tải
# từ Firebase console và đã gitignore. Có file thì chép vào app (FirebaseBootstrap sẽ configure Firebase);
# không có thì xoá bản cũ còn sót trong app đã build và thoát 0, app chạy bằng các seam log/no-op.
# Không in nội dung file.
#
# Build setting FIREBASE_PLIST_DIR (mặc định $SRCROOT/Config/Firebase) đổi thư mục nguồn; trỏ tới thư mục
# rỗng để build bản "không Firebase" dù máy có plist, ví dụ:
#   xcodebuild ... FIREBASE_PLIST_DIR=/tmp/no-firebase
set -eu

PLIST_DIR="${FIREBASE_PLIST_DIR:-$SRCROOT/Config/Firebase}"
SOURCE="$PLIST_DIR/$CONFIGURATION/GoogleService-Info.plist"
DEST_DIR="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
DEST="$DEST_DIR/GoogleService-Info.plist"

if [ -f "$SOURCE" ]; then
  mkdir -p "$DEST_DIR"
  cp "$SOURCE" "$DEST"
  echo "firebase plist: copied for $CONFIGURATION"
else
  rm -f "$DEST"
  echo "firebase plist: none for $CONFIGURATION, app builds without Firebase"
fi
