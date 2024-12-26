package tss.t.featureradio.ui

import android.content.res.Configuration
import android.view.View
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import tss.t.coreapi.models.TSDataState
import tss.t.coreradio.models.RadioChannel
import tss.t.featureradio.models.RadioUISate
import tss.t.featureradio.ui.widget.RadioChannelItem
import tss.t.featureradio.ui.widget.RadioChannelItemShimmer
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    hazeState: HazeState,
    radioUISate: TSDataState<RadioUISate>,
    currentMediaItem: MediaItem? = null,
    isMediaPlaying: Boolean,
    isMediaLoading: Boolean,
    listState: LazyListState = rememberLazyListState(),
    onClick: (RadioChannel) -> Unit = {},
    onPlay: (RadioChannel) -> Unit = {},
    onPause: () -> Unit = {}
) {
    val isLoading = remember(radioUISate) {
        radioUISate is TSDataState.Loading
    }
    val listRadio = remember(radioUISate) {
        radioUISate.data?.listRadio ?: emptyList()
    }
    val orientation = LocalConfiguration.current.orientation
    val layoutDirection = LocalConfiguration.current.layoutDirection
    LazyColumn(
        modifier = modifier
            .haze(hazeState)
            .animateContentSize(),
        state = listState
    ) {
        item("SpaceTop") {
            Spacer(
                Modifier.size(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        contentPadding.calculateTopPadding()
                    } else {
                        contentPadding.calculateStartPadding(
                            if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                                LayoutDirection.Ltr
                            } else {
                                LayoutDirection.Rtl
                            }
                        )
                    }
                )
            )
        }
        if (isLoading) {
            items(20) {
                RadioChannelItemShimmer(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()
            }
            return@LazyColumn
        }
        items(listRadio, key = {
            it.channelId
        }) { radioChannel ->
            RadioChannelItem(
                channel = radioChannel,
                currentMediaItem = currentMediaItem,
                isMediaLoading = isMediaLoading,
                isMediaPlaying = isMediaPlaying,
                modifier = Modifier
                    .clickable(onClick = { onClick(radioChannel) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                onPlay = {
                    onPlay(radioChannel)
                },
                onPause = {
                    onPause()
                }
            )
            HorizontalDivider()
        }
        item("SpaceBottom") {
            Spacer(
                Modifier.size(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        contentPadding.calculateBottomPadding()
                    } else {
                        contentPadding.calculateEndPadding(
                            if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                                LayoutDirection.Ltr
                            } else {
                                LayoutDirection.Rtl
                            }
                        )
                    }
                )
            )
            if (currentMediaItem != null) {
                Spacer(Modifier.size(86.dp))
            }
        }

    }
}