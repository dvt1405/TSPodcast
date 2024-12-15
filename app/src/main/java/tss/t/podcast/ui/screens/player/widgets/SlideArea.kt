package tss.t.podcast.ui.screens.player.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tss.t.ads.MaxAdViewComposable
import tss.t.coreapi.models.Episode
import tss.t.podcast.ui.screens.podcastsdetail.toPx
import tss.t.podcast.ui.screens.podcastsdetail.widgets.EpisodeWidget
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

@Immutable
enum class SlideAreaState {
    Expanded,
    Hidden
}

@Composable
fun BoxScope.SlideArea(
    playList: List<Episode>,
    state: SlideAreaState = SlideAreaState.Expanded,
    listState: LazyListState = rememberLazyListState(),
    draggableState: DraggableState = rememberDraggableState { },
    dragDelta: Float = 0f,
    onStateChanged: (SlideAreaState) -> Unit = {},
    onSelected: Episode.() -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val initSize = (screenWidth + 240).dp.toPx()
    val anim = remember {
        Animatable(0f)
    }
    var dragging by remember {
        mutableStateOf(false)
    }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(key1 = dragDelta) {
        if (dragging) {
            anim.snapTo(anim.value + dragDelta)
        }
    }
    var slideAreaState by remember {
        mutableStateOf(state)
    }
    LaunchedEffect(state) {
        slideAreaState = state
    }
    LaunchedEffect(slideAreaState, dragging) {
        if (slideAreaState == SlideAreaState.Hidden) {
            anim.animateTo(0f)
            listState.scrollToItem(0, 0)
        } else {
            anim.animateTo(-initSize)
        }
        onStateChanged(slideAreaState)
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                translationY = anim.value
                    .coerceAtMost(48.dp.toPx())
                    .coerceAtLeast(-initSize) + initSize
            }
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
            modifier = Modifier.padding(top = 20.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    draggableState,
                    Orientation.Vertical,
                    onDragStarted = {
                        dragging = true
                    },
                    onDragStopped = {
                        dragging = false
                        slideAreaState = if (it >= 0) {
                            if (anim.value >= -0.8 * initSize || it > 1000f) {
                                SlideAreaState.Hidden
                            } else {
                                SlideAreaState.Expanded
                            }
                        } else {
                            if (anim.value <= -0.4 * initSize || it < -1000f) {
                                SlideAreaState.Expanded
                            } else {
                                SlideAreaState.Hidden
                            }
                        }
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
                .fillMaxWidth(),
            userScrollEnabled = slideAreaState != SlideAreaState.Hidden,
            state = listState
        ) {
            item {
                Spacer(modifier = Modifier.size(20.dp))
            }
            items(playList.size) {
                EpisodeWidget(
                    episode = playList[it],
                    modifier = Modifier.clickable {
                        onSelected(playList[it])
                    }.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                )
                HorizontalDivider()
            }
        }
    }
}
