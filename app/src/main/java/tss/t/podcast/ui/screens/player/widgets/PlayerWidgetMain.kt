package tss.t.podcast.ui.screens.player.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import tss.t.podcast.ui.screens.podcastsdetail.toPx
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedresources.R

@Composable
fun PlayerWidgetMain(
    modifier: Modifier = Modifier,
    id: String = "",
    image: String = "",
    title: String = "",
    description: String = "",
    playing: Boolean = false,
    playPauseClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val animateTranslationY = remember {
        Animatable(0f)
    }

    val height = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp - 340)
            .dp.roundToPx()
    }

    val animateAlpha = remember {
        Animatable(1f)
    }

    val draggable = rememberDraggableState { dragAmount ->
        coroutineScope.launch {
            animateTranslationY.snapTo(animateTranslationY.value + dragAmount)
            animateAlpha.snapTo(1f - (dragAmount / height).coerceAtMost(1f))
        }
    }

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFf1f4f9),
                            Color(0xFFE2E9F3)
                        ),
                        radius = 100.dp.toPx()
                    )
                )
                .height(60.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = animateAlpha.value
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image)
                    .error(R.drawable.onboarding_slide_7)
                    .placeholder(R.drawable.image_loader_place_holder_12dp)
                    .build(),
                contentDescription = id,
                modifier = Modifier.size(60.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = TextStyles.Title6,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description, style = TextStyles.SubTitle3,
                    maxLines = 2
                )
            }
            if (playing) {
                Icon(
                    painter = painterResource(R.drawable.round_pause_24),
                    "Pause",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = playPauseClick)
                        .padding(12.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.PlayArrow,
                    "Play", modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = playPauseClick)
                        .padding(12.dp)
                )
            }
        }

    }
}

@Composable
@Preview
fun PlayerWidgetMainPreview() {
    MaterialTheme {
        PlayerWidgetMain(
            title = "Title",
            description = "Description"
        )
    }
}