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
5. **FIREBASE_SERVICE_ACCOUNT_JSON**: Firebase Service Account JSON
   - See [Firebase Service Account Setup Guide](FIREBASE_SERVICE_ACCOUNT_SETUP.md) for detailed instructions on how to set up and obtain a Firebase service account JSON
   - This is the recommended authentication method for Firebase App Distribution

6. **FIREBASE_DEBUG_APP_ID**: Your Firebase App ID for debug builds (found in the Firebase console)
7. **FIREBASE_RELEASE_APP_ID**: Your Firebase App ID for release builds (found in the Firebase console)
   - These App IDs can be found in the Firebase Console under Project Settings > General > Your apps

## Setting Up GitHub Secrets

1. Go to your GitHub repository
2. Click on "Settings" > "Secrets and variables" > "Actions"
3. Click on "New repository secret"
4. Add each of the secrets listed above

For environment-specific secrets (e.g., different Firebase App IDs for development, staging, and production), see the [GitHub Environments Guide](GITHUB_ENVIRONMENTS_GUIDE.md) for detailed instructions on how to set up and switch between different environments in GitHub Actions.

## Triggering the Release Process

The release process can be triggered in two ways:

### Automatic Trigger

The release job is automatically triggered when the workflow runs on a push to the main branch. This ensures that the app is only released after it passes all tests and builds successfully.

### Manual Trigger

The release process can also be triggered manually:

1. Go to your GitHub repository
2. Click on "Actions" > "Android Test"
3. Click on "Run workflow"
4. Select the build type (debug or release)
5. Enter the release notes and distribution groups (comma-separated)
6. Click "Run workflow"

## Workflow Details

The release process is now consolidated in a single workflow (`android.yml`) with multiple jobs:

### Test Job

This job runs first and performs unit tests:

1. Checks out the code
2. Sets up the Java environment
3. Runs unit tests
4. Uploads test results as artifacts

### Build Job

This job runs after the test job completes successfully:

1. Checks out the code
2. Sets up the Java environment
3. Builds the release APK
4. Uploads the APK as an artifact (available for download from the GitHub Actions page)

### Release Job

This job runs after the build job completes successfully, either when manually triggered or on a push to the main branch:

1. Checks out the code
2. Downloads the APK artifact from the build job
3. Finds the path to the downloaded APK
4. Distributes the APK to Firebase App Distribution using the Firebase Distribution GitHub Action:
   - Uses the appropriate Firebase App ID based on the build type
   - Authenticates using the Firebase service account JSON
   - Includes the specified release notes and distribution groups

This approach ensures that:
- The app is only released after passing all tests
- The same APK that was tested is the one that gets released
- The build process is not duplicated, saving time and resources
- The entire process is consolidated in a single workflow file for easier management

## Customizing the Workflow

You can customize the workflow by editing the `.github/workflows/android.yml` file:

- Change the default build type (debug or release)
- Change the default release notes
- Change the default distribution groups
- Add additional build steps or tests
- Modify the APK path if your build outputs are in a different location
- Customize the distribution groups based on the build type
- Adjust the conditions for when the release job should run

For more advanced customization, such as deploying to different environments (development, staging, production) with environment-specific secrets, see the [GitHub Environments Guide](GITHUB_ENVIRONMENTS_GUIDE.md) and check out the example workflow in `.github/workflows/environment_example.yml`.

## Troubleshooting

If the workflow fails, check the following:

1. Make sure all the required secrets are set correctly
2. Check the build logs for any compilation errors
3. Verify that the Firebase App IDs are correct:
   - FIREBASE_DEBUG_APP_ID should match the app ID in app/src/debug/google-services.json
   - FIREBASE_RELEASE_APP_ID should match the app ID in app/src/release/google-services.json
4. Ensure the Firebase service account has the necessary permissions
5. Make sure the selected build type matches the intended distribution target

For more detailed troubleshooting related to Firebase service accounts and App Distribution, see the [Troubleshooting section in the Firebase Service Account Setup Guide](FIREBASE_SERVICE_ACCOUNT_SETUP.md#troubleshooting).

## Integrating with Existing Processes

The release process is now fully integrated into the main workflow:

1. The `android.yml` workflow runs automatically on every push to main and pull request
2. The workflow runs the test, build, and release jobs in sequence
3. The release job distributes the APK to Firebase App Distribution
4. Monitor the distribution in the Firebase console

For more control, you can manually trigger the workflow:
   - Use the debug build type for internal testing
   - Use the release build type for beta testing or production releases
   - Use different distribution groups for debug and release builds to target different testers
   - The manual trigger allows you to specify custom release notes and distribution groups

## Google Play Console Deployment

For deploying to Google Play Console internal testing, use the `release-playstore.yml` workflow instead. This workflow:

- Builds an Android App Bundle (.aab) instead of an APK
- Automatically increments the version code
- Deploys to Google Play Console internal testing
- Commits the version code increment back to the repository

See the [Google Play Deployment Guide](GOOGLE_PLAY_DEPLOYMENT.md) for detailed instructions on how to set up and use this workflow.
