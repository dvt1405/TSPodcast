package tss.t.podcast.ui.screens.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tss.t.core.repository.MediaType
import tss.t.coreapi.Constants
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.usecase.GetPlayableLink
import tss.t.podcasts.usecase.favourite.DeleteFavouriteUseCase
import tss.t.podcasts.usecase.favourite.IsFavouriteUseCase
import tss.t.podcasts.usecase.favourite.SaveFavouriteUseCase
import tss.t.podcasts.usecase.history.GetEpisodeLocalUseCase
import tss.t.podcasts.usecase.history.SaveCurrentPlayingUseCase
import tss.t.sharedplayer.controller.TSMediaController
import tss.t.sharedplayer.player.PlayerManager
import tss.t.sharedplayer.utils.ext.album
import tss.t.sharedplayer.utils.ext.mediaType
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val mediaController: TSMediaController,
    private val saveFavouriteUseCase: SaveFavouriteUseCase,
    private val deleteFavouriteUseCase: DeleteFavouriteUseCase,
    private val isFavouriteUseCase: IsFavouriteUseCase,
    private val saveCurrentPlayingUseCase: SaveCurrentPlayingUseCase,
    private val getEpisodeLocalUseCase: GetEpisodeLocalUseCase,
    private val getPlayableLink: GetPlayableLink
) : ViewModel(), Player.Listener {

    private val currentPlayer: Player
        get() = playerManager.player

    private val _playerControlState: MutableStateFlow<PlayerControlState> by lazy {
        MutableStateFlow(PlayerControlState())
    }

    val playerControlState: StateFlow<PlayerControlState>
        get() = _playerControlState.asStateFlow()

    suspend fun playerEpisode(
        episode: Episode,
        podcast: Podcast? = null,
        listItem: List<Episode> = emptyList()
    ) {
        val currentPodcast = podcast ?: _playerControlState.value.podcast
        viewModelScope.async {
            val playList = listItem.map {
                it.toMediaItem(currentPodcast?.title)
            }.ifEmpty { _playerControlState.value.playList }
            val mediaItem = episode.toMediaItem(currentPodcast?.title)
            val isFav = isFavouriteUseCase(mediaItem)
            _playerControlState.update {
                it.copy(
                    currentMediaItem = mediaItem,
                    isLoading = true,
                    isFavourite = isFav,
                    podcast = currentPodcast,
                    playList = playList,
                    exception = null
                )
            }
            setupPlayerListener()
            playerManager.playMedia(
                currentItem = episode.toMediaItem(currentPodcast?.title),
                mediaItems = playList
            )
            withContext(Dispatchers.IO) {
                saveCurrentPlayingUseCase(
                    episode = episode,
                    podcast = currentPodcast,
                    playList = listItem
                )
            }
        }.await()
    }

    suspend fun playerMediaItem(mediaItem: MediaItem) {
        val currentPodcast = _playerControlState.value.podcast
        val playList = _playerControlState.value.playList
        val mediaType = mediaItem.mediaType
        viewModelScope.async {
            val isFav = isFavouriteUseCase(mediaItem)
            _playerControlState.update {
                it.copy(
                    currentMediaItem = mediaItem,
                    isLoading = true,
                    isFavourite = isFav,
                    podcast = currentPodcast,
                    playList = playList,
                    exception = null
                )
            }
            setupPlayerListener()
            when (mediaType) {
                MediaType.Radio -> {
                    handlePlayRadioItem(mediaItem, playList)
                }

                else -> {
                    playerManager.playMedia(
                        currentItem = mediaItem,
                        mediaItems = playList
                    )
                }
            }
            withContext(Dispatchers.IO) {
                saveCurrentPlayingUseCase(mediaItem)
            }
        }.await()
    }

    private suspend fun handlePlayRadioItem(
        mediaItem: MediaItem,
        playList: List<MediaItem>
    ) {
        val mediaItemResult = extractPlayerLink(mediaItem)
        if (mediaItemResult.isFailure) {
            _playerControlState.update {
                it.copy(
                    exception = mediaItemResult.exceptionOrNull()
                )
            }
        } else {
            val readyToPlay = mediaItemResult.getOrDefault(mediaItem)
            _playerControlState.update {
                it.copy(
                    currentMediaItem = readyToPlay,
                    exception = null
                )
            }
            playerManager.playMedia(
                currentItem = readyToPlay,
                mediaItems = playList,
            )
        }
    }

    private suspend fun extractPlayerLink(mediaItem: MediaItem): Result<MediaItem> {
        val link = getPlayableLink(
            mediaItem.mediaId,
            mediaItem.album!!
        )
        return if (link.isSuccess) {
            Result.success(
                mediaItem.buildUpon()
                    .setUri(link.getOrDefault(emptyList()).first())
                    .build()
            )
        } else {
            Result.failure(link.exceptionOrNull()!!)
        }
    }

    private fun setupPlayerListener() {
        currentPlayer.removeListener(this@PlayerViewModel)
        currentPlayer.addListener(this@PlayerViewModel)
    }

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    suspend fun playRadio(
        radioChannel: RadioChannel,
        listRadio: List<RadioChannel>
    ) {
        val mediaItem = radioChannel.toMediaItem()
        _playerControlState.update {
            it.copy(
                currentMediaItem = mediaItem
            )
        }
        viewModelScope.async {
            val playList = listRadio.map {
                it.toMediaItem(it.category)
            }.ifEmpty { _playerControlState.value.playList }
            val isFav = isFavouriteUseCase(mediaItem)
            _playerControlState.update {
                it.copy(
                    currentMediaItem = mediaItem,
                    isLoading = true,
                    isFavourite = isFav,
                    podcast = null,
                    playList = playList,
                    exception = null
                )
            }
        }.await()
        viewModelScope.launch {
            setupPlayerListener()
            getPlayableLink(radioChannel)
                .mapLatest {
                    if (it.isSuccess) {
                        radioChannel.copy(
                            links = listOf(it.getOrThrow())
                        )
                    } else {
                        radioChannel
                    }
                }
                .map {
                    it.toMediaItem()
                }
                .collectLatest {
                    playerManager.playMedia(
                        currentItem = it,
                        mediaItems = _playerControlState.value.playList
                    )
                    withContext(Dispatchers.IO) {
                        saveCurrentPlayingUseCase(it)
                    }
                    _playerControlState.update {
                        it.copy(
                            isLoading = false,
                            exception = null
                        )
                    }
                }
        }
    }

    fun onPlayPause() {
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            _playerControlState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    exception = null
                )
            }
        } else {
            currentPlayer.play()
            _playerControlState.update {
                it.copy(
                    isPlaying = true,
                    exception = null
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

    fun checkFavourite(mediaId: String? = null) {
        val id = mediaId ?: mediaController.sessionController
            ?.currentMediaItem
            ?.mediaId ?: return
        val currentMediaItem = mediaController.sessionController?.currentMediaItem
        viewModelScope.launch(Dispatchers.IO) {
            val currentItem =
                _playerControlState.value.playList.firstOrNull { it.mediaId == id } ?: return@launch
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
            _playerControlState.value.currentMediaItem?.let {
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
                isLoading = (currentPlayer.playbackState == Player.STATE_IDLE)
                        && !currentPlayer.isPlaying
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
                    val podcastAndEpisode = getEpisodeLocalUseCase.getRelated(episode.feedId)
                    _playerControlState.update {
                        it.copy(
                            podcast = podcastAndEpisode?.podcast,
                            playList = podcastAndEpisode?.episode?.map {
                                it.toMediaItem()
                            } ?: emptyList()
                        )
                    }
                }
                setupPlayerListener()
            }
        } else {
            val episodeId = mediaId?.toLongOrNull() ?: return
            viewModelScope.launch {
                val episode = getEpisodeLocalUseCase(episodeId) ?: return@launch
                val podcastAndEpisode = getEpisodeLocalUseCase.getRelated(episode.feedId)
                playerEpisode(
                    episode = episode,
                    podcast = podcastAndEpisode?.podcast,
                    listItem = podcastAndEpisode?.episode ?: emptyList()
                )
            }
        }
    }

    suspend fun playLive(
        liveEpisode: LiveEpisode,
        listItem: List<LiveEpisode>
    ) {
        val item = Episode.fromLive(liveEpisode)
        val playList = listItem.map {
            Episode.fromLive(it)
        }
        val podcast = Podcast(
            categories = liveEpisode.categories,
            dateCrawled = liveEpisode.dateCrawled,
            datePublished = liveEpisode.datePublished,
            datePublishedPretty = liveEpisode.datePublishedPretty,
            enclosureLength = liveEpisode.enclosureLength,
            enclosureType = liveEpisode.enclosureType,
            enclosureUrl = liveEpisode.enclosureUrl,
            explicit = liveEpisode.explicit,
            feedId = liveEpisode.feedId,
            feedImage = liveEpisode.feedImage,
            feedItunesId = liveEpisode.feedItunesId,
            feedLanguage = liveEpisode.feedLanguage,
            feedTitle = liveEpisode.title,
            guid = liveEpisode.guid,
            id = liveEpisode.feedId,
            image = liveEpisode.image,
            link = liveEpisode.link,
            title = liveEpisode.title,
            description = liveEpisode.description

        )
        playerEpisode(
            episode = item,
            listItem = playList,
            podcast = podcast
        )
    }

    @Immutable
    data class PlayerControlState(
        val isLoading: Boolean = true,
        val isPlaying: Boolean = false,
        val currentMediaItem: MediaItem? = null,
        val mediaType: MediaType = MediaType.PodcastEpisode,
        val currentDuration: Long = 0L,
        val totalDuration: Long = 0L,
        val currentProgress: Float = 0f,
        val isFavourite: Boolean = false,
        val podcast: Podcast? = null,
        val playList: List<MediaItem> = emptyList(),
        val exception: Throwable? = null
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
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setDisplayTitle(title)
                .setIsPlayable(true)
                .setArtworkUri(Uri.parse(getImageUrl()))
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

fun RadioChannel.toMediaItem(album: CharSequence? = "VOV"): MediaItem {
    val link = links.firstOrNull()?.link
    val mimeType = when {
        true == link?.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
        else -> null
    }
    return MediaItem.Builder()
        .setMediaId(channelId)
        .setMimeType(mimeType)
        .setUri(links.firstOrNull()?.link)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(channelName)
                .setArtist(album)
                .setDescription(
                    HtmlCompat.fromHtml(
                        channelName,
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    )
                )
                .setSubtitle(
                    HtmlCompat.fromHtml(
                        album?.toString() ?: "",
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    )
                )
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setDisplayTitle(channelName)
                .setIsPlayable(true)
                .setArtworkUri(Uri.parse(logo))
                .setAlbumTitle(album)
                .setExtras(
                    bundleOf(
                        Constants.EXTRA_MEDIA_TYPE_KEY to Constants.MEDIA_TYPE_RADIO
                    )
                )
                .build()
        )
        .build()
}