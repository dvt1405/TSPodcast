package tss.t.shared.ads

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Ads settings for iOS, a port of Android's `ads/src/main/java/tss/t/ads/AdsConfig.kt`
 * (`AdsKeys`, `AdsSettings.defaults`, `AdsSettings.parse`). Android does not use `:shared`, so
 * the two copies are kept in step by hand; [AdsSettingsTemplateTest] checks this one against
 * `remoteconfig/ads-iap.params.json` (contract: docs/in-progress/plans/monetization-ads-iap.md).
 */
enum class AdFormat { AppOpen, Banner, CollapsibleBanner, Native, Interstitial, Rewarded }

/**
 * Every ad placement. [key] is the identifier used in Remote Config (`ads_placements_enabled`,
 * `ads_unit_ids`) and in analytics, the same as Android's `AdPlacement`.
 */
enum class AdPlacement(val key: String, val format: AdFormat) {
    AppOpenWarm("app_open_warm", AdFormat.AppOpen),
    AppOpenCold("app_open_cold", AdFormat.AppOpen),
    DiscoverInline("discover_inline", AdFormat.Native),
    DiscoverFavInline("discover_fav_inline", AdFormat.Banner),
    FavouriteTop("favourite_top", AdFormat.Banner),
    PlayerEpisodeList("player_episode_list", AdFormat.Banner),
    PodcastDetailHeader("podcast_detail_header", AdFormat.Banner),
    PodcastDetailEpisodes("podcast_detail_episodes", AdFormat.Native),
    SearchResults("search_results", AdFormat.Banner),
    DiscoverCollapsible("discover_collapsible", AdFormat.CollapsibleBanner),
    InterstitialNav("interstitial_nav", AdFormat.Interstitial),
    RewardedAdFree("rewarded_ad_free", AdFormat.Rewarded);

    val isFullScreen: Boolean
        get() = format == AdFormat.AppOpen || format == AdFormat.Interstitial ||
            format == AdFormat.Rewarded

    companion object {
        fun fromKey(key: String): AdPlacement? = entries.firstOrNull { it.key == key }
    }
}

/** Remote Config keys, the names of Android's `AdsKeys`. */
object AdsKeys {
    const val ENABLED = "ads_enabled"
    const val UNIT_IDS = "ads_unit_ids"
    const val PLACEMENTS_ENABLED = "ads_placements_enabled"
    const val GRACE_PERIOD_HOURS = "ads_grace_period_hours"
    const val APP_OPEN_MIN_BACKGROUND_SEC = "ads_app_open_min_background_sec"
    const val APP_OPEN_MIN_INTERVAL_MIN = "ads_app_open_min_interval_min"
    const val APP_OPEN_DAILY_CAP = "ads_app_open_daily_cap"
    const val APP_OPEN_SKIP_FIRST_SESSIONS = "ads_app_open_skip_first_sessions"
    const val MIN_GAP_FULLSCREEN_SEC = "ads_min_gap_fullscreen_sec"
    const val INTERSTITIAL_EVERY_N = "ads_interstitial_every_n"
    const val INTERSTITIAL_MIN_INTERVAL_SEC = "ads_interstitial_min_interval_sec"
    const val INTERSTITIAL_SESSION_CAP = "ads_interstitial_session_cap"
    const val INTERSTITIAL_DAILY_CAP = "ads_interstitial_daily_cap"
    const val INTERSTITIAL_MIN_DWELL_SEC = "ads_interstitial_min_dwell_sec"
    const val INTERSTITIAL_AFTER_PLAYBACK_SEC = "ads_interstitial_after_playback_sec"
    const val NATIVE_FIRST_INDEX = "ads_native_first_index"
    const val NATIVE_INTERVAL = "ads_native_interval"
    const val NATIVE_MAX_PER_LIST = "ads_native_max_per_list"
    const val NATIVE_TEMPLATE = "ads_native_template"
    const val REWARDED_AD_FREE_MINUTES = "ads_rewarded_ad_free_minutes"
    const val REWARDED_DAILY_CAP = "ads_rewarded_daily_cap"
    const val MIN_APP_VERSION = "ads_min_app_version"

