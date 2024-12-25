package tss.t.featureradio.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import tss.t.coreapi.models.TSDataState
import tss.t.coreradio.models.RadioChannel
import tss.t.featureradio.models.RadioUISate
import tss.t.featureradio.ui.widget.RadioChannelItem
import tss.t.featureradio.ui.widget.RadioChannelItemShimmer

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
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

    LazyColumn(
        modifier = modifier.animateContentSize(),
        state = listState
    ) {
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

    }
}