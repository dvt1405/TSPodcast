package tss.t.podcast.ui.screens.discorver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.PositionalThreshold
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.discorver.widgets.FavouriteWidget
import tss.t.podcast.ui.screens.discorver.widgets.LiveEpisodeWidgets
import tss.t.podcast.ui.screens.discorver.widgets.TrendingWidget
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


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
    LaunchedEffect(showLoading, renderCount) {
        if (!showLoading) {
            pullRefreshState.animateToHidden()
        }
    }
    PullToRefreshBox(
        isRefreshing = showLoading,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier
            .fillMaxSize(),
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = showLoading,
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
                    isRefreshing = isRefreshing[MainViewModel.HomepageDataPart.LiveEpisode.value] ?: true,
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
                    isRefreshing = isRefreshing[MainViewModel.HomepageDataPart.RecentFeed.value] ?: true,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecentRow(
    isRefreshing: Boolean,
    placeHolderColor: Color,
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
        item { Spacer(modifier = Modifier.size(4.dp)) }
        if (isRefreshing) {
            items(20, key = { it }) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .aspectRatio(16f / 13)
                        .clip(RoundedCornerShape(10.dp))
                        .background(placeHolderColor, RoundedCornerShape(8.dp))
                )
            }
        } else {
            items(recentFeeds.size) {
                val item = recentFeeds[it]
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TrendingRow(
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
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .aspectRatio(16f / 13)
                        .clip(RoundedCornerShape(10.dp))
                        .background(placeHolderColor, RoundedCornerShape(8.dp))
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Indicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = PullToRefreshDefaults.containerColor,
    color: Color = PullToRefreshDefaults.indicatorColor,
    threshold: Dp = PositionalThreshold,
) {
    Box(
        modifier = modifier.pullToRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            containerColor = containerColor,
            threshold = threshold,
        ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isRefreshing,
            animationSpec = tween(durationMillis = CrossfadeDurationMs),
            label = ""
        ) { refreshing ->
            if (refreshing) {
                CircularProgressIndicator(
                    strokeWidth = StrokeWidth,
                    color = color,
                    modifier = Modifier.size(SpinnerSize),
                )
            } else {
                CircularArrowProgressIndicator(
                    progress = { state.distanceFraction },
                    color = color,
                )
            }
        }
    }
}

@Composable
fun CircularArrowProgressIndicator(
    progress: () -> Float,
    color: Color,
) {
    val path = remember { Path().apply { fillType = PathFillType.EvenOdd } }
    // TODO: Consider refactoring this sub-component utilizing Modifier.Node
    val targetAlpha by remember { derivedStateOf { if (progress() >= 1f) MaxAlpha else MinAlpha } }
    val alphaState = animateFloatAsState(targetValue = targetAlpha, animationSpec = AlphaTween)
    Canvas(
        Modifier
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(progress(), 0f..1f, 0)
            }
            .size(16.dp)
    ) {
        val values = ArrowValues(progress())
        val alpha = alphaState.value
        rotate(degrees = values.rotation) {
            val arcRadius = 5.5.dp.toPx() + 2.5.dp.toPx() / 2f
            val arcBounds = Rect(center = size.center, radius = arcRadius)
            drawCircularIndicator(color, alpha, values, arcBounds, 2.5.dp)
            drawArrow(path, arcBounds, color, alpha, values, StrokeWidth)
        }
    }
}

private fun DrawScope.drawCircularIndicator(
    color: Color,
    alpha: Float,
    values: ArrowValues,
    arcBounds: Rect,
    strokeWidth: Dp
) {
    drawArc(
        color = color,
        alpha = alpha,
        startAngle = values.startAngle,
        sweepAngle = values.endAngle - values.startAngle,
        useCenter = false,
        topLeft = arcBounds.topLeft,
        size = arcBounds.size,
        style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
    )
}


private const val MaxProgressArc = 0.8f
private const val CrossfadeDurationMs = 100

/** The default stroke width for [Indicator] */
private val StrokeWidth = 2.5.dp
private val ArcRadius = 5.5.dp
internal val SpinnerSize = 16.dp // (ArcRadius + PullRefreshIndicatorDefaults.StrokeWidth).times(2)
internal val SpinnerContainerSize = 40.dp
private val ArrowWidth = 10.dp
private val ArrowHeight = 5.dp

// Values taken from SwipeRefreshLayout
private const val MinAlpha = 0.3f
private const val MaxAlpha = 1f
private val AlphaTween = tween<Float>(300, easing = LinearEasing)

private fun DrawScope.drawArrow(
    arrow: Path,
    bounds: Rect,
    color: Color,
    alpha: Float,
    values: ArrowValues,
    strokeWidth: Dp,
) {
    arrow.reset()
    arrow.moveTo(0f, 0f) // Move to left corner
    // Line to tip of arrow
    arrow.lineTo(x = ArrowWidth.toPx() * values.scale / 2, y = ArrowHeight.toPx() * values.scale)
    arrow.lineTo(x = ArrowWidth.toPx() * values.scale, y = 0f) // Line to right corner

    val radius = min(bounds.width, bounds.height) / 2f
    val inset = ArrowWidth.toPx() * values.scale / 2f
    arrow.translate(
        Offset(x = radius + bounds.center.x - inset, y = bounds.center.y - strokeWidth.toPx())
    )
    rotate(degrees = values.endAngle - strokeWidth.toPx()) {
        drawPath(path = arrow, color = color, alpha = alpha, style = Stroke(strokeWidth.toPx()))
    }
}


@Immutable
private class ArrowValues(
    val rotation: Float,
    val startAngle: Float,
    val endAngle: Float,
    val scale: Float
)

private fun ArrowValues(progress: Float): ArrowValues {
    // Discard first 40% of progress. Scale remaining progress to full range between 0 and 100%.
    val adjustedPercent = max(min(1f, progress) - 0.4f, 0f) * 5 / 3
    // How far beyond the threshold pull has gone, as a percentage of the threshold.
    val overshootPercent = abs(progress) - 1.0f
    // Limit the overshoot to 200%. Linear between 0 and 200.
    val linearTension = overshootPercent.coerceIn(0f, 2f)
    // Non-linear tension. Increases with linearTension, but at a decreasing rate.
    val tensionPercent = linearTension - linearTension.pow(2) / 4

    // Calculations based on SwipeRefreshLayout specification.
    val endTrim = adjustedPercent * 0.8f
    val rotation = (-0.25f + 0.4f * adjustedPercent + tensionPercent) * 0.5f
    val startAngle = rotation * 360
    val endAngle = (rotation + endTrim) * 360
    val scale = min(1f, adjustedPercent)

    return ArrowValues(rotation, startAngle, endAngle, scale)
}
