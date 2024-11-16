@file:OptIn(ExperimentalSharedTransitionApi::class)

package tss.t.podcast.ui.screens.podcastsdetail

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.SharedElementKey
import tss.t.podcast.ui.navigations.TSNavigators
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.main.nonSpatialExpressiveSpring
import tss.t.podcast.ui.screens.main.podcastDetailBoundsTransform
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.podcastsdetail.widgets.EpisodeWidget
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSPopup
import tss.t.sharedresources.SharedConstants
import tss.t.sharedresources.R
import kotlin.math.max
import kotlin.math.min

@Composable
fun PodcastDetailScreen(
    podcast: Podcast = Podcast.default,
    playList: List<Episode> = emptyList(),
    sharedElementKey: String? = null,
    mainViewModel: MainViewModel = viewModel<MainViewModel>(),
    podcastViewModel: PodcastViewModel = viewModel<PodcastViewModel>(),
    playerViewmodel: PlayerViewModel = viewModel<PlayerViewModel>()
) {
    LaunchedEffect(podcast, playList) {
        if (playList.isNotEmpty()) {
            mainViewModel.setCurrentPodcast(podcast)
            podcastViewModel.setPodcastAndEpisodes(podcast, playList)
        } else {
            podcastViewModel.getEpisodes("${podcast.id}")
        }
    }

    DisposableEffect(Unit) {
        podcastViewModel.onRestoreState()
        onDispose {
            podcastViewModel.onSavedState()
        }
    }

    val uiState by podcastViewModel.uiState.collectAsState()
    if (uiState is PodcastViewModel.PodcastUIState.Error) {
        val message = (uiState as PodcastViewModel.PodcastUIState.Error).exception.message
        Dialog(onDismissRequest = {
            podcastViewModel.dismissDialog()
        }) {
            TSPopup(
                title = "Lỗi",
                contentText = message ?: "",
                positiveText = "Tải lại",
                onPositiveButtonClick = {
                    podcastViewModel.getEpisodes("${podcast.id}")
                }
            )
        }
    }

    PodcastDetailScreen(
        podcast,
        sharedElementKey,
        listItems = if (uiState is PodcastViewModel.PodcastUIState.Success) {
            (uiState as PodcastViewModel.PodcastUIState.Success).data
        } else {
            emptyList()
        },
        onBackPress = {
            mainViewModel.popBackStack()

        },
        onItemClick = {
            TSNavigators.navigateTo(
                TSNavigators.Player(
                    item = this,
                    podcast = podcast,
                    playList = (uiState as PodcastViewModel.PodcastUIState.Success).data
                )
            )
        },
        scrollState = uiState.lazyListState ?: rememberLazyListState().also {
            podcastViewModel.initListState(it)
        }
    )
}

