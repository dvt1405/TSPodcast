import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

/*
 * AdMob ids. Every value defaults to Google's public TEST ids, so a checkout
 * with no extra configuration only ever requests test ads.
 *
 * Real ids are supplied without code changes, either as Gradle properties
 * (~/.gradle/gradle.properties, -P on the command line) or as environment
 * variables ORG_GRADLE_PROJECT_<NAME> in CI:
 *
 *   ADMOB_APP_ID                 ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
 *   ADMOB_UNIT_APP_OPEN          ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
 *   ADMOB_UNIT_BANNER            (inline adaptive banner unit)
 *   ADMOB_UNIT_NATIVE
 *   ADMOB_UNIT_INTERSTITIAL
 *   ADMOB_UNIT_REWARDED
 *
 * Debug builds ignore them and always use the test ids: clicking or
 * repeatedly loading real ads from a dev device is invalid traffic and can get
 * the AdMob account suspended. Per-placement unit ids can additionally be
 * overridden at runtime through the Remote Config key `ads_unit_ids` (release
 * only). Test devices for debug builds: ADMOB_TEST_DEVICE_IDS and
 * UMP_TEST_DEVICE_HASHED_IDS (comma separated; the ids are printed to logcat on
 * first run by the Mobile Ads SDK and UMP respectively).
 */
object AdMobTestIds {
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}

fun prop(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseAppId = prop("ADMOB_APP_ID") ?: AdMobTestIds.APP_ID
if (releaseAppId == AdMobTestIds.APP_ID) {
    logger.info("ads-admob: ADMOB_APP_ID not set, release builds use Google's TEST AdMob ids.")
}

/*
 * Store builds must carry the real AdMob ids (Sprint 0, docs/in-progress/plans/admob-migration.md): bundleRelease (the
 * AAB uploaded to Google Play) fails while any release id is still Google's test id. Debug and
 * the release APKs used for internal testing are not affected. Override for a deliberate test
 * bundle with -PALLOW_TEST_ADS_IN_RELEASE=true.
 */
gradle.taskGraph.whenReady {
    val buildsStoreBundle = allTasks.any { it.path == ":app:bundleRelease" }
    if (!buildsStoreBundle || prop("ALLOW_TEST_ADS_IN_RELEASE") == "true") return@whenReady
    val missing = listOf(
        "ADMOB_APP_ID", "ADMOB_UNIT_APP_OPEN", "ADMOB_UNIT_BANNER", "ADMOB_UNIT_NATIVE", "ADMOB_UNIT_INTERSTITIAL",
    ).filter { prop(it) == null }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Release bundle without real AdMob ids (missing: ${missing.joinToString()}). " +
                "Set them as GitHub secrets / Gradle properties, or pass -PALLOW_TEST_ADS_IN_RELEASE=true for a test bundle."
        )
    }
}

android {
    namespace = "tss.t.ads.admob"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "ADMOB_TEST_DEVICE_IDS",
            quoted(prop("ADMOB_TEST_DEVICE_IDS").orEmpty())
        )
        buildConfigField(
            "String",
            "UMP_TEST_DEVICE_HASHED_IDS",
            quoted(prop("UMP_TEST_DEVICE_HASHED_IDS").orEmpty())
        )
        // Debug builds only: which region UMP pretends the device is in.
        // EEA (default, shows the GDPR form), US (regulated US state), OTHER, OFF.
        buildConfigField(
            "String",
            "UMP_DEBUG_GEOGRAPHY",
            quoted(prop("UMP_DEBUG_GEOGRAPHY") ?: "EEA")
        )
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = AdMobTestIds.APP_ID
            buildConfigField("String", "ADMOB_APP_ID", quoted(AdMobTestIds.APP_ID))
            buildConfigField("String", "ADMOB_UNIT_APP_OPEN", quoted(AdMobTestIds.APP_OPEN))
            buildConfigField("String", "ADMOB_UNIT_BANNER", quoted(AdMobTestIds.BANNER))
            buildConfigField("String", "ADMOB_UNIT_NATIVE", quoted(AdMobTestIds.NATIVE))
            buildConfigField("String", "ADMOB_UNIT_INTERSTITIAL", quoted(AdMobTestIds.INTERSTITIAL))
            buildConfigField("String", "ADMOB_UNIT_REWARDED", quoted(AdMobTestIds.REWARDED))
            buildConfigField("boolean", "ADMOB_USES_TEST_IDS", "true")
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = releaseAppId
            buildConfigField("String", "ADMOB_APP_ID", quoted(releaseAppId))
            buildConfigField(
                "String", "ADMOB_UNIT_APP_OPEN",
                quoted(prop("ADMOB_UNIT_APP_OPEN") ?: AdMobTestIds.APP_OPEN)
            )
            buildConfigField(
                "String", "ADMOB_UNIT_BANNER",
                quoted(prop("ADMOB_UNIT_BANNER") ?: AdMobTestIds.BANNER)
            )
            buildConfigField(
                "String", "ADMOB_UNIT_NATIVE",
                quoted(prop("ADMOB_UNIT_NATIVE") ?: AdMobTestIds.NATIVE)
            )
            buildConfigField(
                "String", "ADMOB_UNIT_INTERSTITIAL",
                quoted(prop("ADMOB_UNIT_INTERSTITIAL") ?: AdMobTestIds.INTERSTITIAL)
            )
            buildConfigField(
                "String", "ADMOB_UNIT_REWARDED",
                quoted(prop("ADMOB_UNIT_REWARDED") ?: AdMobTestIds.REWARDED)
            )
            buildConfigField(
                "boolean", "ADMOB_USES_TEST_IDS",
                (releaseAppId == AdMobTestIds.APP_ID).toString()
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The Next-Gen SDK contains every class the legacy SDK has. Anything that
// still pulls play-services-ads(-lite) transitively (mediation adapters, some
// Firebase artifacts) would produce duplicate classes, so Google's setup guide
// excludes both. The same exclusion is repeated in :app, which is where the
// final runtime classpath is resolved.
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

dependencies {
    implementation(project(":ads"))
    implementation(project(":sharedLibrary"))

    // `implementation`, not `api`: :app cannot see a single AdMob/UMP type.
    implementation(libs.google.ads.mobile.sdk)
    implementation(libs.google.ump)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
}
