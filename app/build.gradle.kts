import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serilization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    id("applovin-quality-service")
}

applovin {
    apiKey = "avJkJkTUhqmoZGJTcdqgylRVHN9sbc3ZSUbub3UL1fXR3fXOyqupneiTBSvfi8FT0iI060T6danq4i-z5ZaZJp"
}

android {
    namespace = "tss.t.podcast"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        applicationId = "tss.t.podcast"
        versionCode = 10006
        versionName = "v1.4.$versionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("$projectDir/KeyStore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "changeit"
            keyAlias = System.getenv("KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "changeit"
        }
    }

    buildTypes {
        debug {
            setProperty("archivesBaseName", "TSPodcast.${defaultConfig.versionName}_D")
        }
        release {
            defaultConfig.versionCode = 10011
            defaultConfig.versionName = "v1.4.${defaultConfig.versionCode}"
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            setProperty("archivesBaseName", "TSPodcast.${defaultConfig.versionName}_R")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.google.material)
    implementation(libs.material)
    implementation(libs.material.icons)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.exoplayer.ima)
    implementation(libs.androidx.media3.datasource.cronet)
    implementation(libs.androidx.ui.graphics.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.bundles.dagger)
    ksp(libs.dagger.compiler)
    ksp(libs.dagger.android.processor)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.config)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.guava)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.paging)
    implementation(libs.applovin.sdk)
    implementation(libs.facebook.android.sdk)
    implementation(project(":samples"))
    implementation(project(":sharedLibrary"))
    implementation(project(":sharedPlayer"))
    implementation(project(":sharedResources"))
    implementation(project(":securedToken"))
    implementation(project(":coreApi"))
    implementation(project(":coreRadio"))
    implementation(project(":core"))
    implementation(project(":podcasts"))
    implementation(project(":featureOnboarding"))
    implementation(project(":featureRadio"))
    implementation(project(":hazeAndroid"))
    implementation(project(":sharedFirebase"))
    implementation(project(":ads"))
}

// Firebase App Distribution configuration
// We'll use different app IDs for debug and release builds
// Debug: 1:187551572250:android:e952fdb02a12421bd60ca3
// Release: 1:522103746332:android:561c9f81fd5a7cc501d4d9

// Create a custom property for the Firebase App ID
val debugFirebaseAppId = "1:187551572250:android:e952fdb02a12421bd60ca3"
val releaseFirebaseAppId = "1:522103746332:android:561c9f81fd5a7cc501d4d9"

// Firebase App Distribution is configured per build type

// Tasks for generating and distributing APK/AAB files
tasks.register("generateDebugApk") {
    dependsOn("assembleDebug")
    doLast {
        println("Debug APK generated at: ${layout.buildDirectory.get().asFile}/outputs/apk/debug/")
    }
}

tasks.register("generateReleaseApk") {
    dependsOn("assembleRelease")
    doLast {
        println("Release APK generated at: ${layout.buildDirectory.get().asFile}/outputs/apk/release/")
    }
}

tasks.register("generateReleaseBundle") {
    dependsOn("bundleRelease")
    doLast {
        println("Release AAB generated at: ${layout.buildDirectory.get().asFile}/outputs/bundle/release/")
    }
}

// Set up the app ID for debug builds
// Configure Firebase App Distribution for debug builds
android.buildTypes.getByName("debug") {
    firebaseAppDistribution {
        appId = debugFirebaseAppId
        releaseNotes = "Debug build with latest features for testing"
        groups = "podcasttester"
    }
}

// Set up the app ID for release builds
// Configure Firebase App Distribution for release builds
android.buildTypes.getByName("release") {
    firebaseAppDistribution {
        appId = releaseFirebaseAppId
        releaseNotes = "Release build with stable features"
        groups = "release-testers"
    }
}

// Debug distribution task
tasks.register("distributeDebugApk") {
    dependsOn("assembleDebug")
    doLast {
        println("Debug APK generated at: ${layout.buildDirectory.get().asFile}/outputs/apk/debug/")
        println("To distribute to Firebase App Distribution, run: ./gradlew appDistributionUploadDebug")
    }
}

// Release distribution task
tasks.register("distributeReleaseApk") {
    dependsOn("assembleRelease")
    doLast {
        println("Release APK generated at: ${layout.buildDirectory.get().asFile}/outputs/apk/release/")
        println("To distribute to Firebase App Distribution, run: ./gradlew appDistributionUploadRelease")
    }
}
