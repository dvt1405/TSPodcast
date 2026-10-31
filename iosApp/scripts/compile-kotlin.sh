#!/bin/sh
# Build Phase "Compile Kotlin" của target TSPodcast (cách tích hợp A, setup-and-build §3.1).
# Gradle đọc CONFIGURATION, SDK_NAME, ARCHS, ... từ môi trường Xcode và đặt Shared.framework vào
# shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME), nơi FRAMEWORK_SEARCH_PATHS trỏ tới.
set -e

if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
  exit 0
fi

# Xcode mở từ Finder/Dock không kế thừa JAVA_HOME của shell: lấy JDK mặc định của máy.
if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  export JAVA_HOME
fi

cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
