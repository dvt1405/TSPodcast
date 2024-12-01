package tss.t.podcast.ui.screens.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import com.google.gson.Gson
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TrendingPodcastRes
import tss.t.podcast.ui.navigations.TSNavigators
import tss.t.podcast.ui.screens.player.widgets.PlayerArea
import tss.t.podcast.ui.screens.player.widgets.SlideArea
import tss.t.podcast.ui.screens.player.widgets.SlideAreaState
import tss.t.sharedlibrary.theme.Colors

@Composable
fun PlayerScreen(
    episode: MediaItem,
    viewmodel: PlayerViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    val playerControlUIState by viewmodel.playerControlState.collectAsState()
    var slideState by remember {
        mutableStateOf(SlideAreaState.Hidden)
    }
    PlayerScreen(
        podcast = playerControlUIState.podcast,
        episode = episode,
        playList = playerControlUIState.playList,
        isPlaying = playerControlUIState.isPlaying,
        contentDuration = playerControlUIState.totalDuration,
        currentPosition = playerControlUIState.currentDuration,
        progress = {
            playerControlUIState.currentProgress
        },
        onFavourite = {
            viewmodel.onFavouriteChanged(it)
        },
        onSkipToNext = {
            viewmodel.onSkipToNext()
        },
        onSkipPrevious = {
            viewmodel.onSkipToPrevious()
        },
        onPlayPause = {
            viewmodel.onPlayPause()
        },
        onPlayListClick = {
            slideState = SlideAreaState.Expanded
        },
        onLoop = {},
        onSelected = {
            viewmodel.playerEpisode(this)
        },
        onClosePlayer = {
            TSNavigators.popBack()
        },
        onSeek = {
            viewmodel.seekTo(it)
        },
        slideState = slideState,
        onSlideStateChanged = {
            slideState = it
        },
        isEpisodeFavourite = playerControlUIState.isFavourite
    )
}

@Composable
fun PlayerScreen(
    podcast: Podcast,
    episode: Episode,
    playList: List<Episode>,
    viewmodel: PlayerViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    LaunchedEffect(episode) {
        viewmodel.playerEpisode(
            episode,
            podcast,
            playList
        )
    }
    var slideState by remember {
        mutableStateOf(SlideAreaState.Hidden)
    }
    val playerControlUIState by viewmodel.playerControlState.collectAsState()

    PlayerScreen(
        podcast = podcast,
        episode = playerControlUIState.currentMediaItem ?: episode.toMediaItem(podcast.title),
        playList = playList,
        isPlaying = playerControlUIState.isPlaying,
        contentDuration = playerControlUIState.totalDuration,
        currentPosition = playerControlUIState.currentDuration,
        progress = {
            playerControlUIState.currentProgress
        },
        onFavourite = {
            viewmodel.onFavouriteChanged(it)
        },
        onSkipToNext = {
            viewmodel.onSkipToNext()
        },
        onSkipPrevious = {
            viewmodel.onSkipToPrevious()
        },
        onPlayPause = {
            viewmodel.onPlayPause()
        },
        onPlayListClick = {
            slideState = SlideAreaState.Expanded
        },
        onLoop = {},
        onSelected = {
            viewmodel.playerEpisode(this, podcast, playList)
        },
        onClosePlayer = {
            TSNavigators.popBack()
        },
        onSeek = {
            viewmodel.seekTo(it)
        },
        onSlideStateChanged = {
            slideState = it
        },
        slideState = slideState,
        isEpisodeFavourite = playerControlUIState.isFavourite
    )
}

@Composable
internal fun PlayerScreen(
    podcast: Podcast?,
    episode: MediaItem,
    isPlaying: Boolean = false,
    slideState: SlideAreaState = SlideAreaState.Hidden,
    currentPosition: Long = 0L,
    contentDuration: Long = 0L,
    playList: List<Episode>,
    progress: () -> Float = { 0f },
    onFavourite: (Boolean) -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onPlayListClick: () -> Unit = {},
    onLoop: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSelected: Episode.() -> Unit = {},
    onClosePlayer: () -> Unit = {},
    isEpisodeFavourite: Boolean = false,
    onSlideStateChanged: (SlideAreaState) -> Unit = {},
) {
    var dragDelta by remember {
        mutableFloatStateOf(0f)
    }
    val draggableState: DraggableState = rememberDraggableState {
        dragDelta = it
    }
    Box(modifier = Modifier.fillMaxWidth()) {
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
                    .clickable(onClick = onClosePlayer)
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

                    }
                    .padding(12.dp),
            )
        }
        PlayerArea(
            podcast = podcast,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            contentDuration = contentDuration,
            episode = episode,
            progress = progress,
            onFavourite = onFavourite,
            onSkipPrevious = onSkipPrevious,
            onSkipToNext = onSkipToNext,
            onPlayPause = onPlayPause,
            onPlayListClick = onPlayListClick,
            onLoop = onLoop,
            onSeek = onSeek,
            isEpisodeFavourite = isEpisodeFavourite
        )
        SlideArea(
            playList = playList,
            draggableState = draggableState,
            dragDelta = dragDelta,
            onSelected = onSelected,
            state = slideState,
            onStateChanged = onSlideStateChanged
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
                podcast = podcast,
                episode = episodeResponse.items.first().toMediaItem(podcast.title),
                playList = episodeResponse.items,
                progress = {
                    0.2f
                }
            )
        }
    }
}