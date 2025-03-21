import tss.t.build.TSBuilds

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serilization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kotlin.parcelize)
    id("kotlin-kapt")
    id("applovin-quality-service")
}

applovin {
    apiKey = "avJkJkTUhqmoZGJTcdqgylRVHN9sbc3ZSUbub3UL1fXR3fXOyqupneiTBSvfi8FT0iI060T6danq4i-z5ZaZJp"
}

android {
    namespace = "tss.t.podcast"
    compileSdk = TSBuilds.combineSdk

    defaultConfig {
        applicationId = "tss.t.podcast"
        minSdk = TSBuilds.minSdk
        targetSdk = TSBuilds.combineSdk
        versionCode = 10006
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
            defaultConfig.versionCode = 10011
            defaultConfig.versionName = "v1.4.${defaultConfig.versionCode}"
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
        sourceCompatibility = TSBuilds.javaVersion
        targetCompatibility = TSBuilds.javaVersion
    }
    kotlinOptions {
        jvmTarget = TSBuilds.jvmTarget
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