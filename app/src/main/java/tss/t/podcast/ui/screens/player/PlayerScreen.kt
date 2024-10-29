package tss.t.podcast.ui.screens.player

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil.compose.AsyncImage
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TrendingPodcastRes
import tss.t.podcast.ui.navigations.TSNavigators
import tss.t.podcast.ui.screens.podcastsdetail.toPx
import tss.t.podcast.ui.screens.podcastsdetail.widgets.EpisodeWidget
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.utils.imageRequestBuilder
import tss.t.sharedresources.R
import java.util.Formatter
import java.util.Locale

@Composable
fun PlayerScreen(
    episode: MediaItem,
    viewmodel: PlayerViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    val playerControlUIState by viewmodel.playerControlState.collectAsState()

    PlayerScreen(
        podcast = viewmodel.podcast,
        episode = episode,
        playList = viewmodel.playList,
        isPlaying = playerControlUIState.isPlaying,
        contentDuration = playerControlUIState.totalDuration,
        currentPosition = playerControlUIState.currentDuration,
        progress = {
            playerControlUIState.currentProgress
        },
        onFavourite = {
            viewmodel.onFavourite()
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
        onShuffle = {},
        onLoop = {},
        onSelected = {
            viewmodel.playerEpisode(this)
        },
        onClosePlayer = {
            TSNavigators.popBack()
        }
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
            viewmodel.onFavourite()
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
        onShuffle = {},
        onLoop = {},
        onSelected = {
            viewmodel.playerEpisode(this, podcast, playList)
        },
        onClosePlayer = {
            TSNavigators.popBack()
        }
    )
}

@Composable
internal fun PlayerScreen(
    podcast: Podcast?,
    episode: MediaItem,
    isPlaying: Boolean = false,
    currentPosition: Long = 0L,
    contentDuration: Long = 0L,
    playList: List<Episode>,
    progress: () -> Float = { 0f },
    onFavourite: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onLoop: () -> Unit = {},
    onSelected: Episode.() -> Unit = {},
    onClosePlayer: () -> Unit = {}
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
            onShuffle = onShuffle,
            onLoop = onLoop
        )
        SlideArea(
            playList = playList,
            draggableState = draggableState,
            dragDelta = dragDelta,
            onSelected = onSelected
        )
    }
}

@Composable
private fun BoxScope.SlideArea(
    playList: List<Episode>,
    draggableState: DraggableState = rememberDraggableState { },
    dragDelta: Float = 0f,
    onSelected: Episode.() -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val initSize = (screenWidth + 240).dp.toPx()
    val anim = remember {
        Animatable(0f)
    }
    var dragging by remember {
        mutableStateOf(false)
    }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(key1 = dragDelta) {
        if (dragging) {
            anim.snapTo(anim.value + dragDelta)
        }
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                translationY = anim.value
                    .coerceAtMost(48.dp.toPx())
                    .coerceAtLeast(-initSize) + initSize
            }
            .background(
                Color(0xFFE9F3FE),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp
                )
            )
    ) {
        HorizontalDivider(
            color = Colors.NeutralDark.copy(0.3f),
            modifier = Modifier.padding(top = 20.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    draggableState,
                    Orientation.Vertical,
                    onDragStarted = {
                        dragging = true
                    },
                    onDragStopped = {
                        dragging = false
                        delay(30)
                        if (it >= 0) {
                            if (anim.value >= -0.8 * initSize || it > 1000f) {
                                anim.animateTo(0f)
                            } else {
                                anim.animateTo(-initSize)
                            }
                        } else {
                            if (anim.value <= -0.4 * initSize || it < -1000f) {
                                anim.animateTo(-initSize)
                            } else {
                                anim.animateTo(0f)
                            }
                        }
                    }
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "UP NEXT",
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            anim.animateTo(-initSize)
                        }
                    }
                    .padding(16.dp),
                style = TextStyles.Title6
            )
        }
        HorizontalDivider(
            color = Colors.NeutralDark.copy(0.3f),
        )
        LazyColumn(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
        ) {
            item {
                Spacer(modifier = Modifier.size(20.dp))
            }
            items(playList.size) {
                EpisodeWidget(
                    episode = playList[it],
                    onClick = {
                        onSelected(playList[it])
                    },
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                )
                HorizontalDivider()
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BoxScope.PlayerArea(
    podcast: Podcast?,
    episode: MediaItem,
    isPlaying: Boolean = false,
    currentPosition: Long = 0L,
    contentDuration: Long = 0L,
    progress: () -> Float = { 0f },
    onFavourite: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onLoop: () -> Unit = {},
) {

    val formatBuilder = remember {
        StringBuilder()
    }
    val formatter = remember {
        Formatter(formatBuilder, Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .align(Alignment.TopCenter)
            .navigationBarsPadding()
            .padding(top = 70.dp)
    ) {
        AsyncImage(
            model = imageRequestBuilder(LocalContext.current)
                .data(episode.mediaMetadata.artworkUri)
                .build(),
            contentDescription = episode.mediaMetadata.title.toString(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .padding(bottom = 20.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            text = episode.mediaMetadata.title.toString(),
            style = TextStyles.Title4,
            minLines = 2,
            maxLines = 2,
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            text = podcast?.title ?: episode.mediaMetadata.description.toString(),
            style = TextStyles.SubTitle3,
            maxLines = 1
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp)
                .height(6.dp),
            progress = progress,
            color = Colors.Primary,
            gapSize = 0.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                Util.getStringForTime(
                    formatBuilder,
                    formatter,
                    currentPosition
                ), style = TextStyles.SubTitle4
            )
            Text(
                Util.getStringForTime(
                    formatBuilder,
                    formatter,
                    contentDuration
                ), style = TextStyles.SubTitle4
            )
        }
        PlayerControl(
            isPlaying = isPlaying,
            onFavourite = onFavourite,
            onSkipPrevious = onSkipPrevious,
            onPlayPause = onPlayPause,
            onSkipToNext = onSkipToNext,
            onShuffle = onShuffle
        )
    }
}

@Composable
private fun PlayerControl(
    isPlaying: Boolean = false,
    onFavourite: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Icons.Rounded.FavoriteBorder,
            "Fav",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onFavourite)
                .padding(14.dp)

        )
        Icon(
            painter = painterResource(R.drawable.round_skip_previous_24),
            "SkipPrevious",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onSkipPrevious)
                .padding(14.dp)
        )
        AnimatedContent(isPlaying, label = "") {
            if (it) {
                Icon(
                    painter = painterResource(R.drawable.round_pause_24),
                    "PlayPause",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Colors.Secondary)
                        .clickable(onClick = onPlayPause)
                        .padding(24.dp),
                    tint = Colors.White
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    "PlayPause",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Colors.Secondary)
                        .clickable(onClick = onPlayPause)
                        .padding(24.dp),
                    tint = Colors.White
                )
            }
        }

        Icon(
            painter = painterResource(R.drawable.round_skip_next_24),
            "SkipToNext",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onSkipToNext)
                .padding(14.dp)
        )
        Icon(
            painter = painterResource(R.drawable.round_playlist_play_24),
            "Fav",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onShuffle)
                .padding(14.dp)

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