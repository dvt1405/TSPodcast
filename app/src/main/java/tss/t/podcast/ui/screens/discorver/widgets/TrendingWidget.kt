package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tss.t.coreapi.models.TrendingPodcast
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.shadow
import tss.t.sharedresources.R


@Composable
@Preview
fun TrendingWidget(
    modifier: Modifier = Modifier,
    podcast: TrendingPodcast = TrendingPodcast.default,
    onClick: TrendingPodcast.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .shadow(
                Color(0xAA000000),
                4.dp,
                8.dp,
                32.dp
            )
            .background(Colors.White, RoundedCornerShape(20.dp))
            .width(180.dp)
            .clickable {
                onClick(podcast)
            }
            .padding(bottom = 16.dp),
    ) {
        if (podcast.image.isEmpty()) {
            Image(
                painter = painterResource(R.drawable.onboarding_slide_7),
                "${podcast.title} Cover",
                modifier = Modifier
                    .width(180.dp)
                    .aspectRatio(13f / 16),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                podcast.image,
                podcast.image,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(180.dp)
                    .aspectRatio(13f / 16),
            )
        }

        Text(
            podcast.title, style = TextStyles.Title6,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            minLines = 2,
            maxLines = 2,
        )
    }
}
