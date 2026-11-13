package tss.t.shared.ads

/**
 * Frequency / UX policy for ads, a port of Android's `ads/src/main/java/tss/t/ads/AdPolicy.kt`
 * (same rules, same block reasons, same tests in [AdPolicyTest]). Hard rules: never over audio,
 * never on the Player, never on a notification/deep-link launch, grace period after install,
 * first sessions skipped, gaps and caps from Remote Config.
 */
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
    /** Interstitial: 1-based count of natural breaks (leaving a show) this session. */
    val naturalBreakIndex: Int = 0,
    /** Interstitial: how long the user stayed on the screen being left, or null when unknown. */
    val screenDwellMs: Long? = null,
    /** Time since audio last played, or null when nothing played this session. */
    val sincePlaybackMs: Long? = null,
)

/** Persistent counters behind the caps. iOS: `UserDefaultsAdCounterStore` (main thread). */
interface AdCounterStore {
    /** First time the ad layer ran on this install (stored on the first call). */
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

/** Wall clock and the local UTC offset, so day caps roll over at local midnight. */
interface AdClock {
    fun nowMillis(): Long

    /** Offset of local time from UTC at [atMillis], in milliseconds. */
    fun utcOffsetMillis(atMillis: Long): Long
}

class AdPolicy(
    private val store: AdCounterStore,
    private val clock: AdClock,
) {
    fun day(now: Long): Long = floorDiv(now + clock.utcOffsetMillis(now), DAY_MS)

    fun canShowFullScreen(
        placement: AdPlacement,
        settings: AdsSettings,
        ctx: TriggerContext,
    ): Decision {
        val now = clock.nowMillis()
        commonBlock(placement, settings, ctx.canRequestAds, now)?.let { return Decision.Block(it) }

        if (now - store.installAt(now) < settings.gracePeriodHours * HOUR_MS) {
            return Decision.Block("grace")
        }
        if (!ctx.onboardingCompleted) return Decision.Block("onboarding")
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
                if (now - store.lastShownAt(placement) < settings.interstitialMinIntervalSec * SEC_MS) {
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

    /** Rewarded is opt-in: audio does not block it (the caller pauses playback first). */
    fun canShowRewarded(
        placement: AdPlacement,
        settings: AdsSettings,
        canRequestAds: Boolean,
    ): Decision {
        val now = clock.nowMillis()
        commonBlock(placement, settings, canRequestAds, now)?.let { return Decision.Block(it) }
        if (store.shownOnDay(placement, day(now)) >= settings.rewardedDailyCap) {
            return Decision.Block("daily_cap")
        }
        return Decision.Allow
    }

    /** Banner / native: only the switches, consent and an active ad-free reward. */
    fun canShowInline(placement: AdPlacement, settings: AdsSettings, canRequestAds: Boolean): Decision =
        commonBlock(placement, settings, canRequestAds, clock.nowMillis())
            ?.let { Decision.Block(it) } ?: Decision.Allow

    fun recordShown(placement: AdPlacement) {
        val now = clock.nowMillis()
        store.recordShown(placement, now, day(now))
    }

    /** Starts the ad-free window of `rewarded_ad_free`. */
    fun grantAdFree(settings: AdsSettings) {
        store.grantAdFreeUntil(clock.nowMillis() + settings.rewardedAdFreeMinutes * MIN_MS)
    }

    private fun commonBlock(
        placement: AdPlacement,
        settings: AdsSettings,
        canRequestAds: Boolean,
        now: Long,
    ): String? = when {
        !settings.adsEnabled -> "kill_switch"
        settings.placementsEnabled[placement] != true -> "placement_disabled"
        !canRequestAds -> "no_consent"
        placement.format != AdFormat.Rewarded && store.adFreeUntil > now -> "ad_free"
        else -> null
    }

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a xor b) < 0)) q - 1 else q
    }

    companion object {
        const val SEC_MS = 1_000L
        const val MIN_MS = 60 * SEC_MS
        const val HOUR_MS = 60 * MIN_MS
        const val DAY_MS = 24 * HOUR_MS
    }
}

/**
 * A native-ad row inside a list (Android `NativeSlots.kt`). [slotKey] is stable for a position
 * ("ad-0", "ad-1", …) so list identities stay stable and a loaded ad is reused on scroll back.
 */
data class AdSlotRow(val placement: AdPlacement, val slotKey: String) {
    val lazyKey: String get() = "${placement.key}/$slotKey"
}

object NativeSlots {
    /**
     * 0-based indices of the items an ad row follows: after item [AdsSettings.nativeFirstIndex]
     * (1-based), then every [AdsSettings.nativeInterval] items, at most
     * [AdsSettings.nativeMaxPerList] times, never after the last item. Empty when the placement
     * is off.
     */
    fun afterIndices(itemCount: Int, placement: AdPlacement, settings: AdsSettings): List<Int> {
        if (!settings.isEnabled(placement) || settings.nativeMaxPerList <= 0) return emptyList()
        val first = settings.nativeFirstIndex.coerceAtLeast(1)
        val interval = settings.nativeInterval.coerceAtLeast(1)
        val out = ArrayList<Int>()
        for (index in 0 until itemCount) {
            val position = index + 1
            val isSlot = position == first || (position > first && (position - first) % interval == 0)
            if (isSlot && out.size < settings.nativeMaxPerList && position < itemCount) out.add(index)
        }
        return out
    }

    /** [items] with [AdSlotRow]s inserted at [afterIndices], as Android's `interleaveNativeSlots`. */
    fun <T : Any> interleave(items: List<T>, placement: AdPlacement, settings: AdsSettings): List<Any> {
        val after = afterIndices(items.size, placement, settings)
        if (after.isEmpty()) return items
        val out = ArrayList<Any>(items.size + after.size)
        var adCount = 0
        items.forEachIndexed { index, item ->
            out.add(item)
            if (adCount < after.size && after[adCount] == index) {
                out.add(AdSlotRow(placement, "ad-$adCount"))
                adCount++
            }
        }
        return out
    }
}
