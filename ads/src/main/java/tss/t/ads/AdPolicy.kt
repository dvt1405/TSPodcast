package tss.t.ads

import java.util.TimeZone

/** Outcome of a policy check. [Block.reason] is logged as `ads_blocked{reason}`. */
sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: String) : Decision
}

/** Facts about the moment a full-screen ad would be shown. */
data class TriggerContext(
    val canRequestAds: Boolean,
    val playbackActive: Boolean,
    val onPlayerScreen: Boolean,
    val externalLaunch: Boolean,
    val onboardingCompleted: Boolean,
    /** How long the app was in the background before this foreground, or null. */
    val backgroundDurationMs: Long? = null,
    /** Interstitial: 1-based count of natural breaks (e.g. leaving a show) this session. */
    val naturalBreakIndex: Int = 0,
    /** Interstitial: how long the user stayed on the screen being left, or null when unknown. */
    val screenDwellMs: Long? = null,
    /** Time since audio last played, or null when nothing played this session. */
    val sincePlaybackMs: Long? = null,
)

/**
 * Persistent counters behind the frequency caps. Implementations must be
 * thread-safe: the Mobile Ads SDK calls back on background threads.
 */
interface AdCounterStore {
    /** First time the ad layer ran on this install. */
    fun installAt(now: Long): Long
    val sessionCount: Int
    val lastFullScreenAt: Long
    val adFreeUntil: Long
    fun lastShownAt(placement: AdPlacement): Long
    fun shownOnDay(placement: AdPlacement, day: Long): Int
    fun shownThisSession(placement: AdPlacement): Int
    fun recordShown(placement: AdPlacement, now: Long, day: Long)
    fun startSession()
    fun grantAdFreeUntil(until: Long)
}

/**
 * Pure frequency / UX policy for full-screen formats. No Android, no SDK: every
 * rule here is unit tested (AdPolicyTest). The hard rules come from
 * docs/plans/admob-migration.md §2.1: never over audio, never on the Player,
 * never on a notification/deep-link launch, never twice in a row.
 */
class AdPolicy(
    private val store: AdCounterStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
) {
    fun day(now: Long): Long = Math.floorDiv(now + timeZone().getOffset(now), DAY_MS)

    fun canShowFullScreen(
        placement: AdPlacement,
        settings: AdsSettings,
        ctx: TriggerContext,
    ): Decision {
        val now = clock()
        val common = commonBlock(placement, settings, ctx, now)
        if (common != null) return Decision.Block(common)

        if (now - store.installAt(now) < settings.gracePeriodHours * HOUR_MS) {
            return Decision.Block("grace")
        }
        if (!ctx.onboardingCompleted) return Decision.Block("onboarding")
        // Audio-first: the rules a podcast app cannot break.
        if (ctx.playbackActive) return Decision.Block("audio_playing")
        if (ctx.externalLaunch) return Decision.Block("external_launch")
        if (ctx.onPlayerScreen) return Decision.Block("player_screen")

        if (now - store.lastFullScreenAt < settings.minGapFullScreenSec * SEC_MS) {
            return Decision.Block("gap")
        }
        val today = day(now)
        when (placement.format) {
            AdFormat.AppOpen -> {
                val bg = ctx.backgroundDurationMs
                if (bg != null && bg < settings.appOpenMinBackgroundSec * SEC_MS) {
                    return Decision.Block("short_background")
                }
                if (store.sessionCount <= settings.appOpenSkipFirstSessions) {
                    return Decision.Block("early_session")
                }
                if (now - store.lastShownAt(placement) < settings.appOpenMinIntervalMin * MIN_MS) {
                    return Decision.Block("interval")
                }
                if (store.shownOnDay(placement, today) >= settings.appOpenDailyCap) {
                    return Decision.Block("daily_cap")
                }
            }

            AdFormat.Interstitial -> {
                // Interstitials only at natural breaks the user chose (leaving a show), never
                // at launch or exit, never over or right after audio, at most every Nth break.
                if (store.sessionCount <= 1) return Decision.Block("first_session")
                val everyN = settings.interstitialEveryN.coerceAtLeast(1)
                if (ctx.naturalBreakIndex <= 0 || ctx.naturalBreakIndex % everyN != 0) {
                    return Decision.Block("every_n")
                }
                val dwell = ctx.screenDwellMs
                if (dwell != null && dwell < settings.interstitialMinDwellSec * SEC_MS) {
                    return Decision.Block("short_visit")
                }
                val sincePlayback = ctx.sincePlaybackMs
                if (sincePlayback != null && sincePlayback < settings.interstitialAfterPlaybackSec * SEC_MS) {
                    return Decision.Block("recent_playback")
                }
                if (now - store.lastShownAt(placement) <
                    settings.interstitialMinIntervalSec * SEC_MS
                ) {
                    return Decision.Block("interval")
                }
                if (store.shownThisSession(placement) >= settings.interstitialSessionCap) {
                    return Decision.Block("session_cap")
                }
                if (store.shownOnDay(placement, today) >= settings.interstitialDailyCap) {
                    return Decision.Block("daily_cap")
                }
            }

            else -> return Decision.Block("not_full_screen")
        }
        return Decision.Allow
    }

    /**
     * Rewarded ads are opt-in (the user taps a button), so the audio rule does
     * not block them: the caller pauses playback before showing instead.
     */
    fun canShowRewarded(
        placement: AdPlacement,
        settings: AdsSettings,
        ctx: TriggerContext,
    ): Decision {
        val now = clock()
        commonBlock(placement, settings, ctx, now)?.let { return Decision.Block(it) }
        if (store.shownOnDay(placement, day(now)) >= settings.rewardedDailyCap) {
            return Decision.Block("daily_cap")
        }
        return Decision.Allow
    }

    /** Banner / native: only the switches, consent and an active ad-free reward. */
    fun canShowInline(placement: AdPlacement, settings: AdsSettings, canRequestAds: Boolean): Decision {
        if (!settings.adsEnabled) return Decision.Block("kill_switch")
        if (settings.placementsEnabled[placement] != true) return Decision.Block("placement_disabled")
        if (!canRequestAds) return Decision.Block("no_consent")
        if (store.adFreeUntil > clock()) return Decision.Block("ad_free")
        return Decision.Allow
    }

    fun recordShown(placement: AdPlacement) {
        val now = clock()
        store.recordShown(placement, now, day(now))
    }

    private fun commonBlock(
        placement: AdPlacement,
        settings: AdsSettings,
        ctx: TriggerContext,
        now: Long,
    ): String? = when {
        !settings.adsEnabled -> "kill_switch"
        settings.placementsEnabled[placement] != true -> "placement_disabled"
        !ctx.canRequestAds -> "no_consent"
        placement.format != AdFormat.Rewarded && store.adFreeUntil > now -> "ad_free"
        else -> null
    }

    companion object {
        const val SEC_MS = 1_000L
        const val MIN_MS = 60 * SEC_MS
        const val HOUR_MS = 60 * MIN_MS
        const val DAY_MS = 24 * HOUR_MS
    }
}
