# Firebase Token Setup Guide

This document provides detailed instructions on how to set up and obtain a Firebase token for use with GitHub Actions to deploy your app to Firebase App Distribution.

## What is a Firebase Token?

A Firebase token (also known as a Firebase CLI token) is a secure authentication token that allows automated systems like GitHub Actions to interact with Firebase services on your behalf. This token is required for CI/CD pipelines to deploy your app to Firebase App Distribution without manual intervention.

## Prerequisites

Before you begin, make sure you have:

1. A Google account with access to Firebase
2. Firebase project created for your app
3. Firebase CLI installed on your local machine

## Installing Firebase CLI

If you haven't installed the Firebase CLI yet, you can do so using npm:

```bash
npm install -g firebase-tools
```

## Step-by-Step Guide to Generate a Firebase Token

### 1. Create a Firebase Project (if you don't have one already)

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click on "Add project" or select your existing project
3. Follow the setup wizard to create your project if needed

### 2. Set Up Firebase App Distribution

1. In the Firebase Console, select your project
2. In the left sidebar, click on "App Distribution" under the "Release & Monitor" section
3. Follow the prompts to set up App Distribution for your project
4. Note your Firebase App ID, which you'll need for GitHub Actions:
   - For Android, it will look like: `1:123456789012:android:abcdef1234567890`

### 3. Generate a Firebase Token

1. Open a terminal on your local machine
2. Log in to Firebase using the CLI:
   ```bash
   firebase login
   ```
   This will open a browser window where you need to authenticate with your Google account.

3. Once logged in, generate a CI token:
   ```bash
   firebase login:ci
   ```

4. A browser window will open again asking for permissions. Grant the requested permissions.

5. After successful authentication, the terminal will display your Firebase token. It will look something like:
   ```
   ✔ Success! Use this token to login on a CI server:
   1//03kXpqwZkLvOACgYIARAAGAMSNwF-L9IrBBXnwpub0Vp4Sc0JaMrI-dMnLkj1...
   ```

6. Copy this token - you'll need it for setting up GitHub Actions.

> **IMPORTANT**: Keep this token secure! It provides access to your Firebase projects and should be treated like a password.

## Adding the Firebase Token to GitHub Secrets

1. Go to your GitHub repository
2. Click on "Settings" > "Secrets and variables" > "Actions"
3. Click on "New repository secret"
4. Create a new secret with the name `FIREBASE_TOKEN`
5. Paste your Firebase token as the value
6. Click "Add secret"

## Setting Up Firebase App IDs

You'll also need to add your Firebase App IDs as secrets:

1. In the Firebase Console, go to Project Settings > General
2. Find your app under "Your apps" section
3. Copy the App ID (it looks like `1:123456789012:android:abcdef1234567890`)
4. Add these as GitHub secrets:
   - `FIREBASE_DEBUG_APP_ID` for your debug build
   - `FIREBASE_RELEASE_APP_ID` for your release build

## Verifying Your Setup

To verify that your Firebase token is working correctly:

1. Make sure all required secrets are set in GitHub:
   - `FIREBASE_TOKEN`
   - `FIREBASE_DEBUG_APP_ID` and/or `FIREBASE_RELEASE_APP_ID`
   - Other required secrets for signing your app

2. Trigger the release workflow manually:
   - Go to your GitHub repository
   - Click on "Actions" > "Release to Firebase App Distribution"
   - Click on "Run workflow"
   - Fill in the required parameters
   - Click "Run workflow"

3. Monitor the workflow execution to ensure it completes successfully
4. Check the Firebase Console to verify that your app was distributed correctly

## Troubleshooting

If you encounter issues with your Firebase token:

1. **Token Expired**: Firebase tokens can expire. Generate a new token using `firebase login:ci` and update your GitHub secret.

2. **Permission Issues**: Make sure the Google account used to generate the token has the necessary permissions in the Firebase project.

3. **Invalid App ID**: Verify that your Firebase App IDs are correct and match the ones in your Firebase project.

4. **CLI Version Issues**: Make sure you're using a recent version of the Firebase CLI. Update it using `npm install -g firebase-tools`.

5. **Workflow Errors**: Check the GitHub Actions logs for specific error messages that might indicate what's wrong.

## Rotating Your Firebase Token

For security reasons, it's a good practice to rotate your Firebase token periodically:

1. Generate a new token using `firebase login:ci`
2. Update the `FIREBASE_TOKEN` secret in your GitHub repository
3. Verify that the new token works by running the workflow

## Additional Resources

- [Firebase App Distribution Documentation](https://firebase.google.com/docs/app-distribution)
- [Firebase CLI Reference](https://firebase.google.com/docs/cli)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)