# Keystore for Release Builds

This directory contains the keystore file used for signing release builds of the app.

## Generating a Keystore

To generate a new keystore file, run the following command:

```bash
keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
```

You will be prompted to enter a password for the keystore and the key. Make sure to remember these passwords as they will be needed for signing the app.

## Environment Variables

The build script uses the following environment variables for signing:

- `KEYSTORE_PASSWORD`: The password for the keystore file
- `KEY_ALIAS`: The alias of the key (default: "release")
- `KEY_PASSWORD`: The password for the key

You can set these environment variables in your CI/CD pipeline or locally before building the app.

## Example Usage

```bash
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="release"
export KEY_PASSWORD="your_key_password"
./gradlew assembleRelease
```

## Firebase App Distribution

To distribute the app to testers using Firebase App Distribution, make sure to:

1. Replace the placeholder Firebase App ID in `app/build.gradle.kts` with your actual Firebase App ID
2. Run one of the following commands:
   - `./gradlew distributeDebugApk` - Distributes the debug APK to testers
   - `./gradlew distributeReleaseApk` - Distributes the release APK to testers

## Generating APK and AAB Files

The following Gradle tasks are available for generating APK and AAB files:

- `./gradlew generateDebugApk` - Generates a debug APK
- `./gradlew generateReleaseApk` - Generates a release APK
- `./gradlew generateReleaseBundle` - Generates a release AAB (Android App Bundle)