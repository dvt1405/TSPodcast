package tss.t.ads

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import tss.t.sharedlibrary.utils.ConfigAPI
import javax.inject.Inject
import javax.inject.Singleton

/** Remote Config keys. See docs/in-progress/plans/admob-migration.md §6. */
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

    /**
     * Log GA4 `ad_impression` (with revenue) from the paid event. Turn off if the AdMob ↔
     * Firebase link starts logging its own `ad_impression` for these ads, which would count
     * revenue twice (docs/done/analytics/analytics-events.md §Quảng cáo).
     */
    const val GA4_AD_IMPRESSION = "ads_ga4_ad_impression"
}

enum class NativeTemplate { Small, Medium }

/**
 * An immutable snapshot of every ads setting. Built from Remote Config on top
 * of the in-app defaults, so a missing key, an unparsable value or a device
 * that has never fetched Remote Config all fall back to [AdsSettings.defaults].
 */
data class AdsSettings(
    val adsEnabled: Boolean,
    val placementsEnabled: Map<AdPlacement, Boolean>,
    /** Per-placement unit id overrides from Remote Config (`ads_unit_ids`). */
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
    /** The user must have stayed this long on the screen they leave (no ad after a quick peek). */
    val interstitialMinDwellSec: Long = 20,
    /** No interstitial this soon after audio stopped: the user may be about to resume. */
    val interstitialAfterPlaybackSec: Long = 60,
    val nativeFirstIndex: Int,
    val nativeInterval: Int,
    val nativeMaxPerList: Int,
    val nativeTemplate: NativeTemplate,
    val rewardedAdFreeMinutes: Long,
    val rewardedDailyCap: Int,
    val minAppVersion: Long,
    /** [AdsKeys.GA4_AD_IMPRESSION]. */
    val ga4AdImpression: Boolean = true,
) {
    fun isEnabled(placement: AdPlacement): Boolean =
        adsEnabled && placementsEnabled[placement] == true

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
         * In-app defaults. Release (owner, 2026-11-01: real AdMob units from this release):
         * ads on, banner/native placements, App Open on return to the app and an interstitial
         * when leaving a show, all under AdPolicy (24 h grace, first sessions skipped, never
         * over audio or on the Player, intervals and caps). Remote Config `ads_enabled=false`
         * stays the kill switch. Debug: same with Google test ids, no grace / first-session
         * skip, and no interstitial so development is not interrupted. Rewarded is off.
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
        )

        /**
         * Applies Remote Config values over [defaults]. [read] returns the raw
         * string for a key, or null/blank when the key is not set (Firebase
         * returns "" for unknown keys, which must not be read as `false`/`0`).
         *
         * [allowUnitIdOverrides] is false in debug builds: a real unit id must
         * never be requested from a dev device.
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
                raw(key)?.toDoubleOrNull()?.toLong()?.takeIf { it >= 0 } ?: def

            fun int(key: String, def: Int) = long(key, def.toLong()).toInt()

            val placements = defaults.placementsEnabled.toMutableMap()
            raw(AdsKeys.PLACEMENTS_ENABLED)?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    obj.keys().forEach { key ->
                        val placement = AdPlacement.fromKey(key) ?: return@forEach
                        val value = obj.opt(key)
                        if (value is Boolean) placements[placement] = value
                    }
                }
            }

            val unitIds = mutableMapOf<AdPlacement, String>()
            if (allowUnitIdOverrides) raw(AdsKeys.UNIT_IDS)?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    obj.keys().forEach { key ->
                        val placement = AdPlacement.fromKey(key) ?: return@forEach
                        val id = obj.optString(key).trim()
                        if (AD_UNIT_ID.matches(id)) unitIds[placement] = id
                    }
                }
            }

            return AdsSettings(
                adsEnabled = bool(AdsKeys.ENABLED, defaults.adsEnabled),
                placementsEnabled = placements,
                unitIdOverrides = unitIds,
                gracePeriodHours = long(AdsKeys.GRACE_PERIOD_HOURS, defaults.gracePeriodHours),
                appOpenMinBackgroundSec = long(
                    AdsKeys.APP_OPEN_MIN_BACKGROUND_SEC, defaults.appOpenMinBackgroundSec
                ),
                appOpenMinIntervalMin = long(
                    AdsKeys.APP_OPEN_MIN_INTERVAL_MIN, defaults.appOpenMinIntervalMin
                ),
                appOpenDailyCap = int(AdsKeys.APP_OPEN_DAILY_CAP, defaults.appOpenDailyCap),
                appOpenSkipFirstSessions = int(
                    AdsKeys.APP_OPEN_SKIP_FIRST_SESSIONS, defaults.appOpenSkipFirstSessions
                ),
                minGapFullScreenSec = long(
                    AdsKeys.MIN_GAP_FULLSCREEN_SEC, defaults.minGapFullScreenSec
                ),
                interstitialEveryN = int(AdsKeys.INTERSTITIAL_EVERY_N, defaults.interstitialEveryN),
                interstitialMinIntervalSec = long(
                    AdsKeys.INTERSTITIAL_MIN_INTERVAL_SEC, defaults.interstitialMinIntervalSec
                ),
                interstitialSessionCap = int(
                    AdsKeys.INTERSTITIAL_SESSION_CAP, defaults.interstitialSessionCap
                ),
                interstitialDailyCap = int(
                    AdsKeys.INTERSTITIAL_DAILY_CAP, defaults.interstitialDailyCap
                ),
                interstitialMinDwellSec = long(
                    AdsKeys.INTERSTITIAL_MIN_DWELL_SEC, defaults.interstitialMinDwellSec
                ),
                interstitialAfterPlaybackSec = long(
                    AdsKeys.INTERSTITIAL_AFTER_PLAYBACK_SEC, defaults.interstitialAfterPlaybackSec
                ),
                nativeFirstIndex = int(AdsKeys.NATIVE_FIRST_INDEX, defaults.nativeFirstIndex)
                    .coerceAtLeast(1),
                nativeInterval = int(AdsKeys.NATIVE_INTERVAL, defaults.nativeInterval)
                    .coerceAtLeast(1),
                nativeMaxPerList = int(AdsKeys.NATIVE_MAX_PER_LIST, defaults.nativeMaxPerList),
                nativeTemplate = when (raw(AdsKeys.NATIVE_TEMPLATE)?.lowercase()) {
                    "small" -> NativeTemplate.Small
                    "medium" -> NativeTemplate.Medium
                    else -> defaults.nativeTemplate
                },
                rewardedAdFreeMinutes = long(
                    AdsKeys.REWARDED_AD_FREE_MINUTES, defaults.rewardedAdFreeMinutes
                ),
                rewardedDailyCap = int(AdsKeys.REWARDED_DAILY_CAP, defaults.rewardedDailyCap),
                minAppVersion = long(AdsKeys.MIN_APP_VERSION, defaults.minAppVersion),
                ga4AdImpression = bool(AdsKeys.GA4_AD_IMPRESSION, defaults.ga4AdImpression),
            )
        }

        /** `ca-app-pub-<16 digits>/<10 digits>`. Anything else is ignored. */
        private val AD_UNIT_ID = Regex("""ca-app-pub-\d{16}/\d{10}""")
    }
}

