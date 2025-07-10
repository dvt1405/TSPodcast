# Podcast App

A modern Android podcast application built with Kotlin and Jetpack Compose.

## Features

- Stream and download podcasts
- Radio streaming functionality
- User authentication
- Personalized recommendations
- Firebase integration for analytics and crash reporting
- Google Play and Firebase App Distribution deployment

## Project Structure

The project follows a modular architecture with the following components:

- **app**: Main application module
- **core**: Core functionality and utilities
- **podcasts**: Podcast-related features
- **featureRadio**: Radio streaming functionality
- **featureOnboarding**: User onboarding experience
- **sharedPlayer**: Media player implementation
- **sharedResources**: Shared resources (strings, colors, etc.)
- **sharedFirebase**: Firebase integration

## Setup Instructions

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 11 or newer
- Gradle 7.0 or newer

### Getting Started

1. Clone the repository
2. Create a `local.properties` file in the project root with the following properties:
   ```
   KEYSTORE_PASSWORD=your_keystore_password
   KEY_ALIAS=your_key_alias
   KEY_PASSWORD=your_key_password
   ```
3. Open the project in Android Studio
4. Sync the project with Gradle files
5. Run the app on an emulator or physical device

## Build and Deployment

### Building the App

- Debug build: `./gradlew assembleDebug`
- Release build: `./gradlew assembleRelease`
- Generate AAB for Play Store: `./gradlew bundleRelease`

### Deployment

This project uses GitHub Actions for automated deployment to both Firebase App Distribution and Google Play Console.

## Documentation

For more detailed information, please refer to the following documentation:

- [GitHub Actions Release Process](GITHUB_ACTIONS_RELEASE.md) - How to use GitHub Actions to deploy to Firebase App Distribution
- [Google Play Deployment Guide](GOOGLE_PLAY_DEPLOYMENT.md) - How to deploy to Google Play Console
- [Firebase Token Setup Guide](FIREBASE_TOKEN_SETUP.md) - How to set up Firebase tokens for deployment
- [GitHub Environments Guide](GITHUB_ENVIRONMENTS_GUIDE.md) - How to use GitHub Environments for deployment
- [Keystore Password Injection](KEYSTORE_PASSWORD_INJECTION.md) - How to securely manage keystore passwords
- [Release Process](RELEASE_PROCESS.md) - Detailed release process documentation
- [KMP Migration Plan](KMP_MIGRATION_PLAN.md) - Plan for migrating to Kotlin Multiplatform

## Contributing

Please read our contribution guidelines before submitting a pull request.

## License

This project is licensed under the [MIT License](LICENSE).