@Composable
private fun PodcastDetailScreen(
    podcast: Podcast = Podcast.default,
    sharedElementKey: String? = null,
    listItems: List<Episode> = emptyList(),
    scrollState: LazyListState = rememberLazyListState(),
    onBackPress: () -> Unit = {},
    onItemClick: Episode.() -> Unit = {}
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current!!
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val roundedCornerAnim by animatedContentScope.transition
        .animateDp(label = "roundedCorner") { enterExit: EnterExitState ->
            when (enterExit) {
                EnterExitState.PreEnter -> 0.dp
                EnterExitState.Visible -> 0.dp
                EnterExitState.PostExit -> 20.dp
            }
        }


    val coverRoundedCornerAnim by animatedContentScope.transition
        .animateDp(label = "roundedCorner") { enterExit: EnterExitState ->
            when (enterExit) {
                EnterExitState.PreEnter -> 20.dp
                EnterExitState.Visible -> 20.dp
                EnterExitState.PostExit -> 20.dp
            }
        }


    val hazeState = remember { HazeState() }

    val shareElementId = if (sharedElementKey.isNullOrEmpty()) {
        "${podcast.id}"
    } else {
        "${sharedElementKey}_${podcast.id}"
    }
    val layoutInfo by remember { derivedStateOf { scrollState.layoutInfo } }
    val firstItemIndex by remember { derivedStateOf { scrollState.firstVisibleItemIndex } }
    val alpha by animateFloatAsState(
        if (
            (layoutInfo.visibleItemsInfo.firstOrNull()?.key == "Cover"
                    && layoutInfo.visibleItemsInfo.firstOrNull()!!.offset > ExpandedImageSize.roundToPx())
            || (layoutInfo.visibleItemsInfo.firstOrNull()?.key == "Title")
            || firstItemIndex >= 3
        ) {
            1f
        } else {
            0f
        }, label = ""
    )
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(roundedCornerAnim))
                .skipToLookaheadSize()
                .sharedBounds(
                    rememberSharedContentState(
                        key = SharedElementKey(
                            id = shareElementId,
                            podcast = podcast,
                            type = SharedElementKey.Type.Background
                        )
                    ),
                    animatedVisibilityScope = animatedContentScope,
                    clipInOverlayDuringTransition =
                    OverlayClip(RoundedCornerShape(roundedCornerAnim)),
                    boundsTransform = podcastDetailBoundsTransform,
                    exit = fadeOut(nonSpatialExpressiveSpring()),
                    enter = fadeIn(nonSpatialExpressiveSpring()),
                )
                .fillMaxSize(),
        ) {
            PodcastDetailTopAppBar(podcast) {
                onBackPress()
            }
            Header(podcastID = podcast.id)
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp)
                    .defaultMinSize(minHeight = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = podcast.title,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyles.Title4.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )
            }
            PodcastDetailBody(
                hazeState,
                scrollState,
                podcast,
                shareElementId,
                animatedContentScope,
                coverRoundedCornerAnim,
                listItems,
                onItemClick
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.PodcastDetailBody(
    hazeState: HazeState,
    scrollState: LazyListState,
    podcast: Podcast,
    shareElementId: String,
    animatedContentScope: AnimatedVisibilityScope,
    coverRoundedCornerAnim: Dp,
    listItems: List<Episode>,
    onItemClick: Episode.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 100.dp)
            .haze(hazeState),
        state = scrollState,
    ) {
        item(key = "PaddingTop") {
            Spacer(
                modifier = Modifier
                    .size(
                        GradientScroll - 100.dp - 75.dp
                    )
            )
        }

        item(key = "Cover") {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(podcast.image)
                        .addHeader(
                            SharedConstants.USER_AGENT_KEY,
                            SharedConstants.USER_AGENT_WEB_VALUE
                        )
                        .placeholder(R.drawable.image_loader_place_holder)
                        .error(R.drawable.image_loader_place_holder)
                        .crossfade(true)
                        .build(),
                    contentDescription = podcast.title,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(ExpandedImageSize)
                        .zIndex(2f)
                        .clip(RoundedCornerShape(20.dp))
                        .sharedBounds(
                            rememberSharedContentState(
                                SharedElementKey(
                                    id = shareElementId,
                                    podcast = podcast,
                                    type = SharedElementKey.Type.Image
                                )
                            ),
                            animatedContentScope,
                            clipInOverlayDuringTransition = OverlayClip(
                                RoundedCornerShape(coverRoundedCornerAnim)
                            ),
                        ),
                    contentScale = ContentScale.Crop,
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ExpandedImageSize / 2)
                        .background(Color.White)
                        .align(Alignment.BottomStart)
                )
            }

        }

        item(key = "Title") {
            Column(modifier = Modifier.background(Color.White)) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = podcast.title,
                    style = TextStyles.Title6,
                    modifier = HzPadding
                        .sharedBounds(
                            rememberSharedContentState(
                                key = SharedElementKey(
                                    id = shareElementId,
                                    podcast = podcast,
                                    type = SharedElementKey.Type.Title
                                )
                            ),
                            animatedVisibilityScope = animatedContentScope,
                            boundsTransform = podcastDetailBoundsTransform
                        )
                        .skipToLookaheadSize()
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = buildAnnotatedString {
                        append(
                            HtmlCompat.fromHtml(
                                podcast.description ?: "",
                                HtmlCompat.FROM_HTML_MODE_COMPACT
                            )
                        )
                    },
                    style = TextStyles.Body4,
                    color = Colors.TextDescriptionColor,
                    modifier = HzPadding
                        .sharedBounds(
                            rememberSharedContentState(
                                key = SharedElementKey(
                                    id = shareElementId,
                                    podcast = podcast,
                                    type = SharedElementKey.Type.Description
                                )
                            ),
                            animatedVisibilityScope = animatedContentScope,
                            boundsTransform = podcastDetailBoundsTransform
                        )
                        .skipToLookaheadSize()
                        .fillMaxWidth()
                )
            }
        }

        items(count = if (listItems.isEmpty()) {
            20
        } else {
            listItems.size
        }, key = {
            if (listItems.isEmpty()) it else listItems[it].id
        }) {
            val item = if (listItems.isEmpty()) {
                null
            } else {
                listItems[it]
            }
            EpisodeWidget(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(16.dp),
                episode = item,
                onClick = onItemClick,
                isLoading = listItems.isEmpty()
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            ) {
                drawLine(
                    Brush.linearGradient(
                        listOf(
                            Colors.Primary10,
                            Colors.Secondary,
                            Colors.Primary10
                        )
                    ),
                    strokeWidth = 1.dp.toPx(),
                    start = Offset(0f, 1.dp.toPx()),
                    end = Offset(size.width, 1.dp.toPx())
                )
            }
        }
        item(key = "BottomNavigation") {
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(20.dp)
            )
        }
    }
}

