package tss.t.sharedplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
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
    private val context: Context,
    private val recorder: ListeningRecorder,
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

    /**
     * Last position seen while playing, refreshed every tick: a queue skip (previous/next from
     * the notification, the car or the lock screen) has already moved to the new item when
     * onMediaItemTransition arrives, so the item left behind is saved from here.
     */
    private var lastSeen: Snapshot? = null

    /**
     * The paused item whose history the user just removed: not saved again (e.g. when the
     * service is destroyed) until it plays again, or it would come back in "Lịch sử".
     */
    private var forgottenMediaId: String? = null

    private class Snapshot(val item: MediaItem, val positionMs: Long, val durationMs: Long, val live: Boolean)

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
            // Audio focus: a call, a navigation prompt or another media app pauses playback.
            // A transient loss resumes on its own when focus returns, a permanent one stays
            // paused. Every pause saves the position (see onIsPlayingChanged).
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Headphones unplugged / Bluetooth disconnected: pause instead of playing out loud.
            .setHandleAudioBecomingNoisy(true)
            // Keep the CPU and Wi-Fi awake while streaming with the screen off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
                play()
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

    /**
     * Puts a queue back in the player without starting it ("Nghe tiếp" on the next launch): the
     * mini player shows it paused at [positionMs] and [play] continues from there. Nothing is
     * prepared yet, so no data is used and no notification appears until the user presses play.
     * Does nothing when the player already has an item (e.g. still playing in the background).
     */
    fun restore(items: List<MediaItem>, startIndex: Int, positionMs: Long): Boolean {
        if (items.isEmpty()) return false
        ensurePlayerCreate()
        val current = _player ?: return false
        if (current.currentMediaItem != null) return false
        current.playWhenReady = false
        current.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), positionMs.coerceAtLeast(0L))
        return true
    }

    /** Play, preparing first when the item was only restored or stopped after an error. */
    fun play() {
        ensurePlayerCreate()
        val current = _player ?: return
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
        if (current.playbackState == Player.STATE_ENDED) current.seekToDefaultPosition()
        current.play()
    }

    /**
     * Saves the position now and waits for the history row: the session service calls it when
     * the task is swiped away and when it is destroyed, before the process may be killed.
     */
    fun saveNow() {
        val current = _player ?: return
        val item = current.currentMediaItem ?: return
        if (isUntouchedRestore(current, item) || isForgotten(current, item)) return
        val duration = current.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val live = isLive(item)
        positions.record(item.mediaId, live, current.currentPosition, duration)
        lastPositionSaveAt = System.currentTimeMillis()
        if (!live) recorder.recordNow(item, current.currentPosition, duration)
    }

    /** Releases the player (session service destroyed); the next access builds a new one. */
    @OptIn(InternalCoroutinesApi::class)
    fun release() {
        saveNow()
        mainHandler.removeCallbacks(tick)
        synchronized(syncLock) {
            _player?.removeListener(extrasListener)
            _player?.release()
            _player = null
        }
        lastSeen = null
    }

    /** "Xoá khỏi lịch sử": forget where [mediaId] was left. */
    fun forgetPosition(mediaId: String) {
        positions.forget(mediaId)
        if (_player?.currentMediaItem?.mediaId == mediaId && _player?.isPlaying != true) {
            forgottenMediaId = mediaId
        }
        if (lastSeen?.item?.mediaId == mediaId) lastSeen = null
    }

    /** "Xoá lịch sử": forget every saved position (speeds are kept). */
    fun forgetAllPositions() {
        positions.forgetAll()
        if (_player?.isPlaying != true) {
            forgottenMediaId = _player?.currentMediaItem?.mediaId
            lastSeen = null
        }
    }

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
        if (isUntouchedRestore(current, item) || isForgotten(current, item)) return
        val duration = current.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val live = isLive(item)
        positions.record(item.mediaId, live, current.currentPosition, duration)
        lastPositionSaveAt = System.currentTimeMillis()
        if (!live) recorder.record(item, current.currentPosition, duration)
        lastSeen = Snapshot(item, current.currentPosition, duration, live)
    }

    /**
     * An item put back by [restore] and never prepared: its saved position and history row are
     * already right, and its duration is still unknown, so saving it would only lose data.
     */
    private fun isUntouchedRestore(player: Player, item: MediaItem): Boolean =
        player.playbackState == Player.STATE_IDLE && lastSeen?.item?.mediaId != item.mediaId

    private fun isForgotten(player: Player, item: MediaItem): Boolean {
        if (forgottenMediaId == null) return false
        if (player.isPlaying || item.mediaId != forgottenMediaId) {
            forgottenMediaId = null
            return false
        }
        return true
    }

    private fun rememberLastSeen() {
        val current = _player ?: return
        val item = current.currentMediaItem ?: return
        val duration = current.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        lastSeen = Snapshot(item, current.currentPosition, duration, isLive(item))
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
            if (current.isPlaying) {
                if (System.currentTimeMillis() - lastPositionSaveAt >= PlaybackPositions.SAVE_INTERVAL_MS) {
                    saveCurrentPosition()
                } else {
                    rememberLastSeen()
                }
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
            // Stopped for any reason (user, transient or permanent audio focus loss, headphones
            // unplugged, end of episode, error) or started: either way the position and history
            // are saved, so an episode shows in "Lịch sử" from its first second.
            saveCurrentPosition()
            scheduleTick()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The item left behind by a skip inside the queue: save where it was left.
            val left = lastSeen
            if (left != null && left.item.mediaId != mediaItem?.mediaId &&
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            ) {
                positions.record(left.item.mediaId, left.live, left.positionMs, left.durationMs)
                if (!left.live) recorder.record(left.item, left.positionMs, left.durationMs)
            }
            lastSeen = null
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
    override fun removeAll(prefix: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }
}
