package tss.t.podcast.ui.screens.player.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedresources.R


/**
 * Main row of the Player, same order as iOS (M6): Favourite · −15 s · Play/Pause · +30 s ·
 * Playlist. Previous/next episode are in the playlist panel; −15/+30 are disabled for live
 * streams, which cannot seek.
 */
@Composable
fun PlayerControl(
    isPlaying: Boolean = false,
    isFav: Boolean = false,
    canSeek: Boolean = true,
    onFavourite: (Boolean) -> Unit = {},
    onSeekBack: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onPlayListClick: () -> Unit = {},
) {
    var fav by remember(isFav) {
        mutableStateOf(isFav)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            if (fav) Icons.Rounded.Favorite
            else Icons.Rounded.FavoriteBorder,
            "Fav",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = {
                    fav = !fav
                    onFavourite(fav)
                })
                .padding(14.dp)

        )
        SkipButton(
            seconds = 15,
            forward = false,
            enabled = canSeek,
            description = stringResource(R.string.player_seek_back_15),
            onClick = onSeekBack
        )
        AnimatedContent(
            targetState = isPlaying,
            label = "",
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 30))
                        + scaleIn(
                    animationSpec = tween(220, delayMillis = 30),
                    initialScale = 0.8f
                )).togetherWith(
                    fadeOut(animationSpec = tween(90))
                            + scaleOut(animationSpec = tween(90), targetScale = 0.8f)
                )
            }
        ) {
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
        SkipButton(
            seconds = 30,
            forward = true,
            enabled = canSeek,
            description = stringResource(R.string.player_seek_forward_30),
            onClick = onSeekForward
        )
        Icon(
            painter = painterResource(R.drawable.round_playlist_play_24),
            "PlayList",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlayListClick)
                .padding(14.dp)

        )
    }
}

/** A circular arrow (mirrored for forward) with the number of seconds inside. */
@Composable
private fun SkipButton(
    seconds: Int,
    forward: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(10.dp)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Replay,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (forward) -1f else 1f },
        )
        Text(
            text = "$seconds",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = LocalContentColor.current,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Preview
@Composable
fun PlayerControlPreview() {
    Box(Modifier.fillMaxWidth()) {
        PlayerControl()
    }
}
