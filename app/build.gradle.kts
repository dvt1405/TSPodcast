plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
    id("kotlinx-serialization")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("applovin-quality-service")
}

applovin {
    apiKey = "avJkJkTUhqmoZGJTcdqgylRVHN9sbc3ZSUbub3UL1fXR3fXOyqupneiTBSvfi8FT0iI060T6danq4i-z5ZaZJp"
}

android {
    namespace = "tss.t.podcast"
    compileSdk = 34

    defaultConfig {
        applicationId = "tss.t.podcast"
        minSdk = 23
        targetSdk = 34
        versionCode = 10005
        versionName = "v1.4.$versionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            setProperty("archivesBaseName", "TSPodcast.${defaultConfig.versionName}_D")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProperty("archivesBaseName", "TSPodcast.${defaultConfig.versionName}_R")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
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
    kapt(libs.androidx.room.room.compiler)
    annotationProcessor(libs.androidx.room.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.guava)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.paging)
    implementation(libs.applovin.sdk)
    implementation(project(":samples"))
    implementation(project(":sharedLibrary"))
    implementation(project(":sharedPlayer"))
    implementation(project(":sharedResources"))
    implementation(project(":securedToken"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":podcasts"))
    implementation(project(":featureOnboarding"))
    implementation(project(":hazeAndroid"))
    implementation(project(":sharedFirebase"))
    implementation(project(":ads"))
}