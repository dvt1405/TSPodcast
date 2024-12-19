@file:OptIn(ExperimentalSharedTransitionApi::class)

package tss.t.podcast.ui.screens.podcastsdetail

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import tss.t.ads.MaxAdViewComposable
import tss.t.ads.MaxTemplateNativeAdViewComposable
import tss.t.ads.MaxTemplateNativeAdViewComposableLoader
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.SharedElementKey
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.main.nonSpatialExpressiveSpring
import tss.t.podcast.ui.screens.main.podcastDetailBoundsTransform
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.podcastsdetail.widgets.EpisodeWidget
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSPopup
import tss.t.sharedlibrary.ui.widget.ext.linkify
import tss.t.sharedlibrary.utils.imageRequestBuilder
import tss.t.sharedresources.R
import tss.t.sharedresources.SharedConstants

internal val dividerBrush = Brush.linearGradient(
    listOf(
        Colors.Primary10,
        Colors.Secondary,
        Colors.Primary10
    )
)

internal val coverRounded = RoundedCornerShape(20.dp)
private const val ITEM_KEY_COVER = "Cover"
private const val ITEM_KEY_TITLE = "Title"
private const val ITEM_KEY_BOTTOM_NAVIGATION = "BottomNavigation"
private const val ITEM_AD_BANNER = "AdBanner"

