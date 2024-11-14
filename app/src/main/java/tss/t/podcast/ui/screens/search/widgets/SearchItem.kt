package tss.t.podcast.ui.screens.search.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import tss.t.coreapi.models.Feed
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.ShimmerAsyncImage
import java.text.SimpleDateFormat

internal val formatDate = SimpleDateFormat("dd/MM/yy")

@Composable
fun SearchPodcastItem(
    feed: Feed,
    onClick: (Feed) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(feed) }
            .padding(12.dp)
    ) {
        ShimmerAsyncImage(
            feed.image,
            contentDescription = feed.title,
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                feed.title,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyles.Title6
            )
            Text(
                buildAnnotatedString {
                    append(feed.author)
                    val cateList = feed.categories?.values?.toList() ?: emptyList()
                    if (cateList.isNotEmpty()) {
                        append(" \u00b7 ")
                        for (i in 0..2) {
                            val cate = cateList.getOrNull(i) ?: continue
                            append(cate)
                            if (i < 2) {
                                append(" \u00b7 ")
                            }
                        }
                    }

                },
                modifier = Modifier.fillMaxWidth(),
                style = TextStyles.Body4
            )
            Spacer(Modifier.size(4.dp))
            Text(
                buildAnnotatedString {
                    append(
                        HtmlCompat.fromHtml(
                            feed.description,
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                },
                style = TextStyles.Body5,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@Preview
fun SearchPodcastItemPreview() {
    Box(Modifier.background(Colors.White)) {
        SearchPodcastItem(feed = Feed.testItem)
    }
}