# GitHub Actions Release Process

This document describes how to use the GitHub Actions workflow to automate the release of APKs to Firebase App Distribution.

## Prerequisites

Before you can use the GitHub Actions workflow, you need to set up the following secrets in your GitHub repository:

1. **KEYSTORE_BASE64**: Base64-encoded keystore file
   ```bash
   base64 -i keystore/release.keystore | tr -d '\n'
   ```

2. **KEYSTORE_PASSWORD**: Password for the keystore
3. **KEY_ALIAS**: Alias for the key (usually "release")
4. **KEY_PASSWORD**: Password for the key
5. **FIREBASE_TOKEN**: Firebase CLI token
   - See [Firebase Token Setup Guide](FIREBASE_TOKEN_SETUP.md) for detailed instructions on how to set up and obtain a Firebase token from the Firebase console
   - Quick command reference:
   ```bash
   firebase login:ci
   ```

6. **FIREBASE_DEBUG_APP_ID**: Your Firebase App ID for debug builds (found in the Firebase console)
7. **FIREBASE_RELEASE_APP_ID**: Your Firebase App ID for release builds (found in the Firebase console)
   - See [Firebase Token Setup Guide](FIREBASE_TOKEN_SETUP.md#setting-up-firebase-app-ids) for instructions on how to find your Firebase App IDs

## Setting Up GitHub Secrets

1. Go to your GitHub repository
2. Click on "Settings" > "Secrets and variables" > "Actions"
3. Click on "New repository secret"
4. Add each of the secrets listed above

## Triggering the Release Workflow

The release workflow is triggered manually:

1. Go to your GitHub repository
2. Click on "Actions" > "Release to Firebase App Distribution"
3. Click on "Run workflow"
4. Select the build type (debug or release)
5. Enter the release notes and distribution groups (comma-separated)
6. Click "Run workflow"

## Workflow Details

The workflow performs the following steps:

1. Checks out the code
2. Sets up the Java environment
3. Sets up the keystore from the base64-encoded secret
4. Builds the APK (debug or release, based on the selected build type)
5. Uploads the APK as an artifact (available for download from the GitHub Actions page)
6. Distributes the APK to Firebase App Distribution using the appropriate Firebase App ID:
   - For debug builds: Uses the FIREBASE_DEBUG_APP_ID
   - For release builds: Uses the FIREBASE_RELEASE_APP_ID

## Customizing the Workflow

You can customize the workflow by editing the `.github/workflows/release.yml` file:

- Change the default build type (debug or release)
- Change the default release notes
- Change the default distribution groups
- Add additional build steps or tests
- Modify the APK path if your build outputs are in a different location
- Customize the distribution groups based on the build type

## Troubleshooting

If the workflow fails, check the following:

1. Make sure all the required secrets are set correctly
2. Check the build logs for any compilation errors
3. Verify that the Firebase App IDs are correct:
   - FIREBASE_DEBUG_APP_ID should match the app ID in app/src/debug/google-services.json
   - FIREBASE_RELEASE_APP_ID should match the app ID in app/src/release/google-services.json
4. Ensure the Firebase token has the necessary permissions
5. Make sure the selected build type matches the intended distribution target

For more detailed troubleshooting related to Firebase tokens and App Distribution, see the [Troubleshooting section in the Firebase Token Setup Guide](FIREBASE_TOKEN_SETUP.md#troubleshooting).

## Integrating with Existing Processes

This workflow can be integrated with your existing release process:

1. Run tests using the `android.yml` workflow
2. If tests pass, manually trigger the release workflow:
   - Use the debug build type for internal testing
   - Use the release build type for beta testing or production releases
3. Monitor the distribution in the Firebase console
4. Use different distribution groups for debug and release builds to target different testers
