package tss.t.podcast.ui.screens.discorver.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tss.t.podcasts.NewsItem
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/** Cards of "Thời sự & Bản tin": the newest bulletin of each confirmed news source. */
@Composable
fun NewsRow(
    items: List<NewsItem>,
    onClick: (NewsItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.sourceKey }) { item ->
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Colors.Secondary.copy(alpha = 0.08f))
                    .clickable { onClick(item) }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.episode.feedImage.ifEmpty { item.podcast.image },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.podcast.title.orEmpty(),
                            style = TextStyles.SubTitle3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            DateUtils.getRelativeTimeSpanString(item.episode.datePublished * 1000).toString(),
                            style = TextStyles.SubTitle4,
                            color = Colors.Gray60,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    item.episode.title.orEmpty(),
                    style = TextStyles.SubTitle2,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
