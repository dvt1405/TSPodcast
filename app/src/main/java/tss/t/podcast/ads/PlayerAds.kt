package tss.t.podcast.ads

import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import tss.t.ads.AdPlacement
import tss.t.ads.AdPolicy
import tss.t.ads.AdRenderer
import tss.t.ads.AdsConfig
import tss.t.ads.AdsController
import tss.t.ads.AdsSettings
import tss.t.ads.AdsState
import tss.t.ads.Decision
import tss.t.ads.PlayerAdContext
import tss.t.ads.PlayerNativeSlot
import tss.t.ads.interleaveUpNextSlots
import tss.t.sharedplayer.player.PlayerManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Player's ads (docs/in-progress/plans/player-ads-and-premium.md §3): `player_now_playing`
 * (strip under the artwork), `player_up_next` (rows of the open Up next list) and `player_pause`
 * (card in place of the artwork after a manual pause). All three are off by default
 * (`ads_placements_enabled`) and follow [AdPolicy.canShowPlayerInline]: Premium, the kill
 * switch, consent, the ad-free reward, the grace period and first session, and where each may
 * appear. Never full screen, never audio, never next to the playback controls.
 */
@Singleton
class PlayerAds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AdsConfig,
    private val policy: AdPolicy,
    private val controller: AdsController,
    private val renderer: AdRenderer,
    private val playerManager: PlayerManager,
) {
    val nowPlaying: PlayerNativeSlot by lazy { renderer.playerSlot(AdPlacement.PlayerNowPlaying) }
    val pause: PlayerNativeSlot by lazy { renderer.playerSlot(AdPlacement.PlayerPause) }

    /** Elapsed-realtime millis of the user's own pause, null otherwise ([PlayerManager.manualPauseAt]). */
    val manualPauseAt: StateFlow<Long?> get() = playerManager.manualPauseAt

    fun settings(): AdsSettings = config.current()

    /** [ctx] with the current consent ([AdsState.Ready.canRequestAds]). */
    fun decide(placement: AdPlacement, ctx: PlayerAdContext): Decision {
        val consent = (controller.state.value as? AdsState.Ready)?.canRequestAds == true
        return policy.canShowPlayerInline(placement, config.current(), ctx.copy(canRequestAds = consent))
    }

    /** The Up next list with its ad rows ([interleaveUpNextSlots]); unchanged unless allowed. */
    fun <T : Any> upNextRows(items: List<T>, ctx: PlayerAdContext): List<Any> {
        val allowed = decide(AdPlacement.PlayerUpNext, ctx) is Decision.Allow
        return interleaveUpNextSlots(items, config.current(), allowed)
    }

    /** A pause card has appeared: counts towards `ads_player_pause_session_cap` and the 5 min gap. */
    fun recordPauseShown() = policy.recordShown(AdPlacement.PlayerPause)

    /**
     * Audio started: load the strip's ad in the background so it is ready when the Player opens
     * (an ad is only inserted at open or an episode change, never while the Player is looked at).
     */
    fun preloadNowPlaying() {
        val configuration = context.resources.configuration
        val ctx = PlayerAdContext(
            canRequestAds = true,
            windowHeightDp = configuration.screenHeightDp,
            isAccessibilityMode = isAccessibilityMode(configuration.fontScale),
        )
        if (decide(AdPlacement.PlayerNowPlaying, ctx) is Decision.Allow) nowPlaying.load()
    }

    /** A large font scale or a screen reader: no strip / pause card (they would push content). */
    fun isAccessibilityMode(fontScale: Float): Boolean {
        val a11y = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return fontScale >= ACCESSIBILITY_FONT_SCALE || a11y?.isTouchExplorationEnabled == true
    }

    private companion object {
        /** Android's "Largest" font size and above (iOS: accessibility sizes, AX1+). */
        const val ACCESSIBILITY_FONT_SCALE = 1.3f
    }
}