    /** Log GA4 `ad_impression` (with revenue) from the paid event. */
    const val GA4_AD_IMPRESSION = "ads_ga4_ad_impression"

    /** Every key above, for the template parity test. */
    val ALL: List<String> = listOf(
        ENABLED, UNIT_IDS, PLACEMENTS_ENABLED, GRACE_PERIOD_HOURS, APP_OPEN_MIN_BACKGROUND_SEC,
        APP_OPEN_MIN_INTERVAL_MIN, APP_OPEN_DAILY_CAP, APP_OPEN_SKIP_FIRST_SESSIONS,
        MIN_GAP_FULLSCREEN_SEC, INTERSTITIAL_EVERY_N, INTERSTITIAL_MIN_INTERVAL_SEC,
        INTERSTITIAL_SESSION_CAP, INTERSTITIAL_DAILY_CAP, INTERSTITIAL_MIN_DWELL_SEC,
        INTERSTITIAL_AFTER_PLAYBACK_SEC, NATIVE_FIRST_INDEX, NATIVE_INTERVAL, NATIVE_MAX_PER_LIST,
        NATIVE_TEMPLATE, REWARDED_AD_FREE_MINUTES, REWARDED_DAILY_CAP, MIN_APP_VERSION,
        GA4_AD_IMPRESSION,
    )
}

enum class NativeTemplate { Small, Medium }

/**
 * An immutable snapshot of every ads setting: Remote Config over the in-app defaults, so a
 * missing key, an unparsable value or a device that never fetched all fall back to [defaults].
 */
