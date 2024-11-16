package tss.t.podcast.ui.screens.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import tss.t.sharedplayer.controller.TSMediaController
import tss.t.sharedplayer.player.PlayerManager
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val mediaController: TSMediaController
) : ViewModel(), Player.Listener {

    private var current: Episode? = null
    private var currentPodcast: Podcast? = null
    private var currentPlayList: List<Episode>? = null
    private val currentPlayer: Player
        get() = playerManager.player

    private val _playerControlState: MutableStateFlow<PlayerControlState> by lazy {
        MutableStateFlow(PlayerControlState())
    }

    val playerControlState: StateFlow<PlayerControlState>
        get() = _playerControlState

    val podcast: Podcast?
        get() = currentPodcast
    val playList: List<Episode>
        get() = currentPlayList ?: emptyList()

    fun playerEpisode(
        episode: Episode,
        podcast: Podcast? = null,
        listItem: List<Episode> = emptyList()
    ) {
        Log.d("TuanDv", "playerEpisode: $episode")
        viewModelScope.launch {
            current = episode
            currentPodcast = podcast
            currentPlayList = listItem

            val mediaItem = episode.toMediaItem(podcast?.title)
            _playerControlState.update {
                it.copy(
                    currentMediaItem = mediaItem,
                    isLoading = true
                )
            }
            currentPlayer.addListener(this@PlayerViewModel)
            playerManager.playMedia(
                episode.toMediaItem(podcast?.title),
                listItem.map {
                    it.toMediaItem(podcast?.title)
                }
            )
        }
    }

    fun onPlayPause() {
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            _playerControlState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false
                )
            }
        } else {
            currentPlayer.play()
            _playerControlState.update {
                it.copy(
                    isPlaying = true
                )
            }
        }
    }

    fun onSkipToPrevious() {
        if (true == mediaController.sessionController?.isConnected) {
            val hasPrevious = mediaController.sessionController?.hasPreviousMediaItem() ?: return
            if (hasPrevious) {
                mediaController.sessionController?.seekToPreviousMediaItem()
                _playerControlState.update {
                    it.copy(
                        currentMediaItem = mediaController.sessionController?.currentMediaItem
                    )
                }
            }
        }
    }

    fun onSkipToNext() {
        if (true == mediaController.sessionController?.isConnected) {
            val hasNext = mediaController.sessionController?.hasNextMediaItem() ?: return
            if (hasNext) {
                mediaController.sessionController?.seekToNextMediaItem()
                _playerControlState.update {
                    it.copy(
                        currentMediaItem = mediaController.sessionController?.currentMediaItem
                    )
                }
            }
        }
    }

    fun onFavourite(isFav: Boolean) {
        val currentMediaItem = current?.id ?: return
    }

    override fun onCleared() {
        currentPlayer.removeListener(this)
        super.onCleared()
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        super.onIsLoadingChanged(isLoading)
    }

    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
        super.onAvailableCommandsChanged(availableCommands)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        super.onTimelineChanged(timeline, reason)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updatePlayPauseButton()
        }

        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updateProgress()
            startTimerIfNeeded()
        }
        if (events.containsAny(
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updateRepeatModeButton()
        }
        if (events.containsAny(
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updateShuffleButton()
        }
        if (events.containsAny(
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SEEK_BACK_INCREMENT_CHANGED,
                Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updateNavigation()
        }
        if (events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updateTimeline()
        }
        if (events.containsAny(
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updatePlaybackSpeedList()
        }
        if (events.containsAny(
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            )
        ) {
            updateTrackLists()
        }
    }

    private fun updateTrackLists() {

    }

    private fun updatePlaybackSpeedList() {

    }

    private fun updateTimeline() {
        _playerControlState.update {
            it.copy(
                currentDuration = currentPlayer.currentPosition,
                totalDuration = currentPlayer.contentDuration
            )
        }
    }

    private fun updateNavigation() {

    }

    private fun updateShuffleButton() {

    }

    private fun updateRepeatModeButton() {

    }

    private fun updateProgress() {
        if (
            currentPlayer.currentMediaItem != null &&
            _playerControlState.value.currentMediaItem?.mediaId != currentPlayer.currentMediaItem?.mediaId
        ) {
            return
        }
        _playerControlState.update {
            it.copy(
                currentDuration = currentPlayer.currentPosition,
                totalDuration = currentPlayer.contentDuration,
                currentProgress = runCatching {
                    currentPlayer.currentPosition.toDouble() / currentPlayer.contentDuration
                }
                    .getOrDefault(0.0)
                    .toFloat()
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun updatePlayPauseButton() {
        val shouldShowPlayButton = Util.shouldShowPlayButton(currentPlayer, true)
        _playerControlState.update {
            it.copy(
                isPlaying = currentPlayer.isPlaying,
                isLoading = currentPlayer.isLoading
            )
        }
    }


    private fun shouldEnablePlayPauseButton(): Boolean {
        return (currentPlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) && (!currentPlayer.isCommandAvailable(
            Player.COMMAND_GET_TIMELINE
        ) || !currentPlayer.currentTimeline.isEmpty))
    }

    private var timerJob: Job? = null
    private fun startTimerIfNeeded() {
        if (true == timerJob?.isActive) return
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                if (_playerControlState.value.currentMediaItem?.mediaId != currentPlayer.currentMediaItem?.mediaId) {
                    cancel()
                }
                delay(200)
                updateProgress()
            }
        }
        timerJob?.invokeOnCompletion {
            timerJob = null
        }
    }

    fun seekTo(progress: Float) {
        currentPlayer.seekTo(
            (currentPlayer.contentDuration * progress).toLong()
        )
    }


    sealed class PlayerUIState {

        data class PreparingPlay(
            val currentMediaItemId: String,
            val playList: List<Episode> = emptyList(),
            val podcast: Podcast? = null
        ) : PlayerUIState()

        data class ReadyToPlay(
            val currentMediaItemId: String,
            val playList: List<Episode> = emptyList(),
            val podcast: Podcast? = null
        ) : PlayerUIState()

        data class Playing(
            val currentMediaItemId: String,
            val playList: List<Episode> = emptyList(),
            val podcast: Podcast? = null
        )
    }

    data class PlayerControlState(
        val isLoading: Boolean = true,
        val isPlaying: Boolean = false,
        val currentMediaItem: MediaItem? = null,
        val currentDuration: Long = 0L,
        val totalDuration: Long = 0L,
        val currentProgress: Float = 0f
    )
}

@OptIn(UnstableApi::class)
fun Episode.toMediaItem(album: CharSequence? = null): MediaItem {
    return MediaItem.Builder()
        .setMediaId("$id")
        .setUri(enclosureUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(persons?.firstOrNull()?.name ?: album)
                .setDescription(
                    HtmlCompat.fromHtml(
                        description ?: "",
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    )
                )
                .setSubtitle(
                    HtmlCompat.fromHtml(
                        description ?: "",
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    )
                )
                .setDisplayTitle(title)
                .setIsPlayable(true)
                .setArtworkUri(Uri.parse(image))
                .setDurationMs(duration)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}