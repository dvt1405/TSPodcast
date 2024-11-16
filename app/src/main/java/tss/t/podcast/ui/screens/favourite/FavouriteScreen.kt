package tss.t.podcast.ui.screens.favourite

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tss.t.podcast.ui.screens.favourite.widgets.EmptyFavouriteWidget
import tss.t.podcast.ui.screens.favourite.widgets.FavouriteItemWidget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouriteScreen(
    listState: LazyListState,
    innerPadding: PaddingValues,
    pullToRefreshState: PullToRefreshState,
    viewModel: FavouriteViewModel,
    onEmptyClick: () -> Unit = {}
) {
    var isRefreshing by remember() {
        mutableStateOf(false)
    }
    val uiState by viewModel.favUiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.selectAll()
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        state = pullToRefreshState,
        onRefresh = {
            isRefreshing = !isRefreshing
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(),
            state = listState
        ) {
            item {
                Spacer(Modifier.size(innerPadding.calculateTopPadding()))
            }
            if (uiState.listFav.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyFavouriteWidget(
                        Modifier.padding(top = 48.dp),
                        onClick = onEmptyClick
                    )
                }
            } else {
                items(uiState.listFav) {
                    FavouriteItemWidget(
                        it,
                        modifier = Modifier.padding(
                            vertical = 12.dp,
                            horizontal = 16.dp
                        )
                    ) {
                        viewModel.onFavSelected(it)
                    }
                }
            }
            item {
                Spacer(Modifier.size(innerPadding.calculateBottomPadding()))
            }
        }

    }
}