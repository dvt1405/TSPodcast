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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import tss.t.sharedlibrary.theme.Colors

@Composable
fun PlayerScreen(
    navHost: NavHostController,
    episode: MediaItem,
    viewmodel: PlayerViewModel
) {
    val playerControlUIState by viewmodel.playerControlState.collectAsState()
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
        onFavourite = {
            viewmodel.onFavouriteChanged(it)
        },
        onSkipPrevious = {
            viewmodel.onSkipToPrevious()
        },
        onSkipToNext = {
            viewmodel.onSkipToNext()
        },
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
    onFavourite: (Boolean) -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
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
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More",
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .rotate(90f)
                    .clickable {
                        if (slideState == SlideAreaState.Hidden) {
                            onClosePlayer()
                        } else {
                            onDismissSlideArea()
                        }
                    }
                    .padding(12.dp),
            )
        }
        PlayerArea(
            episode = episode,
            isPlaying = isPlaying,
            isEpisodeFavourite = isEpisodeFavourite,
            currentPosition = currentPosition,
            contentDuration = contentDuration,
            progress = progress,
            onFavourite = onFavourite,
            onSkipPrevious = onSkipPrevious,
            onSkipToNext = onSkipToNext,
            onPlayPause = onPlayPause,
            onPlayListClick = onPlayListClick,
            onLoop = onLoop,
            onSeek = onSeek
        )
        SlideArea(
            playList = playList,
            draggableState = draggableState,
            dragDelta = dragDelta,
            onSelected = onSelected,
            isDragInProgress = isDragging,
            state = slideState,
            onStateChanged = onSlideStateChanged,
            fling = fling
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