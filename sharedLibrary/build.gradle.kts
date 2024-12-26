import tss.t.build.TSBuilds

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "tss.t.sharedlibrary"
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coil)
    implementation(libs.material)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)
    implementation(libs.retrofit.gson)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(project(":sharedResources"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}