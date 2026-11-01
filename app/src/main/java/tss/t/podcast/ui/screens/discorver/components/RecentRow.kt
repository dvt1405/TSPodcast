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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.Podcast
import tss.t.podcast.ui.screens.discorver.widgets.AsyncTrendingWidget
import tss.t.podcast.ui.screens.discorver.widgets.PlaceholderPulse
import tss.t.podcast.ui.screens.discorver.widgets.TrendingWidget


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecentRow(
    isRefreshing: Boolean,
    placeHolderColor: PlaceholderPulse,
    recentFeeds: List<Podcast>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    listState: LazyListState = rememberLazyListState(),
    onTrendingClick: Podcast.() -> Unit
) {
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "start", contentType = "spacer") { Spacer(modifier = Modifier.size(4.dp)) }
        if (isRefreshing) {
            items(20, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                AsyncTrendingWidget(placeHolderColor)
            }
        } else {
            items(recentFeeds, contentType = { "podcast" }) { item ->
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