@Composable
@Preview
private fun PodcastDetailScreenPreview() {
    SharedTransitionLayout {
        AnimatedContent(true, label = "") {
            if (it) {
            }
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalNavAnimatedVisibilityScope provides this@AnimatedContent
            ) {
                PodcastDetailScreen(
                    onBackPress = {

                    }
                )
            }
        }
    }
}

@Composable
fun PodcastDetailTopAppBar(
    podcast: Podcast,
    onBackPress: () -> Unit = {}
) {
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val interactiveSource = remember { MutableInteractionSource() }
    val pressed by interactiveSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.8f else 1f, label = "scale",
        animationSpec = spring(stiffness = 300f)
    ) {
    }
    with(animatedContentScope) {
        Box(
            Modifier
                .zIndex(2f)
                .statusBarsPadding()
                .padding(start = 16.dp)
                .animateEnterExit(
                    enter = scaleIn(tween(300, delayMillis = 300)),
                    exit = scaleOut(tween(50))
                )
                .scale(scale)
                .size(40.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(0.3f))
                .clickable(
                    interactionSource = interactiveSource,
                    indication = null,
                    onClick = onBackPress,
                    role = Role.Button,
                    onClickLabel = "Back"
                )
                .padding(12.dp)
        ) {
            Image(
                Icons.Rounded.ArrowBack,
                "Back",
                modifier = Modifier,
                colorFilter = ColorFilter.tint(Colors.White)
            )
        }
    }
}


@Composable
private fun CollapsingImageLayout(
    collapseFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        check(measurables.size == 1)

        val collapseFraction = collapseFractionProvider()

        val imageMaxSize = min(ExpandedImageSize.roundToPx(), constraints.maxWidth)
        val imageMinSize = max(CollapsedImageSize.roundToPx(), constraints.minWidth)
        val imageWidth = lerp(imageMaxSize, imageMinSize, collapseFraction)
        val imagePlaceable = measurables[0].measure(Constraints.fixed(imageWidth, imageWidth))

        val imageY = lerp(220.dp, 100.dp, collapseFraction).roundToPx()
        val imageX = 16.dp.toPx().toInt()
        layout(
            width = constraints.maxWidth,
            height = imageY + imageWidth
        ) {
            imagePlaceable.placeRelative(imageX, imageY)
        }
    }
}

@Composable
private fun Header(podcastID: Long) {
    val brushColors = listOf(Color(0xff7057f5), Color(0xff86f7fa))

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val targetOffset = with(LocalDensity.current) {
        1000.dp.toPx()
    }
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = targetOffset,
        animationSpec = infiniteRepeatable(
            tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )
    Spacer(
        modifier = Modifier
            .height(GradientScroll)
            .fillMaxWidth()
            .blur(40.dp)
            .drawWithCache {
                val brushSize = 400f
                val brush = Brush.linearGradient(
                    colors = brushColors,
                    start = Offset(offset, offset),
                    end = Offset(offset + brushSize, offset + brushSize),
                    tileMode = TileMode.Mirror
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
    )
}

private val BottomBarHeight = 56.dp
private val TitleHeight = 128.dp
private val GradientScroll = 280.dp
private val ImageOverlap = 115.dp
private val MinTitleOffset = 56.dp
private val MinImageOffset = 40.dp
private val MaxTitleOffset = MinTitleOffset + GradientScroll
private val ExpandedImageSize = 150.dp
private val CollapsedImageSize = 100.dp
private val HzPadding = Modifier.padding(horizontal = 24.dp)

fun Dp.roundToPx(): Int {
    val px = toPx()
    return if (px.isInfinite()) Constraints.Infinity else px.fastRoundToInt()
}

fun Dp.toPx(): Float = value * Resources.getSystem().displayMetrics.density
