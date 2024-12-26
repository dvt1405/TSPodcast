import tss.t.build.TSBuilds

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "tss.t.sharedfirebase"
    compileSdk = TSBuilds.combineSdk

    defaultConfig {
        minSdk = TSBuilds.minSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.hilt.android)
    implementation(libs.androidx.security.crypto.ktx)
    kapt(libs.hilt.android.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.remote.config)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(project(":sharedLibrary"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}