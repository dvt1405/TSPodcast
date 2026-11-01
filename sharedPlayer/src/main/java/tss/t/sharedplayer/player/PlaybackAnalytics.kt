package tss.t.sharedplayer.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import tss.t.core.repository.MediaType
import tss.t.sharedlibrary.analytics.Analytics
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import tss.t.sharedlibrary.analytics.AppEvents
import tss.t.sharedlibrary.analytics.EventThrottle
import tss.t.sharedplayer.utils.ext.mediaType
import kotlin.math.abs

/**
 * Playback events from the session's ExoPlayer (registered in [PlayerManager.buildPlayer]),
 * so the Player screen, the mini player, the notification, Android Auto and shortcuts are all
 * counted the same way: `play_start`, `playback_pause` / `playback_resume` (user only),
 * `playback_skip` (±, next / previous), `playback_seek` (scrub), `playback_progress`
 * (25 / 50 / 75 %), `playback_complete` and `playback_error`.
 *
 * Main thread only, like every Player.Listener of ExoPlayer.
 */
internal class PlaybackAnalytics(
    private val tracker: AnalyticsTracker = Analytics,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Player.Listener {

    private var player: Player? = null

    /** Media id whose `play_start` was sent; reset when another item becomes current. */
    private var startedId: String? = null
    private var milestonesSent = mutableSetOf<Int>()
    private var completedId: String? = null

    /** Last item transition, to tell the automatic resume seek from a user's scrub. */
    private var transitionAt = 0L
    private val errorThrottle = EventThrottle(perKey = 1, total = 20)

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
    }

    // ----- Start / pause / resume ------------------------------------------------------------

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        if (isPlaying) {
            if (startedId != item.mediaId) {
                startedId = item.mediaId
                tracker.log(
                    AppEvents.playStart(identity(p, item), p.currentPosition / 1000, tracker.currentScreen)
                )
            }
            scheduleProgress()
        } else {
            handler.removeCallbacks(progressTick)
            checkProgress()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> {
                // The first play of an item is play_start (sent once it really plays).
                if (startedId != item.mediaId) return
                val position = p.currentPosition / 1000
                tracker.log(
                    if (playWhenReady) {
                        AppEvents.playbackResume(identity(p, item), position)
                    } else {
                        AppEvents.playbackPause(identity(p, item), position, percent(p))
                    }
                )
            }

            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> complete(p, item)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        val p = player ?: return
        p.currentMediaItem?.let { complete(p, it) }
    }

    private fun complete(p: Player, item: MediaItem) {
        if (completedId == item.mediaId || p.isCurrentMediaItemLive) return
        completedId = item.mediaId
        tracker.log(AppEvents.playbackComplete(identity(p, item), durationSec(p) ?: 0))
    }

    // ----- Queue -----------------------------------------------------------------------------

    private var lastIndex = C.INDEX_UNSET

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val p = player ?: return
        transitionAt = android.os.SystemClock.elapsedRealtime()
        val index = p.currentMediaItemIndex
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && mediaItem != null &&
            startedId != null && lastIndex != C.INDEX_UNSET && index != lastIndex
        ) {
            tracker.log(AppEvents.playbackSkip(identity(p, mediaItem), if (index > lastIndex) "next" else "previous"))
        }
        lastIndex = index
        if (mediaItem?.mediaId != startedId) {
            startedId = null
            completedId = null
            milestonesSent = mutableSetOf()
        }
    }

    // ----- Seek and ± ------------------------------------------------------------------------

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) return
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        // The resume-position seek right after an item change is the app's, not the user's.
        if (android.os.SystemClock.elapsedRealtime() - transitionAt < RESUME_SEEK_WINDOW_MS) return
        val delta = newPosition.positionMs - oldPosition.positionMs
        val event = when {
            near(delta, -PlayerManager.SEEK_BACK_MS) -> AppEvents.playbackSkip(identity(p, item), "back")
            near(delta, PlayerManager.SEEK_FORWARD_MS) -> AppEvents.playbackSkip(identity(p, item), "forward")
            else -> AppEvents.playbackSeek(identity(p, item), oldPosition.positionMs / 1000, newPosition.positionMs / 1000)
        }
        tracker.log(event)
    }

    private fun near(delta: Long, target: Long) = abs(delta - target) <= SKIP_TOLERANCE_MS

    // ----- Errors ----------------------------------------------------------------------------

    override fun onPlayerError(error: PlaybackException) {
        val p = player
        val item = p?.currentMediaItem
        // One per error code and item: retries of a dead stream would repeat it.
        if (!errorThrottle.allow("${error.errorCode}/${item?.mediaId}")) return
        tracker.log(
            AppEvents.playbackError(
                item?.let { identity(p, it) },
                error.errorCodeName,
                error.errorCode,
            )
        )
    }

    // ----- 25 / 50 / 75 % --------------------------------------------------------------------

    private val progressTick = object : Runnable {
        override fun run() {
            checkProgress()
            scheduleProgress()
        }
    }

    private fun scheduleProgress() {
        handler.removeCallbacks(progressTick)
        if (player?.isPlaying == true) handler.postDelayed(progressTick, PROGRESS_TICK_MS)
    }

    private fun checkProgress() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        if (startedId != item.mediaId || p.isCurrentMediaItemLive) return
        val duration = durationSec(p) ?: return
        val reached = percent(p) ?: return
        for (milestone in MILESTONES) {
            if (reached >= milestone && milestonesSent.add(milestone)) {
                tracker.log(AppEvents.playbackProgress(identity(p, item), milestone, duration))
            }
        }
    }

    private fun durationSec(p: Player): Long? =
        p.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.div(1000)

    private fun percent(p: Player): Int? {
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return null
        return (p.currentPosition * 100 / duration).toInt().coerceIn(0, 100)
    }

    private fun identity(p: Player?, item: MediaItem): AppEvents.PlaybackItem {
        val type = when {
            item.mediaType == MediaType.Radio || item.mediaType == MediaType.TVChannel -> AppEvents.ContentType.RADIO
            p != null && p.currentMediaItem?.mediaId == item.mediaId && p.isCurrentMediaItemLive -> AppEvents.ContentType.LIVE
            else -> AppEvents.ContentType.EPISODE
        }
        return AppEvents.PlaybackItem(type, item.mediaId, item.feedId)
    }

    private companion object {
        val MILESTONES = listOf(25, 50, 75)
        const val PROGRESS_TICK_MS = 15_000L
        const val SKIP_TOLERANCE_MS = 1_500L
        const val RESUME_SEEK_WINDOW_MS = 3_000L
    }
}
