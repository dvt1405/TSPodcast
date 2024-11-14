package tss.t.podcast.ui.screens.discorver.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.LiveEpisode
import tss.t.podcast.ui.screens.discorver.widgets.LiveEpisodeWidgets


@Composable
fun LiveRow(
    isRefreshing: Boolean = false,
    liveEpisode: List<List<LiveEpisode>> = emptyList(),
    pagerState: PagerState = rememberPagerState() { liveEpisode.size },
    onClick: LiveEpisode.() -> Unit = {}
) {
    HorizontalPager(
        modifier = Modifier.fillMaxWidth(),
        state = pagerState,
        pageSize = PageSize.Fixed(
            (LocalConfiguration.current.screenWidthDp * 0.9f).dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { index ->
        val childList = liveEpisode[index]
        Column(verticalArrangement = Arrangement.Top) {
            repeat(childList.size) {
                LiveEpisodeWidgets(
                    episode = if (isRefreshing) null else childList[it],
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isRefreshing,
                    onClick = onClick
                )
                if (it < 2) {
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
@Preview
fun LiveRowPreview() {
    LiveRow(
        isRefreshing = false,
        liveEpisode = listOf(
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
        ).chunked(3)
    )
}
@Composable
@Preview
fun LiveRowPreviewLoading() {
    LiveRow(
        isRefreshing = true,
        liveEpisode = listOf(
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
            LiveEpisode.default,
        ).chunked(3)
    )
}
