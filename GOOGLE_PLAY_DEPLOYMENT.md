# Google Play Console Deployment Guide

This document describes how to use the GitHub Actions workflow to automate the release of Android App Bundles (.aab) to Google Play Console internal testing.

## Prerequisites

Before you can use the GitHub Actions workflow, you need to set up the following secrets in your GitHub repository:

1. **KEYSTORE_PASSWORD**: Password for the keystore
2. **KEY_ALIAS**: Alias for the key (usually "release")
3. **KEY_PASSWORD**: Password for the key
4. **PLAY_STORE_SERVICE_ACCOUNT_KEY_BASE64**: Base64-encoded Google Play Console service account key JSON file

## Setting Up Google Play Console Service Account

To deploy to Google Play Console, you need to create a service account:

1. Go to the [Google Play Console](https://play.google.com/console)
2. Navigate to Setup > API access
3. Create a new service account or use an existing one
4. Grant the service account the necessary permissions:
   - Release Manager role (for uploading and managing releases)
   - App Update role (for updating app metadata)
5. Create and download a JSON key file for the service account
6. Convert the JSON key file to base64:
   ```bash
   base64 -i path/to/service-account-key.json | tr -d '\n'
   ```
7. Add the base64-encoded key as a secret in your GitHub repository with the name `PLAY_STORE_SERVICE_ACCOUNT_KEY_BASE64`

## Setting Up GitHub Secrets

1. Go to your GitHub repository
2. Click on "Settings" > "Secrets and variables" > "Actions"
3. Click on "New repository secret"
4. Add each of the secrets listed in the Prerequisites section

For environment-specific secrets (e.g., different service account keys for staging and production), see the [GitHub Environments Guide](GITHUB_ENVIRONMENTS_GUIDE.md) for detailed instructions on how to set up and switch between different environments in GitHub Actions.

## Triggering the Release Workflow

The release workflow is triggered manually:

1. Go to your GitHub repository
2. Click on "Actions" > "Release to Google Play Console"
3. Click on "Run workflow"
4. Select the environment (staging or production)
5. Enter the release notes
6. Click "Run workflow"

## Workflow Details

The workflow performs the following steps:

1. Checks out the code
2. Sets up the Java environment
3. Automatically increments the version code in the build.gradle.kts file
4. Builds the Android App Bundle (.aab) with the appropriate signing keys
5. Uploads the AAB as an artifact (available for download from the GitHub Actions page)
6. Decodes the Google Play Console service account key
7. Deploys the AAB to Google Play Console internal testing
8. Commits the version code increment back to the repository (only if the deployment is successful)

## Version Code Management

The workflow automatically increments the version code in the `app/build.gradle.kts` file for each successful build. This ensures that each new release has a higher version code than the previous one, which is required by Google Play Console.

The version code is incremented in the release buildType section of the build.gradle.kts file, and the change is committed back to the repository with a commit message like "chore: Increment versionCode to 10012 [skip ci]".

Important: The version code is only committed back to the repository if the entire workflow, including the deployment to Google Play Console, is successful. This prevents incrementing the version number in the repository for failed deployments.

## Customizing the Workflow

You can customize the workflow by editing the `.github/workflows/release-playstore.yml` file:

- Change the default environment (staging or production)
- Change the default release notes
- Modify the track (internal, alpha, beta, production)
- Add additional build steps or tests
- Customize the commit message for version code increments

## Troubleshooting

If the workflow fails, check the following:

1. Make sure all the required secrets are set correctly
2. Check the build logs for any compilation errors
3. Verify that the Google Play Console service account key has the necessary permissions
4. Ensure the package name in the workflow file matches your app's package name
5. Check that the version code is being incremented correctly

## Additional Resources

- [Google Play Developer API Documentation](https://developers.google.com/android-publisher)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [r0adkll/upload-google-play Action Documentation](https://github.com/r0adkll/upload-google-play)
