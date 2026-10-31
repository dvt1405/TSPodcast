package tss.t.podcast.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import tss.t.podcast.R
import tss.t.podcast.ui.screens.common.LocalShowActions
import tss.t.podcast.ui.screens.common.ShowRef
import tss.t.podcast.ui.screens.common.WebLinks
import tss.t.podcast.ui.screens.common.shareText
import tss.t.sharedplayer.player.feedId
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavHostController
import com.google.gson.Gson
import kotlinx.coroutines.launch
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.TrendingPodcastRes
import tss.t.podcast.ui.screens.player.widgets.PlayerArea
import tss.t.podcast.ui.screens.player.widgets.SlideArea
import tss.t.podcast.ui.screens.player.widgets.SlideAreaState
import tss.t.sharedplayer.player.PlaybackExtrasState
import tss.t.sharedplayer.playback.SleepTimerOption
import tss.t.sharedlibrary.theme.Colors

@Composable
fun PlayerScreen(
    navHost: NavHostController,
    episode: MediaItem,
    viewmodel: PlayerViewModel
) {
    val playerControlUIState by viewmodel.playerControlState.collectAsState()
    val extras by viewmodel.playbackExtras.collectAsState()
    var slideState by remember {
        mutableStateOf(SlideAreaState.Hidden)
    }
    val coroutineScope = rememberCoroutineScope()
    PlayerScreen(
        episode = episode,
        isPlaying = playerControlUIState.isPlaying,
        slideState = slideState,
        currentPosition = playerControlUIState.currentDuration,
        contentDuration = playerControlUIState.totalDuration,
        playList = playerControlUIState.playList,
        progress = {
            playerControlUIState.currentProgress
        },
        extras = extras,
        hasError = playerControlUIState.exception != null,
        onFavourite = {
            viewmodel.onFavouriteChanged(it)
        },
        onSeekBack = viewmodel::onSeekBack,
        onSeekForward = viewmodel::onSeekForward,
        onSpeedChanged = viewmodel::onSpeedChanged,
        onApplyToAllChanged = viewmodel::onApplySpeedToAllChanged,
        onSleepTimerSelected = viewmodel::onSleepTimerSelected,
        onRetry = viewmodel::onRetry,
        onPlayPause = {
            viewmodel.onPlayPause()
        },
        onPlayListClick = {
            slideState = SlideAreaState.Expanded
        },
        onLoop = {},
        onSeek = {
            viewmodel.seekTo(it)
        },
        onSelected = {
            val selectedEpisode = this
            coroutineScope.launch {
                viewmodel.playerMediaItem(selectedEpisode)
                slideState = SlideAreaState.Hidden
            }
        },
        onClosePlayer = {
            navHost.popBackStack()
        },
        onDismissSlideArea = {
            slideState = SlideAreaState.Hidden
        },
        isEpisodeFavourite = playerControlUIState.isFavourite,
        onSlideStateChanged = {
            slideState = it
        }
    )
}

