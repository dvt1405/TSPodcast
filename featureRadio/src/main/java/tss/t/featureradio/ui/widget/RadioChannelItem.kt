package tss.t.featureradio.ui.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import tss.t.coreradio.models.RadioChannel
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.ShimmerAsyncImage
import tss.t.sharedlibrary.ui.animations.skeleton.shimmer
import tss.t.sharedlibrary.ui.shapes.TSShapes

@Composable
fun RadioChannelItem(
    channel: RadioChannel,
    modifier: Modifier = Modifier,
    currentMediaItem: MediaItem? = null,
    isMediaPlaying: Boolean,
    isMediaLoading: Boolean,
    onPlay: () -> Unit = {},
    onPause: () -> Unit = {}
) {
    var isPlaying = remember(isMediaPlaying) {
        isMediaPlaying
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ShimmerAsyncImage(
            model = channel.logo,
            modifier = Modifier
                .width(90.dp)
                .clip(TSShapes.rounded10),
            contentDescription = channel.channelName
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = channel.channelName,
            modifier = Modifier.weight(1f),
            style = TextStyles.Title6
        )
        AnimatedContent(
            targetState = currentMediaItem?.mediaId == channel.channelId,
            label = "",
            transitionSpec = {
                (fadeIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy)
                        ))
                    .togetherWith(ExitTransition.None)
            },
            modifier = Modifier.size(36.dp)
        ) {
            when {
                it -> if (isMediaLoading) {
                    CircularProgressIndicator(
                        color = Colors.White,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Colors.Secondary)
                            .padding(8.dp),
                    )
                } else if (isPlaying) {
                    Icon(
                        Icons.Rounded.Pause, "",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = {
                                isPlaying = false
                                onPause()
                            })
                            .background(Colors.Secondary)
                            .padding(8.dp),
                        tint = Colors.White
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow, "",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = {
                                isPlaying = true
                                onPlay()
                            })
                            .background(Colors.Secondary)
                            .padding(8.dp),
                        tint = Colors.White
                    )
                }

                else -> Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlay)
                        .background(Colors.Secondary)
                        .padding(8.dp),
                    tint = Colors.White
                )
            }
        }
    }
}

@Composable
fun RadioChannelItemShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(90.dp, 60.dp)
                .clip(TSShapes.rounded16)
                .shimmer()
        )
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .weight(0.7f)
                .height(15.dp)
                .clip(TSShapes.rounded10)
                .shimmer()
        )
        Spacer(modifier = Modifier.size(12.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .shimmer()
        )
    }
}