data class AdsSettings(
    val adsEnabled: Boolean,
    val placementsEnabled: Map<AdPlacement, Boolean>,
    /** Per-placement unit id overrides from Remote Config (`ads_unit_ids`, release only). */
    val unitIdOverrides: Map<AdPlacement, String>,
    val gracePeriodHours: Long,
    val appOpenMinBackgroundSec: Long,
    val appOpenMinIntervalMin: Long,
    val appOpenDailyCap: Int,
    val appOpenSkipFirstSessions: Int,
    val minGapFullScreenSec: Long,
    val interstitialEveryN: Int,
    val interstitialMinIntervalSec: Long,
    val interstitialSessionCap: Int,
    val interstitialDailyCap: Int,
    /** The user must have stayed this long on the screen they leave. */
    val interstitialMinDwellSec: Long,
    /** No interstitial this soon after audio stopped. */
    val interstitialAfterPlaybackSec: Long,
    val nativeFirstIndex: Int,
    val nativeInterval: Int,
    val nativeMaxPerList: Int,
    val nativeTemplate: NativeTemplate,
    val rewardedAdFreeMinutes: Long,
    val rewardedDailyCap: Int,
    val minAppVersion: Long,
    val ga4AdImpression: Boolean,
) {
    fun isEnabled(placement: AdPlacement): Boolean =
        adsEnabled && placementsEnabled[placement] == true

    /**
     * `ads_min_app_version`: ads off on a build older than the value (iOS compares
     * `CFBundleVersion`, Android `versionCode`).
     */
    fun forBuild(buildNumber: Long): AdsSettings =
        if (buildNumber < minAppVersion) copy(adsEnabled = false) else this

    companion object {
        private val releasePlacements = mapOf(
            AdPlacement.FavouriteTop to true,
            AdPlacement.PodcastDetailHeader to true,
            AdPlacement.PodcastDetailEpisodes to true,
            AdPlacement.DiscoverInline to true,
            AdPlacement.SearchResults to true,
            AdPlacement.DiscoverFavInline to false,
            AdPlacement.PlayerEpisodeList to false,
            AdPlacement.DiscoverCollapsible to false,
            AdPlacement.InterstitialNav to true,
            AdPlacement.RewardedAdFree to false,
            AdPlacement.AppOpenWarm to true,
            AdPlacement.AppOpenCold to false,
        )

        /**
         * In-app defaults = the Remote Config template (release). Debug, like Android: no grace,
         * no first-session skip and no interstitial so development is not interrupted.
         */
        fun defaults(debug: Boolean) = AdsSettings(
            adsEnabled = true,
            placementsEnabled = if (debug) {
                releasePlacements + (AdPlacement.InterstitialNav to false)
            } else {
                releasePlacements
            },
            unitIdOverrides = emptyMap(),
            gracePeriodHours = if (debug) 0 else 24,
            appOpenMinBackgroundSec = 30,
            appOpenMinIntervalMin = 60,
            appOpenDailyCap = 3,
            appOpenSkipFirstSessions = if (debug) 0 else 2,
            minGapFullScreenSec = 90,
            interstitialEveryN = 3,
            interstitialMinIntervalSec = 240,
            interstitialSessionCap = 2,
            interstitialDailyCap = 4,
            interstitialMinDwellSec = 20,
            interstitialAfterPlaybackSec = 60,
            nativeFirstIndex = 3,
            nativeInterval = 8,
            nativeMaxPerList = 3,
            nativeTemplate = NativeTemplate.Small,
            rewardedAdFreeMinutes = 120,
            rewardedDailyCap = 3,
            minAppVersion = 0,
            ga4AdImpression = true,
        )

        /**
         * Applies Remote Config values over [defaults]. [read] returns the raw string for a key,
         * or null/blank when it is not set (a blank value must not read as `false`/`0`).
         * [allowUnitIdOverrides] is false in debug builds: only Google test units there.
         */
        fun parse(
            defaults: AdsSettings,
            allowUnitIdOverrides: Boolean,
            read: (String) -> String?,
        ): AdsSettings {
            fun raw(key: String) = read(key)?.trim()?.takeIf { it.isNotEmpty() }
            fun bool(key: String, def: Boolean) = when (raw(key)?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> def
            }

            fun long(key: String, def: Long) =
                raw(key)?.toDoubleOrNull()?.takeIf { !it.isNaN() }?.toLong()?.takeIf { it >= 0 } ?: def

            fun int(key: String, def: Int) =
                long(key, def.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            val placements = defaults.placementsEnabled.toMutableMap()
            raw(AdsKeys.PLACEMENTS_ENABLED)?.let(::jsonObject)?.forEach { (key, value) ->
                val placement = AdPlacement.fromKey(key) ?: return@forEach
                val flag = (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
                if (flag != null) placements[placement] = flag
            }

            val unitIds = mutableMapOf<AdPlacement, String>()
            if (allowUnitIdOverrides) {
                raw(AdsKeys.UNIT_IDS)?.let(::jsonObject)?.forEach { (key, value) ->
                    val placement = AdPlacement.fromKey(key) ?: return@forEach
                    val id = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                    if (id != null && AD_UNIT_ID.matches(id)) unitIds[placement] = id
                }
            }

            return AdsSettings(
                adsEnabled = bool(AdsKeys.ENABLED, defaults.adsEnabled),
                placementsEnabled = placements,
                unitIdOverrides = unitIds,
                gracePeriodHours = long(AdsKeys.GRACE_PERIOD_HOURS, defaults.gracePeriodHours),
                appOpenMinBackgroundSec = long(AdsKeys.APP_OPEN_MIN_BACKGROUND_SEC, defaults.appOpenMinBackgroundSec),
                appOpenMinIntervalMin = long(AdsKeys.APP_OPEN_MIN_INTERVAL_MIN, defaults.appOpenMinIntervalMin),
                appOpenDailyCap = int(AdsKeys.APP_OPEN_DAILY_CAP, defaults.appOpenDailyCap),
                appOpenSkipFirstSessions = int(AdsKeys.APP_OPEN_SKIP_FIRST_SESSIONS, defaults.appOpenSkipFirstSessions),
                minGapFullScreenSec = long(AdsKeys.MIN_GAP_FULLSCREEN_SEC, defaults.minGapFullScreenSec),
                interstitialEveryN = int(AdsKeys.INTERSTITIAL_EVERY_N, defaults.interstitialEveryN),
                interstitialMinIntervalSec = long(AdsKeys.INTERSTITIAL_MIN_INTERVAL_SEC, defaults.interstitialMinIntervalSec),
                interstitialSessionCap = int(AdsKeys.INTERSTITIAL_SESSION_CAP, defaults.interstitialSessionCap),
                interstitialDailyCap = int(AdsKeys.INTERSTITIAL_DAILY_CAP, defaults.interstitialDailyCap),
                interstitialMinDwellSec = long(AdsKeys.INTERSTITIAL_MIN_DWELL_SEC, defaults.interstitialMinDwellSec),
                interstitialAfterPlaybackSec = long(
                    AdsKeys.INTERSTITIAL_AFTER_PLAYBACK_SEC, defaults.interstitialAfterPlaybackSec
                ),
                nativeFirstIndex = int(AdsKeys.NATIVE_FIRST_INDEX, defaults.nativeFirstIndex).coerceAtLeast(1),
                nativeInterval = int(AdsKeys.NATIVE_INTERVAL, defaults.nativeInterval).coerceAtLeast(1),
                nativeMaxPerList = int(AdsKeys.NATIVE_MAX_PER_LIST, defaults.nativeMaxPerList),
                nativeTemplate = when (raw(AdsKeys.NATIVE_TEMPLATE)?.lowercase()) {
                    "small" -> NativeTemplate.Small
                    "medium" -> NativeTemplate.Medium
                    else -> defaults.nativeTemplate
                },
                rewardedAdFreeMinutes = long(AdsKeys.REWARDED_AD_FREE_MINUTES, defaults.rewardedAdFreeMinutes),
                rewardedDailyCap = int(AdsKeys.REWARDED_DAILY_CAP, defaults.rewardedDailyCap),
                minAppVersion = long(AdsKeys.MIN_APP_VERSION, defaults.minAppVersion),
                ga4AdImpression = bool(AdsKeys.GA4_AD_IMPRESSION, defaults.ga4AdImpression),
            )
        }

        /** `ca-app-pub-<16 digits>/<10 digits>`. Anything else is ignored. */
        private val AD_UNIT_ID = Regex("""ca-app-pub-\d{16}/\d{10}""")

        private fun jsonObject(text: String): JsonObject? =
            runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
    }
}

/**
 * iOS ad unit ids by format (contract §4). Debug builds always use Google's iOS test units and
 * ignore Remote Config; release uses the AdMob app `ca-app-pub-8696388065064767~2921388712`
 * units, overridable per placement by `ads_unit_ids`.
 */
object IosAdUnitIds {
    val release: Map<AdFormat, String> = mapOf(
        AdFormat.AppOpen to "ca-app-pub-8696388065064767/9264415720",
        AdFormat.Banner to "ca-app-pub-8696388065064767/5141512895",
        AdFormat.CollapsibleBanner to "ca-app-pub-8696388065064767/5141512895",
        AdFormat.Native to "ca-app-pub-8696388065064767/2538245333",
        AdFormat.Interstitial to "ca-app-pub-8696388065064767/4518696367",
        AdFormat.Rewarded to "ca-app-pub-8696388065064767/8512626743",
    )

    /** Rewarded interstitial (no placement yet, contract §4). */
    const val RELEASE_REWARDED_INTERSTITIAL = "ca-app-pub-8696388065064767/2729817023"

    /** Google's iOS demo units (developers.google.com/admob/ios/test-ads). */
    val test: Map<AdFormat, String> = mapOf(
        AdFormat.AppOpen to "ca-app-pub-3940256099942544/5575463023",
        AdFormat.Banner to "ca-app-pub-3940256099942544/2435281174",
        AdFormat.CollapsibleBanner to "ca-app-pub-3940256099942544/2435281174",
        AdFormat.Native to "ca-app-pub-3940256099942544/3986624511",
        AdFormat.Interstitial to "ca-app-pub-3940256099942544/4411468910",
        AdFormat.Rewarded to "ca-app-pub-3940256099942544/1712485313",
    )

    fun forPlacement(placement: AdPlacement, settings: AdsSettings, debug: Boolean): String =
        if (debug) {
            test.getValue(placement.format)
        } else {
            settings.unitIdOverrides[placement] ?: release.getValue(placement.format)
        }
}
