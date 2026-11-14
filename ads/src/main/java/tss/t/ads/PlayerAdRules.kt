package tss.t.ads

/**
 * When the Player's ads appear, refresh and go (docs/in-progress/plans/player-ads-and-premium.md
 * §3.1 #3, §3.2, §3.5). Pure; iOS uses the same rules from shared `PlayerAdRules`
 * (`shared/src/commonMain/kotlin/tss/t/shared/ads/AdPolicy.kt`), tested by the same cases.
 */
object PlayerAdRules {
    /** Why the now-playing strip is evaluated. */
    enum class StripMoment {
        /** The Player has just opened. */
        Opened,

        /** Another episode became current while the Player is shown. */
        EpisodeChanged,

        /** Anything else: an ad finished loading, a setting or the Player state changed. */
        Updated,
    }

    /**
     * Whether the `player_now_playing` strip is on screen after [moment]. It appears only at
     * Player open or an episode change and only with an ad already loaded, so nothing moves
     * while the Player is looked at (an ad that loads late waits for the next time). It goes as
     * soon as it is no longer allowed (Premium, a playback error, ...).
     */
    fun stripVisible(moment: StripMoment, wasVisible: Boolean, allowed: Boolean, hasAd: Boolean): Boolean =
        when (moment) {
            StripMoment.Opened, StripMoment.EpisodeChanged -> allowed && hasAd
            StripMoment.Updated -> wasVisible && allowed && hasAd
        }

    /** Refresh period of the visible strip, or null when it changes only with the episode. */
    fun refreshIntervalMs(settings: AdsSettings): Long? =
        settings.playerNativeRefreshSec.takeIf { it > 0 }?.times(AdPolicy.SEC_MS)

    /**
     * How long to wait before replacing the visible strip's ad ([ageMs] old): 0 = now, null =
     * never (refresh off or no ad). The caller only counts while the strip is really visible
     * (Player in the foreground, Up next not covering it).
     */
    fun refreshDelayMs(ageMs: Long?, settings: AdsSettings): Long? {
        val interval = refreshIntervalMs(settings) ?: return null
        val age = ageMs ?: return null
        return (interval - age).coerceAtLeast(0)
    }

    /**
     * On an episode change, replace the strip's ad when it is at least one refresh period old,
     * or always when the strip only changes with the episode (`ads_player_native_refresh_sec=0`).
     */
    fun refreshOnEpisodeChange(ageMs: Long?, settings: AdsSettings): Boolean {
        val age = ageMs ?: return false
        val interval = refreshIntervalMs(settings) ?: return true
        return age >= interval
    }

    /**
     * How long the user's own pause (at [pausedAt]) has lasted while the Player was shown (since
     * [playerShownAt]), or null when not paused by the user. Same clock for the three values.
     */
    fun manualPauseMs(pausedAt: Long?, playerShownAt: Long, now: Long): Long? =
        pausedAt?.let { (now - maxOf(it, playerShownAt)).coerceAtLeast(0) }

    /** Time until the pause card is due: 0 when due, null when not paused by the user. */
    fun pauseCardDueInMs(manualPauseMs: Long?, settings: AdsSettings): Long? =
        manualPauseMs?.let { (settings.playerPauseDelaySec * AdPolicy.SEC_MS - it).coerceAtLeast(0) }
}
