package tss.t.podcast.ui.screens.player.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil.compose.AsyncImage
import tss.t.coreapi.models.Podcast
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.utils.imageRequestBuilder
import java.util.Formatter
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BoxScope.PlayerArea(
    podcast: Podcast?,
    episode: MediaItem,
    isPlaying: Boolean = false,
    isEpisodeFavourite: Boolean = false,
    currentPosition: Long = 0L,
    contentDuration: Long = 0L,
    progress: () -> Float = { 0f },
    onFavourite: (Boolean) -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipToNext: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onPlayListClick: () -> Unit = {},
    onLoop: () -> Unit = {},
    onSeek: (Float) -> Unit = {}
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
        PlayerProgress(
            progress(),
            start = 0L,
            end = contentDuration,
            onProgressChanged = {
                onSeek(it)
            },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(8.dp)
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
            isFav = isEpisodeFavourite,
            onFavourite = onFavourite,
            onSkipPrevious = onSkipPrevious,
            onPlayPause = onPlayPause,
            onSkipToNext = onSkipToNext,
            onPlayListClick = onPlayListClick
        )
    }
}