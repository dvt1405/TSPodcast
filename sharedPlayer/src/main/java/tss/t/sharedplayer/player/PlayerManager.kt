package tss.t.sharedplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.internal.SynchronizedObject
import tss.t.core.repository.MediaType
import tss.t.sharedplayer.playback.PlaybackKeyValue
import tss.t.sharedplayer.playback.PlaybackPositions
import tss.t.sharedplayer.playback.PlaybackProfiles
import tss.t.sharedplayer.playback.PlaybackSpeed
import tss.t.sharedplayer.playback.SleepTimer
import tss.t.sharedplayer.playback.SleepTimerAction
import tss.t.sharedplayer.playback.SleepTimerOption
import tss.t.sharedplayer.playback.SleepTimerState
import tss.t.sharedplayer.utils.ext.mediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the Player screen shows beside play/pause: the speed of the current item, the
 * "apply to every show" switch, the sleep timer and whether the item is live (no speed, no
 * resume position).
 */
data class PlaybackExtrasState(
    val speedTenths: Int = PlaybackSpeed.DEFAULT_TENTHS,
    val applyToAll: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val isLive: Boolean = false,
)

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    @OptIn(InternalCoroutinesApi::class)
    private val syncLock by lazy {
        SynchronizedObject()
    }
    private var _player: Player? = null

    @OptIn(InternalCoroutinesApi::class)
    val player: Player
        get() = synchronized(syncLock) {
            _player ?: createPlayer().also {
                _player = it
            }
        }

    private val prefs by lazy { SharedPreferencesKeyValue(context) }
    private val positions by lazy { PlaybackPositions(prefs) }
    private val profiles by lazy { PlaybackProfiles(prefs) }
    private val sleepTimer = SleepTimer()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _extras = MutableStateFlow(PlaybackExtrasState())

    /** Speed, sleep timer and live flag of the current item, for the Player screen. */
    val extras: StateFlow<PlaybackExtrasState> = _extras.asStateFlow()

    /** Elapsed-realtime millis when audio was last playing (0 = never), for the ad policy. */
    @Volatile
    var lastPlayingAt: Long = 0L
        private set

    /** Set while the sleep timer itself pauses, so that pause does not count as the user's. */
    private var pausingForTimer = false
    private var lastPositionSaveAt = 0L

    // No eager construction in init {}: ExoPlayer must be built on a thread with
    // a Looper, and that made the player's thread depend on whichever thread
    // Hilt happened to construct this singleton on. Built lazily on the main
    // looper instead, on first access.

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun createPlayer(): Player {
        if (Looper.myLooper() == null) {
            return runOnMainLooper { buildPlayer() }
        }
        return buildPlayer()
    }

    private fun <T> runOnMainLooper(block: () -> T): T {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: T? = null
        var error: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                result = block()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildPlayer(): Player {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
        return ExoPlayer.Builder(context)
            .setPauseAtEndOfMediaItems(true)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                this.playWhenReady = true
                addListener(extrasListener)
            }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun ensurePlayerCreate() {
        synchronized(syncLock) {
            if (_player == null) {
                _player = createPlayer()
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun playMedia(
        id: String,
        url: String,
        title: CharSequence,
        artist: CharSequence? = null,
        description: CharSequence? = null,
        artworkUri: String,
        duration: Long? = null
    ) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setDescription(description)
                    .setDisplayTitle(description)
                    .setArtworkUri(Uri.parse(artworkUri))
                    .setDurationMs(duration)
                    .build()
            )
            .build()
        ensurePlayerCreate()
        saveCurrentPosition()
        _player?.setMediaItem(mediaItem, positions.resumePositionMs(id, isLive(mediaItem)))
        _player?.playWhenReady = true
        _player?.prepare()
        _player?.play()
    }

    fun playMedia(currentItem: MediaItem, mediaItems: List<MediaItem>) {
        ensurePlayerCreate()
        val startIndex = mediaItems.indexOfFirst {
            currentItem.mediaId == it.mediaId
        }
        if (currentItem.mediaId == _player?.currentMediaItem?.mediaId) {
            if (true != _player?.isPlaying) {
                _player?.play()
            }
        } else {
            saveCurrentPosition()
            val items = if (startIndex < 0) {
                listOf(currentItem)
            } else {
                mediaItems.mapIndexed { index, mediaItem ->
                    if (index == startIndex) currentItem else mediaItem
                }
            }
            _player?.setMediaItems(
                items,
                startIndex.coerceAtLeast(0),
                positions.resumePositionMs(currentItem.mediaId, isLive(currentItem))
            )
            _player?.playWhenReady = true
            _player?.prepare()
            _player?.play()
        }
    }

    /**
     * Saved position of an on-demand episode (0 = start over), for queues that the media session
     * builds itself (Android Auto browse, playback resumption) instead of [playMedia].
     */
    fun resumePositionMs(mediaId: String): Long = positions.resumePositionMs(mediaId, false)

    // ---------------------------------------------------------------------------------------
    // Skip, speed, sleep timer (parity with iOS M6 "player cơ bản")
    // ---------------------------------------------------------------------------------------

    /** −15 s. */
    fun seekBack() {
        _player?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_BACK) }?.seekBack()
    }

    /** +30 s. */
    fun seekForward() {
        _player?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_FORWARD) }?.seekForward()
    }

    /** Sets and remembers the speed of the current show (or of every show, see [setApplyToAll]). Live: ignored. */
    fun setSpeed(tenths: Int) {
        val item = _player?.currentMediaItem
        if (item != null && isLive(item)) return
        val value = PlaybackSpeed.clamp(tenths)
        profiles.setSpeed(item?.feedId, value)
        _player?.setPlaybackSpeed(PlaybackSpeed.rate(value))
        _extras.update { it.copy(speedTenths = value) }
    }

    fun setApplyToAll(on: Boolean) {
        profiles.setApplyToAll(on, _extras.value.speedTenths)
        applySpeedForCurrentItem()
    }

    fun setSleepTimer(option: SleepTimerOption) {
        val wasFading = _player?.volume?.let { it < 1f } == true
        sleepTimer.set(option)
        if (wasFading) _player?.volume = 1f
        publishTimer()
        scheduleTick()
    }

    private fun applySpeedForCurrentItem() {
        val item = _player?.currentMediaItem
        val live = item != null && isLive(item)
        val tenths = if (live) PlaybackSpeed.DEFAULT_TENTHS else profiles.speedFor(item?.feedId)
        _player?.setPlaybackSpeed(PlaybackSpeed.rate(tenths))
        _extras.update {
            it.copy(speedTenths = tenths, applyToAll = profiles.isApplyToAll(), isLive = live)
        }
    }

    private fun publishTimer() {
        _extras.update { it.copy(sleepTimer = sleepTimer.state) }
    }

    private fun isLive(item: MediaItem): Boolean {
        if (item.mediaType == MediaType.Radio) return true
        val current = _player
        return current != null && current.currentMediaItem?.mediaId == item.mediaId &&
            current.isCurrentMediaItemLive
    }

    private fun saveCurrentPosition() {
        val current = _player ?: return
        val item = current.currentMediaItem ?: return
        val duration = current.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        positions.record(item.mediaId, isLive(item), current.currentPosition, duration)
        lastPositionSaveAt = System.currentTimeMillis()
    }

    private val tick = object : Runnable {
        override fun run() {
            val current = _player ?: return
            when (val action = sleepTimer.tick()) {
                SleepTimerAction.None -> if (current.volume < 1f) current.volume = 1f
                is SleepTimerAction.Fade -> current.volume = action.volume
                SleepTimerAction.Stop -> {
                    pausingForTimer = true
                    current.pause()
                    pausingForTimer = false
                    current.volume = 1f
                }
            }
            publishTimer()
            if (current.isPlaying &&
                System.currentTimeMillis() - lastPositionSaveAt >= PlaybackPositions.SAVE_INTERVAL_MS
            ) {
                saveCurrentPosition()
            }
            scheduleTick()
        }
    }

    /** Ticks twice a second while audio plays or a timer runs; stops otherwise. */
    private fun scheduleTick() {
        mainHandler.removeCallbacks(tick)
        val current = _player ?: return
        if (current.isPlaying || sleepTimer.state.isActive) {
            mainHandler.postDelayed(tick, TICK_MS)
        }
    }

    private val extrasListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) return
            when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ->
                    if (!pausingForTimer) {
                        sleepTimer.onManualPause()
                        _player?.volume = 1f
                    }

                Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> sleepTimer.onItemEnded()
            }
            saveCurrentPosition()
            publishTimer()
            scheduleTick()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            lastPlayingAt = android.os.SystemClock.elapsedRealtime()
            if (!isPlaying) saveCurrentPosition()
            scheduleTick()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applySpeedForCurrentItem()
            // Prev/next inside the queue: start where the user left that episode. A new queue
            // from playMedia() already starts at the saved position (PLAYLIST_CHANGED).
            if (mediaItem != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                val resume = positions.resumePositionMs(mediaItem.mediaId, isLive(mediaItem))
                val current = _player
                if (resume > 0L && current != null && current.currentPosition < resume) {
                    current.seekTo(resume)
                }
            }
        }
    }

    companion object {
        const val SEEK_BACK_MS = 15_000L
        const val SEEK_FORWARD_MS = 30_000L
        private const val TICK_MS = 500L

        /** Long extra of an episode's MediaItem metadata: the show it belongs to (speed per show). */
        const val EXTRA_FEED_ID = "tss.extra.feed_id"
    }
}

/** Show id of an episode item, from [PlayerManager.EXTRA_FEED_ID]; null for radio and old items. */
val MediaItem.feedId: Long?
    get() = mediaMetadata.extras
        ?.takeIf { it.containsKey(PlayerManager.EXTRA_FEED_ID) }
        ?.getLong(PlayerManager.EXTRA_FEED_ID)

/** [PlaybackKeyValue] over a private SharedPreferences file. */
private class SharedPreferencesKeyValue(context: Context) : PlaybackKeyValue {
    private val prefs = context.getSharedPreferences("TSPlayback", Context.MODE_PRIVATE)

    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun getBoolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}
