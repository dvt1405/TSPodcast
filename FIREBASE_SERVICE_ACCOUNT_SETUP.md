# Firebase Service Account Setup Guide

This document provides instructions on how to set up a Firebase service account for use with GitHub Actions to deploy your app to Firebase App Distribution.

## What is a Firebase Service Account?

A Firebase service account is a type of Google Cloud service account that provides secure authentication for server-to-server interactions. Unlike Firebase CLI tokens, service accounts:

- Don't expire frequently
- Have more granular permission controls
- Are the recommended approach for CI/CD pipelines

## Prerequisites

Before you begin, make sure you have:

1. A Google account with access to Firebase
2. Firebase project created for your app
3. Owner or Editor role on the Firebase project

## Step-by-Step Guide to Create a Firebase Service Account

### 1. Access the Firebase Console

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Select your project

### 2. Generate a Service Account Key

1. In the Firebase Console, click on the gear icon (⚙️) next to "Project Overview" to open Project settings
2. Click on the "Service accounts" tab
3. Under "Firebase Admin SDK", click on "Generate new private key"
4. Click "Generate key" in the popup dialog
5. A JSON file will be downloaded to your computer - this is your service account key file
6. Keep this file secure and don't commit it to your repository

### 3. Add the Service Account Key to GitHub Secrets

1. Open the downloaded JSON file in a text editor
2. Copy the entire contents of the file
3. Go to your GitHub repository
4. Click on "Settings" > "Secrets and variables" > "Actions"
5. Click on "New repository secret"
6. Name the secret `FIREBASE_SERVICE_ACCOUNT_JSON`
7. Paste the entire JSON content as the value
8. Click "Add secret"

## Updating Your Workflow

The GitHub workflow has been updated to use the Firebase Distribution GitHub Action with the service account authentication method. The key changes are:

1. Removed the Firebase CLI installation step
2. Added a step to find the APK path
3. Used the `wzieba/Firebase-Distribution-Github-Action@v1` action for distribution
4. Changed authentication from token-based to service account-based

## Permissions Required

For the service account to work with Firebase App Distribution, it needs the following permissions:

1. Firebase App Distribution Admin role
2. Firebase Admin SDK role

These permissions are typically granted by default when you create a service account through the Firebase Console.

## Troubleshooting

If you encounter issues with the service account:

1. **Permission Issues**: Make sure the service account has the necessary permissions in the Firebase project. You may need to add the service account email as a member to your Firebase project with appropriate roles.

2. **Invalid Service Account**: Verify that the service account JSON is correctly formatted and complete. The entire JSON file content should be added to the GitHub secret.

3. **App ID Issues**: Ensure that your Firebase App IDs are correct and match the ones in your Firebase project.

4. **Workflow Errors**: Check the GitHub Actions logs for specific error messages that might indicate what's wrong.

## Additional Resources

- [Firebase Service Account Documentation](https://firebase.google.com/docs/admin/setup#initialize-sdk)
- [Firebase App Distribution Documentation](https://firebase.google.com/docs/app-distribution)
- [Firebase Distribution GitHub Action](https://github.com/wzieba/Firebase-Distribution-Github-Action)