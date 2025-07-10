# Kotlin Multiplatform Migration Plan for Podcast App

## Overview

This document outlines the plan to migrate the Podcast app from a pure Android application to a Kotlin Multiplatform (KMP) project. The migration will be done incrementally, starting with the most suitable modules and gradually expanding to more complex ones.

## Current Project Analysis

The project is a modular Android application with the following key components:

- **App Module**: Main Android application module with UI and business logic
- **Core Modules**: Core functionality like API, authentication, etc.
- **Feature Modules**: Feature-specific modules like Radio, Onboarding, etc.
- **Shared Modules**: Shared functionality like resources, libraries, etc.

The project uses:
- Kotlin 2.0.0
- Compose UI for the user interface
- Hilt for dependency injection
- Retrofit for networking
- Room for local database
- Firebase for analytics, crashlytics, and messaging
- ExoPlayer for media playback

## Migration Strategy

### Phase 1: Setup and Infrastructure

1. **Update Gradle Configuration**
   - Add Kotlin Multiplatform plugin to the project
   - Configure Gradle for multiplatform builds
   - Update version catalog with multiplatform dependencies

2. **Create Common Module Structure**
   - Set up `commonMain`, `androidMain`, and other platform-specific source sets
   - Configure multiplatform test infrastructure

### Phase 2: Migrate Core Modules

Start with modules that contain mostly platform-independent code:

1. **Create a new KMP module for models**
   - Migrate data models from `coreApi/models` to a new KMP module
   - Replace Android-specific annotations with multiplatform alternatives
   - Update imports in dependent modules

2. **Migrate Utility Functions**
   - Create a KMP utilities module
   - Migrate platform-independent utilities from `sharedLibrary/utils`
   - Create expect/actual declarations for platform-specific utilities

### Phase 3: Migrate Business Logic

1. **Network Layer**
   - Create KMP network module
   - Migrate API interfaces and models
   - Implement platform-specific network clients using expect/actual

2. **Data Repositories**
   - Migrate repository interfaces to KMP
   - Create platform-specific implementations for data sources

### Phase 4: Platform-Specific UI and Features

1. **Keep Android-specific UI in Android modules**
   - Maintain Compose UI code in Android modules
   - Create interfaces in common modules for UI-business logic interaction

2. **Implement iOS UI (Future)**
   - Create SwiftUI implementations for iOS
   - Connect to common business logic

## Module Migration Priority

### High Priority (Easiest to Migrate)
1. **Models and DTOs** - Platform-independent data classes
2. **Constants** - Simple constant values
3. **Utility Functions** - Platform-independent helper functions

### Medium Priority
1. **Network Interfaces** - API definitions
2. **Repository Interfaces** - Data access patterns
3. **Business Logic** - Core functionality

### Low Priority (Most Difficult)
1. **ViewModels** - Android lifecycle-dependent
2. **UI Components** - Platform-specific UI
3. **Media Playback** - Platform-specific functionality

## Implementation Details

### Example: Migrating a Model Class

Current Android-specific model:
```kotlin
// In coreApi/models/TSDataState.kt
import androidx.annotation.Keep

@Keep
sealed class TSDataState<T : Any>(
    open val data: T?
) {
    data class Success<T : Any>(override val data: T) : TSDataState<T>(data)
    data class Loading<T : Any>(override val data: T? = null) : TSDataState<T>(data)
    data class Error<T : Any>(
        val exception: Throwable,
        override val data: T? = null
    ) : TSDataState<T>(data)
}
```

Migrated KMP model:
```kotlin
// In commonMain
sealed class TSDataState<T : Any>(
    open val data: T?
) {
    data class Success<T : Any>(override val data: T) : TSDataState<T>(data)
    data class Loading<T : Any>(override val data: T? = null) : TSDataState<T>(data)
    data class Error<T : Any>(
        val exception: Throwable,
        override val data: T? = null
    ) : TSDataState<T>(data)
}
```

### Example: Platform-Specific Implementation

For platform-specific code, use expect/actual declarations:

```kotlin
// In commonMain
expect fun getPlatformName(): String

// In androidMain
actual fun getPlatformName(): String = "Android"

// In iosMain
actual fun getPlatformName(): String = "iOS"
```

## Challenges and Considerations

1. **Android-Specific Dependencies**
   - Many modules rely heavily on Android-specific libraries
   - Need to create platform-independent abstractions

2. **UI Framework**
   - Current UI is built with Jetpack Compose
   - Consider Compose Multiplatform for shared UI in the future

3. **Media Playback**
   - ExoPlayer is Android-specific
   - Need platform-specific implementations for media playback

4. **Testing Infrastructure**
   - Need to set up multiplatform testing
   - Ensure tests run on all target platforms

## Timeline and Milestones

1. **Phase 1: Setup and Infrastructure** - 2 weeks
2. **Phase 2: Migrate Core Modules** - 4 weeks
3. **Phase 3: Migrate Business Logic** - 6 weeks
4. **Phase 4: Platform-Specific UI and Features** - Ongoing

## Conclusion

Migrating to Kotlin Multiplatform will be a gradual process, focusing first on the most suitable modules and expanding to more complex ones. The migration will enable code sharing between platforms while maintaining the existing Android functionality.