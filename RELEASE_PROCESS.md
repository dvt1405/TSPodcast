# Release Process

This document describes the process for building and distributing release versions of the app.

## Prerequisites

1. Make sure you have the necessary keystore file for signing release builds:
   - The keystore file should be located at `keystore/release.keystore`
   - If you don't have a keystore file, follow the instructions in `keystore/README.md` to generate one

2. Set up environment variables for signing:
   ```bash
   export KEYSTORE_PASSWORD="your_keystore_password"
   export KEY_ALIAS="release"
   export KEY_PASSWORD="your_key_password"
   ```

3. Make sure you have Firebase App Distribution set up:
   - The app is configured to use different Firebase App IDs for debug and release builds:
     - Debug: `1:187551572250:android:e952fdb02a12421bd60ca3` (from `app/src/debug/google-services.json`)
     - Release: `1:522103746332:android:561c9f81fd5a7cc501d4d9` (from `app/src/release/google-services.json`)
   - Make sure you have the Firebase CLI installed and authenticated

## Building APK and AAB Files

### Debug APK

To build a debug APK:

```bash
./gradlew generateDebugApk
```

The debug APK will be generated at `app/build/outputs/apk/debug/`.

### Release APK

To build a release APK:

```bash
./gradlew generateReleaseApk
```

The release APK will be generated at `app/build/outputs/apk/release/`.

### Release AAB (Android App Bundle)

To build a release AAB:

```bash
./gradlew generateReleaseBundle
```

The release AAB will be generated at `app/build/outputs/bundle/release/`.

## Distributing to Testers

### Distributing Debug APK

To distribute the debug APK to testers using Firebase App Distribution:

```bash
./gradlew distributeDebugApk
```

This will distribute the debug APK to the "debug-testers" group using the debug Firebase App ID.

### Distributing Release APK

To distribute the release APK to testers using Firebase App Distribution:

```bash
./gradlew distributeReleaseApk
```

This will distribute the release APK to the "release-testers" group using the release Firebase App ID.

### Distribution Groups

- **debug-testers**: Typically internal developers and QA testers who need to test the latest features
- **release-testers**: Beta testers and stakeholders who test more stable builds before production release

## Build Configuration

The app is configured with the following build attributes:

1. **Signing Configuration**:
   - The release builds are signed using the keystore file at `keystore/release.keystore`
   - The signing configuration uses environment variables for passwords and key alias

2. **Build Types**:
   - Debug: Minimal configuration for development and testing
   - Release: Optimized with minification and resource shrinking enabled

3. **Bundle Configuration**:
   - Language, density, and ABI splits are enabled for optimized APK delivery

4. **Firebase App Distribution**:
   - Configured separately for debug and release builds:
     - Debug builds use the debug Firebase App ID and target the "debug-testers" group
     - Release builds use the release Firebase App ID and target the "release-testers" group
   - Each build type can have different release notes and target different tester groups

## Troubleshooting

If you encounter issues with the build process:

1. Make sure the keystore file exists and is accessible
2. Verify that the environment variables for signing are set correctly
3. Check the Firebase App IDs in `app/build.gradle.kts` match the ones in the google-services.json files
4. Run the build with the `--stacktrace` option for more detailed error information:
   ```bash
   ./gradlew generateReleaseApk --stacktrace
   ```

## CI/CD Integration

For CI/CD pipelines, make sure to:

1. Set up the necessary environment variables for signing
2. Store the keystore file securely
3. Configure the pipeline to run the appropriate Gradle tasks
4. Set up Firebase App Distribution integration

### GitHub Actions Integration

This project includes a GitHub Actions workflow for automating the release process. See [GITHUB_ACTIONS_RELEASE.md](GITHUB_ACTIONS_RELEASE.md) for detailed instructions on:

1. Setting up required GitHub secrets
2. Triggering the release workflow
3. Customizing the workflow
4. Troubleshooting common issues
