package tss.t.podcast.ui.screens.player.widgets

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import tss.t.ads.AdPlacement
import tss.t.ads.Decision
import tss.t.ads.LocalAdsController
import tss.t.ads.PlayerAdContext
import tss.t.ads.PlayerAdRules
import tss.t.podcast.ads.PlayerAds

/** What the Player shows of its ads this frame (player-ads-and-premium.md §3.3, §3.5). */
@Immutable
data class PlayerAdUi(
    /** `player_now_playing` strip under the (smaller) artwork. */
    val strip: Boolean = false,
    /** `player_pause` card in place of the artwork (and the strip). */
    val pauseCard: Boolean = false,
    /** The Player facts for the Up next rows; the list adds `upNextExpanded`. */
    val context: PlayerAdContext = PlayerAdContext(canRequestAds = false, windowHeightDp = 0),
)

/** Committed between compositions: the strip only appears at open or an episode change. */
private class StripMemory {
    var opened = false
    var mediaId: String? = null
    var visible = false
}

/**
 * The Player's ad state. The strip is decided at Player open and at each episode change (with
 * an ad already loaded, so the controls never move), refreshed in place every
 * `ads_player_native_refresh_sec` while really visible, and dropped as soon as it is not allowed.
 * The pause card follows a manual pause of `ads_player_pause_delay_sec` while the Player is shown,
 * goes the moment audio plays again, and uses a new ad each time.
 */
@Composable
fun rememberPlayerAdUi(
    ads: PlayerAds?,
    mediaId: String?,
    upNextExpanded: Boolean,
    hasError: Boolean,
    isLive: Boolean,
): PlayerAdUi {
    if (ads == null) return PlayerAdUi()
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    // Recompose when consent / the SDK state changes.
    val adsState by LocalAdsController.current.state.collectAsState()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val settings = remember(adsState, mediaId, foreground) { ads.settings() }
    val base = PlayerAdContext(
        canRequestAds = true,
        windowHeightDp = configuration.screenHeightDp,
        isAccessibilityMode = remember(fontScale, adsState) { ads.isAccessibilityMode(fontScale) },
        hasPlaybackError = hasError,
        isLive = isLive,
    )

    // Strip.
    val strip = ads.nowPlaying
    val stripHasAd by strip.hasAd.collectAsState()
    val stripAllowed = ads.decide(AdPlacement.PlayerNowPlaying, base) is Decision.Allow
    val memory = remember { StripMemory() }
    val moment = when {
        !memory.opened -> PlayerAdRules.StripMoment.Opened
        memory.mediaId != mediaId -> PlayerAdRules.StripMoment.EpisodeChanged
        else -> PlayerAdRules.StripMoment.Updated
    }
    val stripVisible = PlayerAdRules.stripVisible(moment, memory.visible, stripAllowed, stripHasAd)
    SideEffect {
        memory.opened = true
        memory.mediaId = mediaId
        memory.visible = stripVisible
    }
    LaunchedEffect(mediaId, stripAllowed) {
        if (!stripAllowed) return@LaunchedEffect
        // Ready for the next episode or the next opening; an old one is replaced on a new episode.
        if (moment == PlayerAdRules.StripMoment.EpisodeChanged &&
            PlayerAdRules.refreshOnEpisodeChange(strip.ageMs(), settings)
        ) {
            strip.reload()
        } else {
            strip.load()
        }
    }
    // Refresh only while the strip is really seen: foreground, Up next not covering it.
    val stripSeen = stripVisible && foreground && !upNextExpanded
    LaunchedEffect(stripSeen, mediaId) {
        if (!stripSeen) return@LaunchedEffect
        while (true) {
            val current = ads.settings()
            val wait = PlayerAdRules.refreshDelayMs(strip.ageMs(), current) ?: break
            if (wait > 0) {
                delay(wait)
                continue
            }
            strip.reload()
            // The new ad arrives after the request: look again one period later.
            delay(PlayerAdRules.refreshIntervalMs(current) ?: break)
        }
    }

    // Pause card.
    val manualPauseAt by ads.manualPauseAt.collectAsState()
    val pause = ads.pause
    val pauseHasAd by pause.hasAd.collectAsState()
    val shownSince = remember { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var cardActive by remember { mutableStateOf(false) }
    var cardCounted by remember { mutableStateOf(false) }
    LaunchedEffect(manualPauseAt) {
        now = SystemClock.elapsedRealtime()
        val pausedAt = manualPauseAt
        if (pausedAt == null) {
            // Played again (any way): the card goes now, and the next pause gets a new ad.
            if (cardCounted) pause.discard()
            cardActive = false
            cardCounted = false
            return@LaunchedEffect
        }
        val delayMs = settings.playerPauseDelaySec * 1_000
        val preload = base.copy(manualPauseMs = delayMs)
        if (ads.decide(AdPlacement.PlayerPause, preload) is Decision.Allow) pause.load()
        val due = PlayerAdRules.pauseCardDueInMs(
            PlayerAdRules.manualPauseMs(pausedAt, shownSince, now), settings
        ) ?: return@LaunchedEffect
        delay(due)
        now = SystemClock.elapsedRealtime()
    }
    val pauseMs = PlayerAdRules.manualPauseMs(manualPauseAt, shownSince, now)
    val pauseAllowed = foreground && ads.decide(
        AdPlacement.PlayerPause,
        base.copy(manualPauseMs = pauseMs, pauseCardShowing = cardActive),
    ) is Decision.Allow
    LaunchedEffect(pauseAllowed) {
        if (pauseAllowed && manualPauseAt != null) cardActive = true
    }
    val pauseCard = cardActive && pauseAllowed && pauseHasAd && manualPauseAt != null
    LaunchedEffect(pauseCard) {
        if (pauseCard && !cardCounted) {
            cardCounted = true
            ads.recordPauseShown()
        }
    }

    return PlayerAdUi(
        strip = stripVisible && !pauseCard,
        pauseCard = pauseCard,
        context = base,
    )
}
