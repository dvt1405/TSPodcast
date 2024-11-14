package tss.t.podcast.ui.screens.discorver.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.Podcast
import tss.t.podcast.ui.screens.discorver.widgets.AsyncTrendingWidget
import tss.t.podcast.ui.screens.discorver.widgets.TrendingWidget

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TrendingRow(
    trendingRowState: LazyListState,
    isRefreshing: Boolean,
    placeHolderColor: Color,
    listTrending: List<Podcast>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    onTrendingClick: Podcast.() -> Unit
) {
    LazyRow(
        state = trendingRowState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(modifier = Modifier.size(4.dp)) }
        if (listTrending.isEmpty()) {
            items(20, key = { it }) {
                AsyncTrendingWidget(placeHolderColor)
            }
        } else {
            items(listTrending.size) {
                val item = listTrending[it]
                TrendingWidget(
                    podcast = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope
                ) {
                    onTrendingClick(item)
                }
            }
        }
    }
}