@Composable
fun PodcastDetailScreen(
    navHost: NavHostController,
    podcast: Podcast = Podcast.default,
    playList: List<Episode> = emptyList(),
    sharedElementKey: String? = null,
    mainViewModel: MainViewModel = viewModel<MainViewModel>(),
    podcastViewModel: PodcastViewModel = viewModel<PodcastViewModel>(),
    playerViewmodel: PlayerViewModel = viewModel<PlayerViewModel>(LocalViewModelStoreOwner.current!!)
) {
    LaunchedEffect(podcast, playList) {
        if (playList.isNotEmpty()) {
            podcastViewModel.setPodcastAndEpisodes(podcast, playList)
        }
    }

    DisposableEffect(Unit) {
        podcastViewModel.onRestoreState()
        onDispose {
            podcastViewModel.onSavedState()
        }
    }
    val coroutineScope = rememberCoroutineScope()

    val uiState by podcastViewModel.uiState.collectAsState()
    val showErrorDialog = remember(uiState) {
        uiState is PodcastViewModel.PodcastUIState.Error
    }
    val errorException = remember(uiState) {
        (uiState as? PodcastViewModel.PodcastUIState.Error)?.exception?.message
    }
    val playerUIState by playerViewmodel.playerControlState.collectAsState()
    if (showErrorDialog) {
        Dialog(onDismissRequest = {
            podcastViewModel.dismissDialog()
        }) {
            TSPopup(
                title = stringResource(R.string.dialog_error_title),
                contentText = errorException ?: "",
                positiveText = stringResource(R.string.dialog_positive_btn_title),
                onPositiveButtonClick = {
                    podcastViewModel.getEpisodes(podcast)
                }
            )
        }
    }
    val listItems = remember(uiState) {
        if (uiState is PodcastViewModel.PodcastUIState.Success) {
            (uiState as PodcastViewModel.PodcastUIState.Success).episodes
        } else {
            emptyList()
        }
    }
    val renderItemList = remember(uiState) {
        (uiState as? PodcastViewModel.PodcastUIState.Success)?.listRenderItems ?: emptyList()
    }
    PodcastDetailScreen(
        podcast = podcast,
        sharedElementKey = sharedElementKey,
        listItems = listItems,
        renderItemList = renderItemList,
        onBackPress = { navHost.popBackStack() },
        onItemClick = {
            val episode = this
            coroutineScope.launch {
                playerViewmodel.playerEpisode(
                    episode = episode,
                    podcast = uiState.podcast,
                    listItem = listItems
                )
                navHost.navigate(TSRouter.Player.route) {
                    restoreState = true
                }
            }
        },
        currentMediaItem = playerUIState.currentMediaItem,
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
    renderItemList: List<Any> = emptyList<Any>(),
    scrollState: LazyListState = rememberLazyListState(),
    currentMediaItem: MediaItem? = null,
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

    val shareElementId = remember(sharedElementKey) {
        if (sharedElementKey.isNullOrEmpty()) {
            "${podcast.id}"
        } else {
            "${sharedElementKey}_${podcast.id}"
        }
    }

    var showTopBar by remember {
        mutableStateOf(false)
    }
    val alpha by animateFloatAsState(
        if (showTopBar) {
            1f
        } else {
            0f
        }, label = ""
    )
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.firstOrNull() }
            .collect {
                val item = it ?: return@collect
                showTopBar = (item.key == ITEM_KEY_COVER
                        && item.offset > ExpandedImageSize.roundToPx())
                        || item.key === ITEM_KEY_TITLE
                        || item.index >= 3
            }
    }
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .clip(coverRounded)
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
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(roundedCornerAnim)),
                    boundsTransform = podcastDetailBoundsTransform,
                    exit = fadeOut(nonSpatialExpressiveSpring()),
                    enter = fadeIn(nonSpatialExpressiveSpring()),
                )
                .fillMaxSize(),
        ) {
            PodcastDetailTopAppBar(podcast) {
                onBackPress()
            }
            Header(podcast = podcast)
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp)
                    .defaultMinSize(minHeight = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = podcast.title ?: "",
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
                scrollState = scrollState,
                podcast = podcast,
                shareElementId = shareElementId,
                animatedContentScope = animatedContentScope,
                listItems = listItems,
                renderItemList = renderItemList,
                currentMediaItem = currentMediaItem,
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun PodcastDetailBody(
    scrollState: LazyListState,
    podcast: Podcast,
    shareElementId: String,
    animatedContentScope: AnimatedVisibilityScope,
    listItems: List<Episode>,
    renderItemList: List<Any>,
    currentMediaItem: MediaItem? = null,
    onItemClick: Episode.() -> Unit
) {
    val sharedTransition = LocalSharedTransitionScope.current!!
    val isLoading = remember(listItems) {
        listItems.isEmpty()
    }

    val itemCount = remember(renderItemList) {
        if (renderItemList.isEmpty()) {
            20
        } else {
            renderItemList.size
        }
    }

    val isPlayingMedia = remember(currentMediaItem) {
        currentMediaItem != null
    }
    with(sharedTransition) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp)
                .animateContentSize(),
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

            item(key = ITEM_KEY_COVER) {
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
                            .clip(coverRounded)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(
                                    SharedElementKey(
                                        id = shareElementId,
                                        podcast = podcast,
                                        type = SharedElementKey.Type.Image
                                    )
                                ),
                                animatedVisibilityScope = animatedContentScope,
                                clipInOverlayDuringTransition = OverlayClip(coverRounded),
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

            item(key = ITEM_AD_BANNER) {
                MaxAdViewComposable(
                    modifier = Modifier
                        .background(Colors.White)
                        .padding(vertical = 4.dp),
                    tsAnalytics = LocalAnalyticsScope.current!!
                )
            }

            item(key = ITEM_KEY_TITLE) {
                Column(modifier = Modifier.background(Color.White)) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = podcast.title ?: "",
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
                    if (!podcast.description.isNullOrEmpty()) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = HtmlCompat.fromHtml(
                                /* source = */ podcast.description ?: "",
                                /* flags = */ HtmlCompat.FROM_HTML_MODE_COMPACT
                            ).linkify(
                                TextStyles.Body4.copy(
                                    color = Colors.ButtonColor,
                                    textDecoration = TextDecoration.Underline
                                ).toSpanStyle()
                            ),
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
            }

            items(count = itemCount, key = {
                when {
                    renderItemList.isEmpty() -> {
                        it
                    }

                    renderItemList[it] is Int || renderItemList[it] is MaxTemplateNativeAdViewComposableLoader -> {
                        "NativeAd-$it"
                    }

                    renderItemList[it] is Episode -> {
                        (renderItemList[it] as Episode).id
                    }

                    else -> {
                        it
                    }
                }
            }) {
                val item = if (renderItemList.isEmpty()) {
                    null
                } else {
                    renderItemList[it]
                }
                if (item is MaxTemplateNativeAdViewComposableLoader) {
                    MaxTemplateNativeAdViewComposable(item)
                } else {
                    EpisodeWidget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item as Episode) }
                            .background(Color.White)
                            .padding(16.dp)
                            .drawWithContent {
                                val contentPadding = 16.dp.toPx()
                                val dividerSize = 1.dp.toPx()
                                drawContent()
                                drawLine(
                                    dividerBrush,
                                    strokeWidth = dividerSize,
                                    start = Offset(
                                        0f,
                                        size.height - dividerSize + contentPadding
                                    ),
                                    end = Offset(
                                        size.width,
                                        size.height - dividerSize + contentPadding
                                    )
                                )
                            },
                        episode = item as? Episode,
                        isLoading = isLoading
                    )
                }
            }
            item(key = ITEM_KEY_BOTTOM_NAVIGATION) {
                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .size(20.dp)
                )
                if (isPlayingMedia) {
                    Spacer(Modifier.size(60.dp))
                }
            }
        }
    }
}

@Composable
@Preview
private fun PodcastDetailScreenPreview() {
    SharedTransitionLayout {
        AnimatedContent(true, label = "") {
            if (it)
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
private fun Header(podcast: Podcast) {
    val brushColors = listOf(Color(0xDCF9FFFF), Color(0xFFDCF9FF))

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
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader(context)
    }
    var imageBitmap: ImageBitmap? by remember {
        mutableStateOf(null)
    }

    LaunchedEffect(podcast) {
        imageBitmap = imageLoader.execute(
            imageRequestBuilder(context)
                .diskCacheKey(podcast.image.ifEmpty { podcast.feedImage })
                .data(podcast.image.ifEmpty { podcast.feedImage })
                .build()
        ).drawable
            ?.toBitmapOrNull()
            ?.asImageBitmap()
    }
    Spacer(
        modifier = Modifier
            .height(GradientScroll + 50.dp)
            .fillMaxWidth()
            .blur(50.dp, BlurredEdgeTreatment.Unbounded)
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
                    imageBitmap?.let { img ->
                        drawIntoCanvas {
                            it.drawImageRect(
                                image = img,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                paint = Paint().apply {
                                    alpha = 0.7f
                                }

                            )
                        }
                    }
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
