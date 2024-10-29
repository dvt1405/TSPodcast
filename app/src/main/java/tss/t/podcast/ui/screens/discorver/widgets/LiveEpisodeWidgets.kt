package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tss.t.coreapi.models.LiveEpisode
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.placeholder
import tss.t.sharedresources.R

@Composable
fun LiveEpisodeWidgets(
    modifier: Modifier = Modifier,
    episode: LiveEpisode? = LiveEpisode.default,
    isLoading: Boolean = false,
    placeHolderColor: Color = Colors.Secondary.copy(0.3f),
    onClick: LiveEpisode.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .clickable(
                onClick = {
                    episode?.let { onClick(it) }
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (episode?.feedImage.isNullOrEmpty()) {
            Image(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .placeholder(
                        isLoading, placeHolderColor,
                        RoundedCornerShape(12.dp)
                    ),
                painter = painterResource(R.drawable.onboarding_slide_7),
                contentDescription = "Fav"
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(
                        episode?.feedImage
                    )
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    )
                    .placeholder(R.drawable.image_loader_place_holder_12dp)
                    .crossfade(true)
                    .crossfade(250)
                    .build(),
                contentDescription = episode?.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                episode?.title ?: "", style = TextStyles.Title6,
                modifier = Modifier
                    .placeholder(
                        isLoading, placeHolderColor,
                        RoundedCornerShape(4.dp)
                    )
                    .then(
                        if (isLoading) {
                            Modifier
                                .fillMaxWidth(0.9f)
                                .height(15.dp)
                        } else {
                            Modifier
                        }
                    ),
                maxLines = 1
            )
            Spacer(
                Modifier.size(
                    if (isLoading) 4.dp else 2.dp
                )
            )
            Text(
                buildAnnotatedString {
                    append(
                        HtmlCompat.fromHtml(
                            episode?.datePublishedPretty ?: "",
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        )
                    )
                },
                style = TextStyles.SubTitle4.copy(Colors.TextDescriptionColor),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .placeholder(
                        isLoading, placeHolderColor,
                        RoundedCornerShape(4.dp)
                    )
                    .then(
                        if (isLoading) {
                            Modifier
                                .padding(top = 4.dp)
                                .height(12.dp)
                                .fillMaxWidth(0.5f)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
@Preview
fun LiveEpisodeWidgetsPreview() {
    Column(modifier = Modifier.fillMaxSize()) {
        LiveEpisodeWidgets(isLoading = true)
        Box(modifier = Modifier.size(24.dp))
        LiveEpisodeWidgets(isLoading = false)
    }
}
