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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.ads.MaxAdViewComposable
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.R
import tss.t.podcast.ui.screens.discorver.components.LiveRow
import tss.t.podcast.ui.screens.discorver.components.RecentRow
import tss.t.podcast.ui.screens.discorver.components.TrendingRow
import tss.t.podcast.ui.screens.discorver.widgets.FavouriteWidget
import tss.t.podcast.ui.screens.discorver.widgets.Indicator
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.uimodels.main.HomepageDataPart
import tss.t.podcast.ui.screens.uimodels.main.UIState
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

private const val ITEM_RECENT_TITLE = "RecentTitle"
private const val ITEM_RECENT_ROW = "RecentRow"
private const val ITEM_LIVE_ROW = "LiveRow"
private const val ITEM_LIVE_TITLE = "LiveTitle"
private const val ITEM_FAV_TITLE = "FavTitle"
private const val ITEM_SPACE_BOTTOM = "SpaceBottom"
private const val ITEM_AD_BANNER_1 = "AdBanner1"
private const val ITEM_TRENDING_ROW = "TrendingRow"
private const val ITEM_TRENDING_TITLE = "TrendingTitle"
private const val ITEM_SPACE_TOP = "SpaceTop"
private const val ITEM_FAV_CATEGORIES = "FavouritesCategories"

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun DiscoverPodcastsScreen(
    uiState: UIState = UIState(),
    playerControlState: PlayerViewModel.PlayerControlState,
    hazeState: HazeState,
    innerPadding: PaddingValues,
    showLoading: Boolean = true,
    onRefresh: () -> Unit,
    onTrendingClick: Podcast.() -> Unit = {},
    onFavClick: Podcast.() -> Unit = {},
    onLiveItemClick: LiveEpisode.() -> Unit = {},
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current!!
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteTransition")
    val parentListState = rememberLazyListState()
    val trendingRowState = rememberLazyListState()
    val recentFeedState = rememberLazyListState()
    val pagerState = rememberPagerState { uiState.liveEpisode.size }
    val currentMediaItem = remember(playerControlState.currentMediaItem?.mediaId) {
        playerControlState.currentMediaItem
    }

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

    val isFavRefreshing = remember(key1 = uiState.isDataPartLoading[HomepageDataPart.Favourite.value]) {
        uiState.isDataPartLoading[HomepageDataPart.Favourite.value] ?: true
    }

    val pullToRefreshThreshHold = remember(innerPadding.calculateTopPadding()) {
        50.dp + innerPadding.calculateTopPadding()
    }
    var isLoading by remember {
        mutableStateOf(showLoading)
    }
    val pullRefreshState: PullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(showLoading) {
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
            state = parentListState
        ) {
            item(key = ITEM_SPACE_TOP) {
                Spacer(modifier = Modifier.size(innerPadding.calculateTopPadding()))
            }
            item(key = ITEM_TRENDING_TITLE) {
                Text(
                    stringResource(R.string.trending_podcast_title), style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }
            item(key = ITEM_TRENDING_ROW) {
                TrendingRow(
                    trendingRowState = trendingRowState,
                    isRefreshing = uiState.isDataPartLoading[HomepageDataPart.Trending.value]
                        ?: true,
                    placeHolderColor = placeHolderColor,
                    listTrending = uiState.listTrending,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onTrendingClick = onTrendingClick
                )
            }
            item(key = ITEM_AD_BANNER_1) {
                MaxAdViewComposable(
                    tsAnalytics = LocalAnalyticsScope.current!!
                )
            }
            item(key = ITEM_LIVE_TITLE) {
                Text(
                    stringResource(R.string.live_podcast_title), style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }
            item(key = ITEM_LIVE_ROW) {
                LiveRow(
                    isRefreshing = uiState.isDataPartLoading[HomepageDataPart.LiveEpisode.value]
                        ?: true,
                    liveEpisode = uiState.liveEpisode,
                    pagerState = pagerState,
                    onClick = onLiveItemClick
                )
            }
            item(key = ITEM_RECENT_TITLE) {
                Text(
                    stringResource(R.string.recent_podcast_title), style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }
            item(key = ITEM_RECENT_ROW) {
                RecentRow(
                    isRefreshing = uiState.isDataPartLoading[HomepageDataPart.RecentFeed.value]
                        ?: true,
                    placeHolderColor,
                    uiState.recentFeeds,
                    sharedTransitionScope,
                    animatedContentScope,
                    recentFeedState,
                    onTrendingClick
                )
            }
            item(key = ITEM_FAV_TITLE) {
                Text(
                    stringResource(R.string.fav_podcast_title), style = TextStyles.Title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
            }

            items(if (isFavRefreshing) 50 else uiState.listFav.size,
                key = {
                    if (isFavRefreshing) {
                        ITEM_FAV_CATEGORIES
                            .plus("Loading")
                            .plus(it)
                    } else {
                        uiState.listFav[it].id
                    }
                }) {
                FavouriteWidget(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    podcast = if (!isFavRefreshing) uiState.listFav[it] else Podcast.default,
                    isLoading = isFavRefreshing
                ) {
                    onFavClick(this)
                }
                if (it == 1 && !isFavRefreshing) {
                    Spacer(Modifier.size(4.dp))
                    MaxAdViewComposable(
                        tsAnalytics = LocalAnalyticsScope.current!!
                    )
                }
            }

            item(key = ITEM_SPACE_BOTTOM) {
                Spacer(modifier = Modifier.size(innerPadding.calculateBottomPadding()))
                if (currentMediaItem != null) {
                    Spacer(modifier = Modifier.size(86.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
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
                            hazeState = remember { HazeState() },
                            innerPadding = PaddingValues(),
                            showLoading = true,
                            onRefresh = {

                            },
                            onTrendingClick = {

                            },
                            onFavClick = {

                            },
                            uiState = UIState(),
                            playerControlState = PlayerViewModel.PlayerControlState()
                        )
                    } else {
                        Box { }
                    }
                }

            }
        }
    }
}