package tss.t.podcast.ui.screens.discorver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.discorver.components.LiveRow
import tss.t.podcast.ui.screens.discorver.components.RecentRow
import tss.t.podcast.ui.screens.discorver.components.TrendingRow
import tss.t.podcast.ui.screens.discorver.widgets.FavouriteWidget
import tss.t.podcast.ui.screens.discorver.widgets.Indicator
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun DiscoverPodcastsScreen(
    isRefreshing: Map<Int, Boolean> = emptyMap(),
    showLoading: Boolean = true,
    onRefresh: () -> Unit,
    hazeState: HazeState,
    pullRefreshState: PullToRefreshState,
    innerPadding: PaddingValues,
    listTrending: List<Podcast>,
    listFav: List<Podcast>,
    liveEpisode: List<List<LiveEpisode>> = emptyList(),
    pagerState: PagerState = rememberPagerState() { liveEpisode.size },
    recentFeeds: List<Podcast> = emptyList(),
    recentNewFeeds: List<Podcast> = emptyList(),
    onTrendingClick: Podcast.() -> Unit = {},
    onFavClick: Podcast.() -> Unit = {},
    onLiveItemClick: LiveEpisode.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    trendingRowState: LazyListState = rememberLazyListState(),
    renderCount: Int = 0,
    recentFeedState: LazyListState = rememberLazyListState(),
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current!!
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteTransition")
    val placeHolderColor by infiniteTransition.animateColor(
        initialValue = Colors.Primary10.copy(alpha = 0.3f),
        targetValue = Colors.Secondary.copy(alpha = 0.3f),
        animationSpec = infiniteRepeatable(
            tween(
                1700,
                delayMillis = 200
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Color"
    )
    val isFavRefreshing =
        remember(key1 = isRefreshing[MainViewModel.HomepageDataPart.Favourite.value]) {
            isRefreshing[MainViewModel.HomepageDataPart.Favourite.value] ?: true
        }

    val pullToRefreshThreshHold = 50.dp + innerPadding.calculateTopPadding()
    var isLoading by remember(showLoading) {
        mutableStateOf(showLoading)
    }
    LaunchedEffect(showLoading, renderCount) {
        isLoading = showLoading
    }
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = {
            isLoading = true
            onRefresh()
        },
        state = pullRefreshState,
        modifier = Modifier
            .fillMaxSize(),
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isLoading,
                state = pullRefreshState,
                threshold = pullToRefreshThreshHold
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .haze(
                    hazeState,
                    HazeDefaults.style(
                        backgroundColor = Colors.White,
                        tint = Colors.White.copy(.1f),
                        blurRadius = 20.dp,
                    )
                )
                .graphicsLayer {
                    translationY = pullRefreshState.distanceFraction * 50.dp.toPx()
                },
            state = listState
        ) {
            item(key = "SpaceTop") {
                Spacer(modifier = Modifier.size(innerPadding.calculateTopPadding()))
            }
            item(key = "TrendingTitle") {
                Text(
                    "Trending Podcasts", style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }
            item(key = "TrendingRow") {
                TrendingRow(
                    trendingRowState,
                    isRefreshing[MainViewModel.HomepageDataPart.Trending.value] ?: true,
                    placeHolderColor,
                    listTrending,
                    sharedTransitionScope,
                    animatedContentScope,
                    onTrendingClick
                )
            }
            item(key = "LiveTitle") {
                Text(
                    "Live Episodes", style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }
            item(key = "LiveRow") {
                LiveRow(
                    isRefreshing = isRefreshing[MainViewModel.HomepageDataPart.LiveEpisode.value]
                        ?: true,
                    liveEpisode = liveEpisode,
                    pagerState = pagerState,
                    onClick = onLiveItemClick
                )
            }
            item(key = "RecentTitle") {
                Text(
                    "Recent Podcasts", style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }
            item(key = "RecentRow") {
                RecentRow(
                    isRefreshing = isRefreshing[MainViewModel.HomepageDataPart.RecentFeed.value]
                        ?: true,
                    placeHolderColor,
                    recentFeeds,
                    sharedTransitionScope,
                    animatedContentScope,
                    recentFeedState,
                    onTrendingClick
                )
            }
            item(key = "FavTitle") {
                Text(
                    "Maybe you like", style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }

            items(if (isFavRefreshing) 50 else listFav.size) {
                FavouriteWidget(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    podcast = if (!isFavRefreshing) listFav[it] else Podcast.default,
                    isLoading = isFavRefreshing
                ) {
                    onFavClick(this)
                }
            }

            item(key = "SpaceBottom") {
                Spacer(modifier = Modifier.size(innerPadding.calculateBottomPadding()))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview(backgroundColor = 0xFFE1D5D5)
fun DiscoverPodcastsScreenPreview() {
    Box(modifier = Modifier.background(Colors.White)) {
        SharedTransitionScope {
            AnimatedContent(true, label = "") {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionScope,
                    LocalNavAnimatedVisibilityScope provides this
                ) {
                    if (it) {
                        DiscoverPodcastsScreen(
                            isRefreshing = emptyMap(),
                            onRefresh = {

                            },
                            hazeState = remember { HazeState() },
                            pullRefreshState = rememberPullToRefreshState(),
                            innerPadding = PaddingValues(),
                            listTrending = listOf(),
                            listFav = listOf(),
                            onTrendingClick = {

                            },
                            onFavClick = {

                            },
                            listState = rememberLazyListState(),
                            showLoading = true
                        )
                    } else {
                        Box { }
                    }
                }

            }
        }
    }
}