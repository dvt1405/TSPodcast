package tss.t.podcast.ui.screens.podcastsdetail.widgets

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tss.t.coreapi.models.Episode
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.placeholder
import tss.t.sharedresources.R

val placeHolderShape = RoundedCornerShape(4.dp)

@Composable
fun EpisodeWidget(
    modifier: Modifier = Modifier,
    episode: Episode? = null,
    isLoading: Boolean = false,
    onClick: Episode.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .clickable {
                episode?.let { onClick(it) }
            }
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(
                    episode?.image
                )
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
                .placeholder(R.drawable.image_loader_place_holder_12dp)
                .error(R.drawable.onboarding_slide_7)
                .crossfade(true)
                .crossfade(200)
                .build(),
            contentDescription = episode?.image,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .placeholder(isLoading, Colors.Secondary.copy(alpha = 0.3f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Spacer(Modifier.height(if (isLoading) 4.dp else 0.dp))
            Text(
                episode?.title ?: "",
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
            Spacer(Modifier.height(if (isLoading) 4.dp else 2.dp))
            Text(
                text = buildAnnotatedString {
                    append(
                        HtmlCompat.fromHtml(
                            episode?.description ?: "",
                            HtmlCompat.FROM_HTML_MODE_COMPACT,
                            null,
                            null
                        )
                    )
                },
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
                    ),
                onTextLayout = {
                    if (it.hasVisualOverflow) {
                    }
                }
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