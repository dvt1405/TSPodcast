package tss.t.sharedplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import javax.inject.Inject
import javax.inject.Singleton

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

    val player: Player
        get() = synchronized(syncLock) {
            _player ?: createPlayer().also {
                _player = it
            }
        }

    init {
        _player = createPlayer()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun createPlayer(): Player {
        return ExoPlayer.Builder(context)
            .setPauseAtEndOfMediaItems(true)
            .build()
            .apply {
                this.playWhenReady = true
            }
    }

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
        _player?.setMediaItem(mediaItem)
        _player?.playWhenReady = true
        _player?.prepare()
        _player?.play()
    }

    fun playMedia(currentItem: MediaItem, mediaItems: List<MediaItem>) {
        ensurePlayerCreate()
        Log.d("TuanDv", "playMedia: ${currentItem}")
        val startIndex = mediaItems.indexOfFirst {
            currentItem.mediaId == it.mediaId
        }
        if (currentItem.mediaId == _player?.currentMediaItem?.mediaId) {
            if (true != _player?.isPlaying) {
                _player?.play()
            }
        } else {
            _player?.setMediaItems(
                mediaItems,
                startIndex,
                0L
            )
            _player?.playWhenReady = true
            _player?.prepare()
            _player?.play()
        }
    }
}