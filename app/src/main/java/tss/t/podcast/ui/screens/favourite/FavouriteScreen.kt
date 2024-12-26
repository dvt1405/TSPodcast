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
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import tss.t.ads.MaxAdViewComposable
import tss.t.core.models.FavouriteDTO
import tss.t.coreapi.models.databaseview.PodcastAndEpisode
import tss.t.coreradio.models.RadioChannel
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.favourite.widgets.EmptyFavouriteWidget
import tss.t.podcast.ui.screens.favourite.widgets.FavouriteItemWidget
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.sharedfirebase.LocalAnalyticsScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouriteScreen(
    rootNavHost: NavHostController,
    listState: LazyListState,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    innerPadding: PaddingValues,
    hazeState: HazeState,
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
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .haze(
                state = hazeState,
                style = HazeDefaults.tint
            )
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
            item {
                MaxAdViewComposable(
                    tsAnalytics = LocalAnalyticsScope.current!!
                )
            }
            items(uiState.listFav) { favouriteDTO ->
                FavouriteItemWidget(
                    favouriteDTO,
                    modifier = Modifier.padding(
                        vertical = 12.dp,
                        horizontal = 16.dp
                    )
                ) {
                    coroutineScope.launch {
                        val podcastEpisode = viewModel.onFavSelected(it) ?: return@launch
                        onFavouriteSelected(
                            mainViewModel,
                            podcastEpisode,
                            playerViewModel,
                            favouriteDTO,
                            rootNavHost
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.size(innerPadding.calculateBottomPadding()))
        }
    }
}

private suspend fun onFavouriteSelected(
    mainViewModel: MainViewModel,
    related: Any,
    playerViewModel: PlayerViewModel,
    favouriteDTO: FavouriteDTO,
    rootNavHost: NavHostController
) {
    if (related is PodcastAndEpisode) {
        mainViewModel.setCurrentPodcast(related.podcast)
        playerViewModel.playerEpisode(
            episode = related.episode.firstOrNull {
                favouriteDTO.id == it.id.toString()
            } ?: related.episode.first(),
            podcast = related.podcast,
            listItem = related.episode
        )
        rootNavHost.navigate(TSRouter.PodcastDetail.route)
    } else if (related is List<*>) {
        val listRadio = related.filterIsInstance<RadioChannel>()
            .takeIf { it.isNotEmpty() }
            ?: return
        val playItem = listRadio.firstOrNull { it.channelId == favouriteDTO.id }
            ?: listRadio.first()
        playerViewModel.playRadio(
            playItem,
            listRadio
        )
        rootNavHost.navigate(TSRouter.Player.route)
    }
}