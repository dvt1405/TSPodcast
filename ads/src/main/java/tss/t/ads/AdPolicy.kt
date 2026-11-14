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
 * Facts about the Player when one of its placements would show
 * (docs/in-progress/plans/player-ads-and-premium.md §3.5).
 */
data class PlayerAdContext(
    val canRequestAds: Boolean,
    /** Height of the window the Player is drawn in (dp / pt). */
    val windowHeightDp: Int,
    /** iPhone landscape (compact vertical size class). Android: false (the height rule covers it). */
    val isCompactHeight: Boolean = false,
    /** Accessibility text size (iOS AX1+, Android font scale ≥ 1.3) or a screen reader is on. */
    val isAccessibilityMode: Boolean = false,
    /** The Player shows its playback error. */
    val hasPlaybackError: Boolean = false,
    /** The Up next list is fully open (not the peek, not mid-drag). */
    val upNextExpanded: Boolean = false,
    val isLive: Boolean = false,
    /**
     * `player_pause`: how long the user's own pause has lasted while the Player was shown, or
     * null when not paused by the user (playing, buffering, sleep timer, focus loss, error).
     */
    val manualPauseMs: Long? = null,
    /** `player_pause`: the card is already up for this pause (cap and gap were checked then). */
    val pauseCardShowing: Boolean = false,
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
 * docs/in-progress/plans/admob-migration.md §2.1: never over audio, never on the Player,
 * never on a notification/deep-link launch, never twice in a row.
 */
class AdPolicy(
    private val store: AdCounterStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
    /** The user owns an ad-free product ([AdFreeEntitlement]): blocks every format. */
    private val isPremium: () -> Boolean = { false },
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

    /** Banner / native: Premium, the switches, consent and an active ad-free reward. */
    fun canShowInline(placement: AdPlacement, settings: AdsSettings, canRequestAds: Boolean): Decision {
        if (isPremium()) return Decision.Block(AdFreeEntitlement.REASON_PREMIUM)
        if (!settings.adsEnabled) return Decision.Block("kill_switch")
        if (settings.placementsEnabled[placement] != true) return Decision.Block("placement_disabled")
        if (!canRequestAds) return Decision.Block("no_consent")
        if (store.adFreeUntil > clock()) return Decision.Block("ad_free")
        return Decision.Allow
    }

    /**
     * The Player placements (`player_now_playing`, `player_up_next`, `player_pause`): the
     * [canShowInline] rules, then the grace period and the first session
     * (`ads_player_respect_grace`), and where each one may appear (§3.1, §3.5). "Never adjacent
     * to the controls" is a layout rule of the screens, not checked here.
     */
    fun canShowPlayerInline(
        placement: AdPlacement,
        settings: AdsSettings,
        ctx: PlayerAdContext,
    ): Decision {
        val base = canShowInline(placement, settings, ctx.canRequestAds)
        if (base !is Decision.Allow) return base
        if (!placement.isPlayer) return Decision.Block("not_player")
        val now = clock()
        if (settings.playerRespectGrace) {
            if (now - store.installAt(now) < settings.gracePeriodHours * HOUR_MS) {
                return Decision.Block("grace")
            }
            if (store.sessionCount <= 1) return Decision.Block("first_session")
        }
        if (placement == AdPlacement.PlayerUpNext) {
            return if (ctx.upNextExpanded) Decision.Allow else Decision.Block("up_next_collapsed")
        }
        // On the Player itself: room for the artwork area and nothing that pushes content.
        if (ctx.isCompactHeight) return Decision.Block("compact_height")
        if (ctx.windowHeightDp < settings.playerMinHeightDp) return Decision.Block("short_screen")
        if (ctx.isAccessibilityMode) return Decision.Block("accessibility")
        if (ctx.hasPlaybackError) return Decision.Block("player_error")
        if (placement == AdPlacement.PlayerPause) {
            if (ctx.isLive) return Decision.Block("live")
            val paused = ctx.manualPauseMs ?: return Decision.Block("not_paused")
            if (paused < settings.playerPauseDelaySec * SEC_MS) return Decision.Block("pause_delay")
            if (!ctx.pauseCardShowing) {
                if (store.shownThisSession(placement) >= settings.playerPauseSessionCap) {
                    return Decision.Block("session_cap")
                }
                val last = store.lastShownAt(placement)
                if (last > 0 && now - last < PLAYER_PAUSE_MIN_GAP_MS) {
                    return Decision.Block("interval")
                }
            }
        }
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
        isPremium() -> AdFreeEntitlement.REASON_PREMIUM
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

        /** Two pause cards are at least this far apart (fixed in code, §3.6). */
        const val PLAYER_PAUSE_MIN_GAP_MS = 5 * MIN_MS
    }
}
