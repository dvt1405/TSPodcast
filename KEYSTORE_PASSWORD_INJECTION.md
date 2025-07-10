# Injecting KEYSTORE_PASSWORD into Gradle Build

This document explains how to inject the `KEYSTORE_PASSWORD` and other signing configuration properties into the Gradle build process.

## Solution Implemented

The solution involves using the `local.properties` file to store the signing configuration properties. This file is already set up to not be committed to version control, making it a secure place to store sensitive information.

### Changes Made

1. Added signing configuration properties to `local.properties`:
   ```properties
   # Signing configuration
   KEYSTORE_PASSWORD=your_keystore_password_here
   KEY_ALIAS=release
   KEY_PASSWORD=your_key_password_here
   ```

2. Modified `app/build.gradle.kts` to load and use properties from `local.properties`:
   ```
   // Add these imports at the top of the file
   // import java.util.Properties
   // import java.io.FileInputStream

   // Load local.properties
   val localProperties = Properties()
   val localPropertiesFile = rootProject.file("local.properties")
   if (localPropertiesFile.exists()) {
       localProperties.load(FileInputStream(localPropertiesFile))
   }
   ```

3. Updated the `signingConfigs` section to read properties from `local.properties` with fallbacks:
   ```
   signingConfigs {
       create("release") {
           storeFile = file("$projectDir/KeyStore")
           storePassword = localProperties.getProperty("KEYSTORE_PASSWORD") 
               ?: System.getenv("KEYSTORE_PASSWORD") 
               ?: "changeit"
           keyAlias = localProperties.getProperty("KEY_ALIAS") 
               ?: System.getenv("KEY_ALIAS") 
               ?: "release"
           keyPassword = localProperties.getProperty("KEY_PASSWORD") 
               ?: System.getenv("KEY_PASSWORD") 
               ?: "changeit"
       }
   }
   ```

## How to Use

1. Open your `local.properties` file (it's located in the root of your project).
2. Add the following lines, replacing the placeholder values with your actual passwords:
   ```properties
   # Signing configuration
   KEYSTORE_PASSWORD=your_actual_keystore_password
   KEY_ALIAS=your_key_alias
   KEY_PASSWORD=your_key_password
   ```
3. Build your project as usual. The Gradle build will now use the values from `local.properties`.

## Alternative Methods

If you prefer not to use `local.properties`, you can also:

1. Set environment variables before running the build:
   ```bash
   export KEYSTORE_PASSWORD=your_password
   ./gradlew assembleRelease
   ```

2. Pass properties via command line:
   ```bash
   ./gradlew assembleRelease -PKEYSTORE_PASSWORD=your_password
   ```
   (Note: This would require additional changes to the build script to read from project properties)

3. Create a separate `keystore.properties` file (would require similar changes as implemented for `local.properties`).

## Security Considerations

- Never commit files containing passwords to version control
- Ensure `local.properties` is in your `.gitignore` file (it already is by default)
- Consider using a secure password manager for team sharing of these credentials
