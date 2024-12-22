package tss.t.podcast.ui.screens.player.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tss.t.ads.MaxAdViewComposable
import tss.t.coreapi.models.Episode
import tss.t.podcast.ui.screens.podcastsdetail.toPx
import tss.t.podcast.ui.screens.podcastsdetail.widgets.EpisodeWidget
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import kotlin.math.abs

@Immutable
enum class SlideAreaState {
    Expanded,
    Hidden
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.SlideArea(
    playList: List<Episode>,
    state: SlideAreaState = SlideAreaState.Expanded,
    listState: LazyListState = rememberLazyListState(),
    draggableState: DraggableState = rememberDraggableState {},
    isDragInProgress: Boolean = false,
    dragDelta: Float = 0f,
    fling: Float = 0f,
    onStateChanged: (SlideAreaState) -> Unit = {},
    onSelected: Episode.() -> Unit = {},
) {
    val initSize = 680.dp.toPx()
    val anim = remember {
        Animatable(initSize)
    }
    var dragging by remember(isDragInProgress) {
        mutableStateOf(isDragInProgress)
    }
    val coroutineScope = rememberCoroutineScope()
    var slideAreaState by remember {
        mutableStateOf(state)
    }
    val overScroll = ScrollableDefaults.overscrollEffect()
    val flingBehavior = ScrollableDefaults.flingBehavior()
    var isAllowScrollListContent by remember {
        mutableStateOf(false)
    }
    var listContentBound by remember {
        mutableStateOf<Rect?>(null)
    }

    fun flingSlideAreaState(velocity: Float): SlideAreaState {
        if (anim.value == 0f && ((listState.canScrollForward && velocity > 0) || (listState.canScrollBackward && velocity < 0))) {
            coroutineScope.launch {
                listState.scroll(MutatePriority.UserInput) {
                    if (!velocity.isInfinite()) {
                        with(flingBehavior) {
                            this@scroll.performFling(-velocity)
                        }
                    }
                }
                if (!listState.canScrollForward || !listState.canScrollBackward) {
                    overScroll.applyToFling(Velocity(0f, velocity)) {
                        -it
                    }
                }
            }
            return state
        }
        val targetState = if (velocity >= 0) {
            if (anim.value >= -0.8 * initSize || velocity > 1000f) {
                SlideAreaState.Hidden
            } else {
                SlideAreaState.Expanded
            }
        } else {
            if (anim.value <= -0.4 * initSize || velocity < -1000f) {
                SlideAreaState.Expanded
            } else {
                SlideAreaState.Hidden
            }
        }
        return targetState
    }

    var dragPosition by remember {
        mutableFloatStateOf(0f)
    }
    val minPosition = 0f
    LaunchedEffect(key1 = dragDelta) {
        if (dragging) {
            if (((listState.canScrollForward && dragDelta < 0) || (listState.canScrollBackward && dragDelta > 0))
                && isAllowScrollListContent && slideAreaState == SlideAreaState.Expanded
                && anim.value == 0f
            ) {
                coroutineScope.launch {
                    listState.scroll(MutatePriority.UserInput) {
                        this.scrollBy(-dragDelta)
                    }
                }
                return@LaunchedEffect
            }
            if (!listState.canScrollForward) {
                overScroll.applyToScroll(
                    Offset(0f, dragDelta),
                    NestedScrollSource.UserInput
                ) { remainingOffset ->
                    val remainingDelta = remainingOffset.y
                    val newPosition = (dragPosition + remainingDelta)
                        .coerceIn(
                            minPosition,
                            initSize
                        )
                    // Calculate how much delta we have consumed
                    val consumed = newPosition - dragPosition
                    dragPosition = newPosition
                    Offset(0f, consumed)
                }
            }
            if (!listState.canScrollBackward
                && abs(dragDelta) > 0
                && !listState.isScrollInProgress
                && !overScroll.isInProgress
            ) {
                coroutineScope.launch {
                    anim.snapTo(anim.value - dragDelta)
                }
            }
        }
    }
    LaunchedEffect(state) {
        slideAreaState = state
    }
    LaunchedEffect(fling) {
        if (fling != 0f) {
            slideAreaState = flingSlideAreaState(fling)
        }
    }
    LaunchedEffect(slideAreaState, dragging) {
        if (slideAreaState == SlideAreaState.Hidden) {
            anim.animateTo(-initSize)
        } else {
            anim.animateTo(0f)
        }
        onStateChanged(slideAreaState)
    }
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                top = with(density) {
                    abs(anim.value).toDp()
                }
            )
            .background(
                Color(0xFFE9F3FE),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp
                )
            )
    ) {
        HorizontalDivider(
            color = Colors.NeutralDark.copy(0.3f),
            modifier = Modifier
                .padding(top = 20.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragging = true
                        listContentBound
                            ?.contains(it)
                            ?.let {
                                isAllowScrollListContent =
                                    it && (listState.canScrollForward || listState.canScrollBackward)
                            }
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        slideAreaState = flingSlideAreaState(velocity)
                    }
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "UP NEXT",
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            anim.animateTo(-initSize)
                        }
                    }
                    .padding(16.dp),
                style = TextStyles.Title6
            )
        }
        HorizontalDivider(
            color = Colors.NeutralDark.copy(0.3f),
        )
        MaxAdViewComposable(
            modifier = Modifier.padding(vertical = 4.dp),
            tsAnalytics = LocalAnalyticsScope.current!!
        )
        LazyColumn(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragging = true
                        listContentBound
                            ?.contains(it)
                            ?.let {
                                isAllowScrollListContent =
                                    it && (listState.canScrollForward || listState.canScrollBackward)
                            }
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        slideAreaState = flingSlideAreaState(velocity)
                    }
                )
                .overscroll(overScroll)
                .onGloballyPositioned {
                    listContentBound = it.boundsInParent()
                },
            userScrollEnabled = false,
            flingBehavior = flingBehavior,
            state = listState
        ) {
            item {
                Spacer(modifier = Modifier.size(20.dp))
            }
            items(playList.size) {
                EpisodeWidget(
                    episode = playList[it],
                    modifier = Modifier
                        .clickable(
                            enabled = !dragging && !listState.isScrollInProgress,
                            onClick = {
                                onSelected(playList[it])
                            }
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                )
                HorizontalDivider()
            }
            item {
                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}