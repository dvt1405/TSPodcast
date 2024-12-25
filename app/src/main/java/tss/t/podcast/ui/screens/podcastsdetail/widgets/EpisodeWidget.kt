package tss.t.podcast.ui.screens.podcastsdetail.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tss.t.coreapi.models.Episode
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.placeholder
import tss.t.sharedplayer.utils.ext.album
import tss.t.sharedplayer.utils.ext.artist
import tss.t.sharedplayer.utils.ext.description
import tss.t.sharedplayer.utils.ext.feedImage
import tss.t.sharedplayer.utils.ext.image
import tss.t.sharedplayer.utils.ext.link
import tss.t.sharedplayer.utils.ext.title
import tss.t.sharedresources.R
import tss.t.sharedresources.SharedConstants

val placeHolderShape = RoundedCornerShape(4.dp)
val episodeCoverShape = RoundedCornerShape(12.dp)

@Composable
fun EpisodeWidget(
    modifier: Modifier = Modifier,
    episode: Episode? = null,
    isLoading: Boolean = false,
) {
    val context = LocalContext.current
    val imageRequest = remember(episode) {
        ImageRequest.Builder(context)
            .data(
                episode?.image
                    .takeIf {
                        !it.isNullOrEmpty()
                    }
                    ?: episode?.feedImage
            )
            .addHeader(
                SharedConstants.USER_AGENT_KEY,
                SharedConstants.USER_AGENT_WEB_VALUE
            )
            .apply {
                episode?.link?.let {
                    this.addHeader(
                        SharedConstants.REFERER_KEY,
                        it
                    )
                }
            }
            .placeholder(R.drawable.image_loader_place_holder_12dp)
            .error(R.drawable.onboarding_slide_7)
            .crossfade(true)
            .crossfade(200)
            .build()
    }
    val spacerHeight = remember(isLoading) {
        if (isLoading) 4.dp else 2.dp
    }
    val title = remember(episode) {
        episode?.title ?: ""
    }
    val description = remember(episode) {
        buildAnnotatedString {
            append(
                HtmlCompat.fromHtml(
                    episode?.description ?: "",
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                    null,
                    null
                )
            )
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Image",
            modifier = Modifier
                .size(60.dp)
                .clip(episodeCoverShape)
                .placeholder(isLoading, Colors.Secondary.copy(alpha = 0.3f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Spacer(Modifier.height(if (isLoading) 4.dp else 0.dp))
            Text(
                title,
                style = TextStyles.Title6,
                modifier = Modifier
                    .placeholder(
                        visible = isLoading,
                        color = Colors.Secondary.copy(alpha = 0.3f),
                        shape = placeHolderShape
                    )
                    .then(
                        if (isLoading) Modifier
                            .height(15.dp)
                            .fillMaxWidth()
                        else Modifier
                    )
            )
            Spacer(Modifier.height(spacerHeight))
            Text(
                text = description,
                style = TextStyles.SubTitle4.copy(Colors.TextDescriptionColor),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .placeholder(
                        visible = isLoading,
                        color = Colors.Secondary.copy(alpha = 0.3f),
                        shape = placeHolderShape
                    )
                    .then(
                        if (isLoading) Modifier
                            .height(13.dp)
                            .fillMaxWidth(0.6f) else Modifier
                    )
            )
        }
    }
}


@Composable
fun EpisodeWidget(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem?,
    isLoading: Boolean = false,
) {
    val context = LocalContext.current
    val imageRequest = remember(mediaItem) {
        ImageRequest.Builder(context)
            .data(
                mediaItem?.image
                    .takeIf {
                        !it.isNullOrEmpty()
                    }
                    ?: mediaItem?.feedImage
            )
            .addHeader(
                SharedConstants.USER_AGENT_KEY,
                SharedConstants.USER_AGENT_WEB_VALUE
            )
            .apply {
                mediaItem?.link?.let {
                    this.addHeader(
                        SharedConstants.REFERER_KEY,
                        it
                    )
                }
            }
            .placeholder(R.drawable.image_loader_place_holder_12dp)
            .error(R.drawable.onboarding_slide_7)
            .crossfade(true)
            .crossfade(200)
            .build()
    }
    val spacerHeight = remember(isLoading) {
        if (isLoading) 4.dp else 2.dp
    }
    val title = remember(mediaItem) {
        mediaItem?.title ?: ""
    }
    val description = remember(mediaItem) {
        buildAnnotatedString {
            append(
                HtmlCompat.fromHtml(
                    /* source = */ mediaItem?.description
                        ?: mediaItem?.album
                        ?: mediaItem?.artist
                        ?: "",
                    /* flags = */ HtmlCompat.FROM_HTML_MODE_COMPACT,
                    /* imageGetter = */ null,
                    /* tagHandler = */ null
                )
            )
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Image",
            modifier = Modifier
                .size(60.dp)
                .clip(episodeCoverShape)
                .placeholder(isLoading, Colors.Secondary.copy(alpha = 0.3f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Spacer(Modifier.height(if (isLoading) 4.dp else 0.dp))
            Text(
                title,
                style = TextStyles.Title6,
                modifier = Modifier
                    .placeholder(
                        visible = isLoading,
                        color = Colors.Secondary.copy(alpha = 0.3f),
                        shape = placeHolderShape
                    )
                    .then(
                        if (isLoading) Modifier
                            .height(15.dp)
                            .fillMaxWidth()
                        else Modifier
                    )
            )
            Spacer(Modifier.height(spacerHeight))
            Text(
                text = description,
                style = TextStyles.SubTitle4.copy(Colors.TextDescriptionColor),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .placeholder(
                        visible = isLoading,
                        color = Colors.Secondary.copy(alpha = 0.3f),
                        shape = placeHolderShape
                    )
                    .then(
                        if (isLoading) Modifier
                            .height(13.dp)
                            .fillMaxWidth(0.6f) else Modifier
                    )
            )
        }
    }
}

@Composable
@Preview
fun EpisodeWidgetPreview() {
    EpisodeWidget(
        isLoading = true
    )
}