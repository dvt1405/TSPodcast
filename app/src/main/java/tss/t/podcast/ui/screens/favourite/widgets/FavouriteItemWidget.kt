package tss.t.podcast.ui.screens.favourite.widgets

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
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import tss.t.core.models.FavouriteDTO
import tss.t.podcast.ui.screens.podcastsdetail.widgets.placeHolderShape
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.placeholder
import tss.t.sharedlibrary.utils.imageRequestBuilder
import tss.t.sharedresources.R

@Composable
fun FavouriteItemWidget(
    episode: FavouriteDTO,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: (FavouriteDTO) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable {
                onClick(episode)
            }
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = imageRequestBuilder(LocalContext.current)
                .data(episode.image)
                .placeholder(R.drawable.image_loader_place_holder_12dp)
                .error(R.drawable.onboarding_slide_7)
                .crossfade(tss.t.podcast.ui.theme.Motion.IMAGE_CROSSFADE)
                .build(),
            contentDescription = episode.image,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .placeholderIf(isLoading, Colors.Secondary.copy(alpha = 0.3f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Spacer(Modifier.height(if (isLoading) 4.dp else 0.dp))
            Text(
                episode.title ?: "",
                style = TextStyles.Title6,
                modifier = Modifier
                    .placeholderIf(isLoading, Colors.Secondary.copy(alpha = 0.3f), placeHolderShape)
                    .then(
                        if (isLoading) Modifier
                            .height(15.dp)
                            .fillMaxWidth()
                        else Modifier
                    )
            )
            Spacer(Modifier.height(if (isLoading) 4.dp else 2.dp))
            Text(
                text = tss.t.podcast.ui.screens.common.rememberHtmlText(
                    episode.description,
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                ),
                style = TextStyles.SubTitle4.copy(Colors.TextDescriptionColor),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .placeholderIf(isLoading, Colors.Secondary.copy(alpha = 0.3f), placeHolderShape)
                    .then(
                        if (isLoading) Modifier
                            .height(13.dp)
                            .fillMaxWidth(0.6f) else Modifier
                    ),
            )
        }
    }
}

// Rows here are never in the loading state; the composed placeholder modifier (two
// transitions each, three per row) only exists when one is.
private fun Modifier.placeholderIf(
    visible: Boolean,
    color: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape
): Modifier = if (visible) placeholder(visible, color, shape) else this