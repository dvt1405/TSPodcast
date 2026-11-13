import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :billing is the in-app purchase SEAM, like :ads. It holds the interfaces, the
 * no-op default, the Remote Config reader (`iap_*`), the Premium cache and the
 * pure-Kotlin entitlement / offer rules. It must never depend on a store SDK:
 * the Google Play Billing implementation lives in :billing-play and is bound
 * through Hilt (see BillingSeamModule). Drop :billing-play from :app and the
 * paywall shows "unavailable" while a cached Premium keeps working.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "tss.t.billing"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // ConfigAPI (Remote Config abstraction) and AnalyticsTracker.
    implementation(project(":sharedLibrary"))

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
