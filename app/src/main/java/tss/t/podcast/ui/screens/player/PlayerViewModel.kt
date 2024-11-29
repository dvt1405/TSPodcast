package tss.t.podcast.ui.screens.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.os.bundleOf
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tss.t.coreapi.Constants
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import tss.t.podcasts.usecase.favourite.DeleteFavouriteUseCase
import tss.t.podcasts.usecase.favourite.IsFavouriteUseCase
import tss.t.podcasts.usecase.favourite.SaveFavouriteUseCase
import tss.t.podcasts.usecase.history.GetEpisodeLocalUseCase
import tss.t.podcasts.usecase.history.SaveCurrentPlayingUseCase
import tss.t.sharedplayer.controller.TSMediaController
import tss.t.sharedplayer.player.PlayerManager
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val mediaController: TSMediaController,
    private val saveFavouriteUseCase: SaveFavouriteUseCase,
    private val deleteFavouriteUseCase: DeleteFavouriteUseCase,
    private val isFavouriteUseCase: IsFavouriteUseCase,
    private val saveCurrentPlayingUseCase: SaveCurrentPlayingUseCase,
    private val getEpisodeLocalUseCase: GetEpisodeLocalUseCase
) : ViewModel(), Player.Listener {

    private var current: Episode? = null
    private val currentPlayer: Player
        get() = playerManager.player

    private val _playerControlState: MutableStateFlow<PlayerControlState> by lazy {
        MutableStateFlow(PlayerControlState())
    }

    val playerControlState: StateFlow<PlayerControlState>
        get() = _playerControlState.asStateFlow()

    fun playerEpisode(
        episode: Episode,
        podcast: Podcast? = null,
        listItem: List<Episode> = emptyList()
    ) {
        viewModelScope.launch {
            current = episode

            val mediaItem = episode.toMediaItem(podcast?.title)
            val isFav = isFavouriteUseCase(episode)
            _playerControlState.update {
                it.copy(
                    currentMediaItem = mediaItem,
                    isLoading = true,
                    isFavourite = isFav,
                    podcast = podcast,
                    playList = listItem
                )
            }
            currentPlayer.addListener(this@PlayerViewModel)
            playerManager.playMedia(
                episode.toMediaItem(podcast?.title),
                listItem.map {
                    it.toMediaItem(podcast?.title)
                }
            )
            withContext(Dispatchers.IO) {
                saveCurrentPlayingUseCase(
                    episode = episode,
                    podcast = podcast,
                    playList = listItem
                )
            }
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
                checkFavourite()
            }
        }
    }

    private fun checkFavourite(mediaId: String? = null) {
        val id = mediaId ?: mediaController.sessionController
            ?.currentMediaItem
            ?.mediaId
            ?.toLong() ?: return
        val currentMediaItem = mediaController.sessionController?.currentMediaItem
        viewModelScope.launch(Dispatchers.IO) {
            val currentItem =
                _playerControlState.value.playList.firstOrNull { it.id == id } ?: return@launch
            val isFav = isFavouriteUseCase(currentItem)
            _playerControlState.update {
                it.copy(
                    isFavourite = isFav,
                    currentMediaItem = currentMediaItem
                )
            }
        }
    }

    fun onSkipToNext() {
        if (true == mediaController.sessionController?.isConnected) {
            val hasNext = mediaController.sessionController?.hasNextMediaItem() ?: return
            if (hasNext) {
                mediaController.sessionController?.seekToNextMediaItem()
                checkFavourite()
            }
        }
    }

    fun onFavouriteChanged(isFav: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            current?.let {
                if (isFav) {
                    saveFavouriteUseCase(it)
                } else {
                    deleteFavouriteUseCase(it)
                }
            }
        }
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
        saveHistory(currentPlayer.currentMediaItem)
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

    private fun saveHistory(mediaItem: MediaItem?) {
        val item = mediaItem ?: return

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

    fun onRestoreFromNotification(mediaId: String?) {
        Log.d("TuanDv", "onRestoreFromNotification: currentMediaItem: ${currentPlayer.currentMediaItem?.mediaId}")
        if (currentPlayer.currentMediaItem?.mediaId == mediaId) {
            val currentValue = _playerControlState.value.currentMediaItem
            if (currentValue == null && currentPlayer.isPlaying) {
                _playerControlState.update {
                    it.copy(
                        currentMediaItem = currentPlayer.currentMediaItem,
                        isPlaying = currentPlayer.isPlaying,
                        currentDuration = currentPlayer.contentPosition,
                        totalDuration = currentPlayer.contentDuration,
                        currentProgress = (currentPlayer.contentPosition.toDouble() / currentPlayer.contentDuration).toFloat(),
                    )
                }
                viewModelScope.launch {
                    val episodeId = mediaId?.toLongOrNull() ?: return@launch
                    val episode = getEpisodeLocalUseCase(episodeId) ?: return@launch
                    Log.d("TuanDv", "episode: $episode")
                    val podcastAndEpisode = getEpisodeLocalUseCase.getRelated(episode.feedId)
                    Log.d("TuanDv", "podcastAndEpisode: $podcastAndEpisode")
                    _playerControlState.update {
                        it.copy(
                            podcast = podcastAndEpisode?.podcast,
                            playList = podcastAndEpisode?.episode ?: emptyList()
                        )
                    }
                }
                currentPlayer.addListener(this@PlayerViewModel)
            }
        } else {
            val episodeId = mediaId?.toLongOrNull() ?: return
            viewModelScope.launch {
                val episode = getEpisodeLocalUseCase(episodeId) ?: return@launch
                val podcastAndEpisode = getEpisodeLocalUseCase.getRelated(episode.feedId)
                playerEpisode(
                    episode,
                    podcastAndEpisode?.podcast,
                    listItem = podcastAndEpisode?.episode ?: emptyList()
                )
            }
        }
    }

    @Immutable
    data class PlayerControlState(
        val isLoading: Boolean = true,
        val isPlaying: Boolean = false,
        val currentMediaItem: MediaItem? = null,
        val currentDuration: Long = 0L,
        val totalDuration: Long = 0L,
        val currentProgress: Float = 0f,
        val isFavourite: Boolean = false,
        val podcast: Podcast? = null,
        val playList: List<Episode> = emptyList()
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
                .setExtras(
                    bundleOf(
                        Constants.EXTRA_MEDIA_TYPE_KEY to Constants.MEDIA_TYPE_EPISODE
                    )
                )
                .build()
        )
        .build()
}