@Composable
internal fun PlayerScreen(
    episode: MediaItem,
    isPlaying: Boolean = false,
    slideState: SlideAreaState = SlideAreaState.Hidden,
    currentPosition: Long = 0L,
    contentDuration: Long = 0L,
    playList: List<MediaItem>,
    progress: () -> Float = { 0f },
    extras: PlaybackExtrasState = PlaybackExtrasState(),
    hasError: Boolean = false,
    onFavourite: (Boolean) -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSpeedChanged: (Int) -> Unit = {},
    onApplyToAllChanged: (Boolean) -> Unit = {},
    onSleepTimerSelected: (SleepTimerOption) -> Unit = {},
    onRetry: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onPlayListClick: () -> Unit = {},
    onLoop: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSelected: MediaItem.() -> Unit = {},
    onClosePlayer: () -> Unit = {},
    onDismissSlideArea: () -> Unit = {},
    isEpisodeFavourite: Boolean = false,
    onSlideStateChanged: (SlideAreaState) -> Unit = {},
) {
    var dragDelta by remember {
        mutableFloatStateOf(0f)
    }
    var fling by remember {
        mutableFloatStateOf(0f)
    }
    val draggableState: DraggableState = rememberDraggableState {
        dragDelta = it
    }
    var isDragging by remember {
        mutableStateOf(false)
    }
    var playerArea by remember {
        mutableIntStateOf(0)
    }
    Box(modifier = Modifier
        .fillMaxWidth()
        .draggable(
            state = rememberDraggableState {
                dragDelta = it
            },
            orientation = Orientation.Vertical,
            onDragStarted = {
                dragDelta = 0f
                isDragging = true
            },
            onDragStopped = {
                isDragging = false
                fling = it
            }
        )
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "ClosePlayer",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = {
                        onClosePlayer()
                    })
                    .padding(12.dp)
            )
            // "…" menu (iOS M6 parity): share the episode, report the show. It used to close
            // the Player, a button that did not do what it showed.
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val showActions = LocalShowActions.current
                val shareTitle = stringResource(R.string.share_chooser_title)
                val title = episode.mediaMetadata.title?.toString().orEmpty()
                val episodeId = episode.mediaId.toLongOrNull()
                val shareText = stringResource(
                    R.string.share_episode_text,
                    title,
                    if (episodeId != null) WebLinks.episode(episodeId, title) else WebLinks.WEB_BASE
                )
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .rotate(90f)
                        .clickable {
                            if (slideState == SlideAreaState.Hidden) {
                                menuOpen = true
                            } else {
                                onDismissSlideArea()
                            }
                        }
                        .padding(12.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_more_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menuOpen = false
                            context.shareText(shareText, shareTitle)
                        }
                    )
                    val feedId = episode.feedId
                    if (showActions != null && feedId != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_more_report)) },
                            leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                            onClick = {
                                menuOpen = false
                                showActions.report(
                                    ShowRef(feedId, episode.mediaMetadata.albumTitle?.toString() ?: title, null)
                                )
                            }
                        )
                    }
                }
            }
        }
        PlayerArea(
            episode = episode,
            isPlaying = isPlaying,
            isEpisodeFavourite = isEpisodeFavourite,
            currentPosition = currentPosition,
            contentDuration = contentDuration,
            progress = progress,
            extras = extras,
            hasError = hasError,
            onFavourite = onFavourite,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onSpeedChanged = onSpeedChanged,
            onApplyToAllChanged = onApplyToAllChanged,
            onSleepTimerSelected = onSleepTimerSelected,
            onRetry = onRetry,
            onPlayPause = onPlayPause,
            onPlayListClick = onPlayListClick,
            onLoop = onLoop,
            onSeek = onSeek,
            onPlayerSizeChanged = {
                playerArea  = it.coerceAtLeast(playerArea)
            }
        )
        SlideArea(
            playList = playList,
            draggableState = draggableState,
            dragDelta = dragDelta,
            onSelected = onSelected,
            isDragInProgress = isDragging,
            state = slideState,
            onStateChanged = onSlideStateChanged,
            fling = fling,
            playerAreaHeight = playerArea
        )
    }
}

@Composable
@Preview
fun PlayerScreenPreview() {
    val context = LocalContext.current
    val episodeResponse = context.assets.open("Episode.json")
        .bufferedReader()
        .readText()
        .let {
            Gson().fromJson(it, EpisodeResponse::class.java)
        }

    val podcast = context.assets.open("Trending.json")
        .bufferedReader()
        .readText()
        .let {
            Gson().fromJson(it, TrendingPodcastRes::class.java)
        }
        .items
        .first()

    MaterialTheme {
        Box(
            modifier = Modifier
                .wrapContentHeight()
                .background(Colors.White)
        ) {
            PlayerScreen(
                episode = episodeResponse.items.first().toMediaItem(podcast.title),
                playList = episodeResponse.items.map {
                    it.toMediaItem()
                },
                progress = {
                    0.2f
                }
            )
        }
    }
}