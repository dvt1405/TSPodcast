package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tss.t.coreapi.models.TrendingPodcast
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedresources.R


@Composable
@Preview(backgroundColor = 0x0FD5C0C0)
fun FavouriteWidget(
    modifier: Modifier = Modifier,
    podcast: TrendingPodcast = TrendingPodcast.default,
    onClick: TrendingPodcast.() -> Unit = {}
) {
    Row(
        modifier = modifier.clickable {
            onClick(podcast)
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (podcast.image.isEmpty()) {
            Image(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp)),
                painter = painterResource(R.drawable.onboarding_slide_7),
                contentDescription = "Fav"
            )
        } else {
            AsyncImage(
                podcast.image,
                podcast.image,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            Text(podcast.title, style = TextStyles.Title6)
            Text(podcast.title, style = TextStyles.SubTitle4.copy(Colors.TextDescriptionColor))
        }
    }
}