/** Read access to the current [AdsSettings]. */
interface AdsConfig {
    fun current(): AdsSettings
}

/**
 * [AdsConfig] backed by Firebase Remote Config through [ConfigAPI].
 *
 * Settings are re-read at most every [CACHE_MS] so composables can call
 * [current] freely; the snapshot is immutable, so it is safe to share across
 * the main thread and the SDK's background callback threads.
 */
@Singleton
class RemoteAdsConfig @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configAPI: ConfigAPI,
) : AdsConfig {
    private val defaults = AdsSettings.defaults(debug = BuildConfig.DEBUG)

    private val versionCode: Long by lazy {
        runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
        }.getOrDefault(Long.MAX_VALUE)
    }

    @Volatile
    private var cached: Pair<Long, AdsSettings>? = null

    override fun current(): AdsSettings {
        val now = System.currentTimeMillis()
        cached?.let { (at, settings) -> if (now - at < CACHE_MS) return settings }
        val settings = runCatching {
            AdsSettings.parse(defaults, allowUnitIdOverrides = !BuildConfig.DEBUG) { key ->
                configAPI.getString(key, null)
            }
        }.getOrDefault(defaults).let {
            // `ads_min_app_version`: switch ads off for old builds with a known ad bug.
            if (versionCode < it.minAppVersion) it.copy(adsEnabled = false) else it
        }
        cached = now to settings
        return settings
    }

    private companion object {
        const val CACHE_MS = 30_000L
    }
}
