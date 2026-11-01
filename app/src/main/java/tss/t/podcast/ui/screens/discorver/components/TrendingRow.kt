package tss.t.podcast.ui.screens.discorver.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.Podcast
import tss.t.podcast.ui.screens.discorver.widgets.AsyncTrendingWidget
import tss.t.podcast.ui.screens.discorver.widgets.PlaceholderColor
import tss.t.podcast.ui.screens.discorver.widgets.TrendingWidget

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TrendingRow(
    trendingRowState: LazyListState,
    isRefreshing: Boolean,
    placeHolderColor: PlaceholderColor,
    listTrending: List<Podcast>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    onTrendingClick: Podcast.() -> Unit
) {
    LazyRow(
        state = trendingRowState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "start", contentType = "spacer") { Spacer(modifier = Modifier.size(4.dp)) }
        if (listTrending.isEmpty()) {
            items(20, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                AsyncTrendingWidget(placeHolderColor)
            }
        } else {
            // No id key: the API can return a show twice, and a duplicate key crashes LazyRow.
            items(listTrending, contentType = { "podcast" }) { item ->
                TrendingWidget(
                    podcast = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onClick = onTrendingClick
                )
            }
        }
    }
}
