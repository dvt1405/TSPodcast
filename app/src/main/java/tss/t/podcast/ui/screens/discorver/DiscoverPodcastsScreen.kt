package tss.t.podcast.ui.screens.discorver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.coreapi.models.TrendingPodcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.ui.screens.discorver.widgets.FavouriteWidget
import tss.t.podcast.ui.screens.discorver.widgets.TrendingWidget
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverPodcastsScreen(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    hazeState: HazeState,
    pullRefreshState: PullToRefreshState,
    innerPadding: PaddingValues,
    listTrending: List<TrendingPodcast>,
    listFav: List<TrendingPodcast>,
    onTrendingClick: TrendingPodcast.() -> Unit = {},
    onFavClick: TrendingPodcast.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    trendingRowState: LazyListState = rememberLazyListState(),
    renderCount: Int = 0
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current!!
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val scaleFraction = {
        if (isRefreshing) 1f
        else LinearOutSlowInEasing.transform(pullRefreshState.distanceFraction).coerceIn(0f, 1f)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteTransition")
    val animationSpec = infiniteRepeatable<Float>(
        animation = tween(durationMillis = 1700, delayMillis = 200),
        repeatMode = RepeatMode.Restart
    )
    val highlightProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = animationSpec, label = "FloatAnimation",
    ).value

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

    val pullToRefreshThreshHold = 50.dp + innerPadding.calculateTopPadding()
    PullToRefreshBox(
        isRefreshing = if (renderCount == 0) false else isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier
            .fillMaxSize(),
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
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
                LazyRow(
                    state = trendingRowState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(modifier = Modifier.size(4.dp)) }
                    if (isRefreshing) {
                        items(20, key = { it }) {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .aspectRatio(10f / 16)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(placeHolderColor, RoundedCornerShape(20.dp))
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

            items(if (isRefreshing) 50 else listFav.size) {
                FavouriteWidget(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    podcast = if (!isRefreshing) listFav[it] else TrendingPodcast.default,
                    isLoading = isRefreshing
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
                            isRefreshing = true,
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
                            listState = rememberLazyListState()
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
    values: tss.t.podcast.ui.screens.discorver.ArrowValues,
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
    values: tss.t.podcast.ui.screens.discorver.ArrowValues,
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

private fun ArrowValues(progress: Float): tss.t.podcast.ui.screens.discorver.ArrowValues {
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
