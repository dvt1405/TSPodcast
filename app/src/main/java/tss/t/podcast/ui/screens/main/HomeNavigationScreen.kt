@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast.ui.screens.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tss.t.coreapi.models.TrendingPodcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.HazeStyle
import tss.t.hazeandroid.HazeTint
import tss.t.hazeandroid.hazeChild
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.discorver.DiscoverPodcastsScreen
import tss.t.podcast.ui.screens.playlist.PlaylistScreen
import tss.t.podcast.ui.screens.podcastsdetail.PodcastDetailScreen
import tss.t.podcast.ui.screens.profile.ProfileScreen
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSPopup
import tss.t.sharedresources.R

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeNavigationScreen(
    sharedTransitionScope: SharedTransitionScope,
    mainViewModel: MainViewModel,
    screenTitle: String = "TSPodcast",
    selectedTabIndex: Int = 1,
    bottomTabs: List<BottomBarTab> = tabDefaults,
    listState: SnapshotStateMap<BottomBarTab, LazyListState> = mutableStateMapOf(),
    pullRefreshState: SnapshotStateMap<BottomBarTab, PullToRefreshState> = mutableStateMapOf(),
    hazeState: HazeState = remember { HazeState() },
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> }
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    if (uiState.error != null) {
        Dialog(onDismissRequest = { mainViewModel.onErrorDialogDismiss() }) {
            TSPopup(
                title = stringResource(R.string.error_load_podcast_popup_title),
                contentText = "${uiState.error?.message}",
                positiveText = stringResource(R.string.popup_btn_reload_title),
                negativeText = stringResource(R.string.popup_btn_cancel_title),
                onPositiveButtonClick = {
                    mainViewModel.reload()
                },
                onNegativeButtonClick = {
                    mainViewModel.onErrorDialogDismiss()
                }
            )
        }
    }
    var isRefreshing by remember {
        mutableStateOf(uiState.isLoading)
    }
    LaunchedEffect(uiState) {
        coroutineScope.launch {
            snapshotFlow { uiState.isLoading }
                .distinctUntilChanged()
                .collect {
                    isRefreshing = it
                }
        }
    }
    Crossfade(uiState.currentPodcast, label = "currentPodcast") { podcast ->
        if (podcast != null) {
            PodcastDetailScreen(
                sharedTransitionScope,
                podcast,
                mainViewModel
            )
        } else {
            HomeNavigationScreen(
                listTrending = uiState.listTrending,
                listFav = uiState.listTrending,
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    mainViewModel.reload()
                },
                selectedTabIndex = selectedTabIndex,
                bottomTabs = bottomTabs,
                listState = listState,
                pullRefreshState = pullRefreshState,
                onTabSelected = onTabSelected,
                hazeState = hazeState,
                screenTitle = screenTitle,
                onTrendingClick = {
                    //Navigate to Detail Screen
                    mainViewModel.getPodcast(this)
                },
                onFavClick = {
                    //Navigate to Detail Screen
                    mainViewModel.getPodcast(this)
                }
            )
        }
    }

}


@Composable
private fun HomeNavigationScreen(
    listTrending: List<TrendingPodcast> = listOf(),
    listFav: List<TrendingPodcast> = listOf(),
    screenTitle: String = "TSPodcast",
    selectedTabIndex: Int = 1,
    bottomTabs: List<BottomBarTab> = tabDefaults,
    listState: SnapshotStateMap<BottomBarTab, LazyListState> = mutableStateMapOf(),
    pullRefreshState: SnapshotStateMap<BottomBarTab, PullToRefreshState> = mutableStateMapOf(),
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> },
    onTrendingClick: TrendingPodcast.() -> Unit = {},
    onFavClick: TrendingPodcast.() -> Unit = {},
) {
    val selectedTab = bottomTabs[selectedTabIndex]
    Scaffold(
        topBar = {
            MainTSAppBar(hazeState, screenTitle)
        },
        bottomBar = {
            TSBottomNavigation(
                hazeState = hazeState,
                selectedTab = selectedTabIndex,
                tabs = tabDefaults
            ) { tab, index ->
                onTabSelected(tab, index)
            }
        }
    ) { innerPadding ->
        val childListState = listState[selectedTab] ?: rememberLazyListState().also {
            listState[selectedTab] = it
        }
        val pullToRefreshState =
            pullRefreshState[selectedTab] ?: rememberPullToRefreshState().also {
                pullRefreshState[selectedTab] = it
            }
        when (selectedTabIndex) {
            0 -> {
                PlaylistScreen()
            }

            1 -> {
                DiscoverPodcastsScreen(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        onRefresh()
                    },
                    hazeState = hazeState,
                    pullRefreshState = pullToRefreshState,
                    innerPadding = innerPadding,
                    listTrending = listTrending,
                    listFav = listFav,
                    listState = childListState,
                    onFavClick = onFavClick,
                    onTrendingClick = onTrendingClick

                )
            }

            else -> {
                ProfileScreen()
            }
        }
    }
}

@Composable
private fun TSBottomNavigation(
    hazeState: HazeState,
    selectedTab: Int,
    tabs: List<BottomBarTab> = tabDefaults,
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
            .padding(vertical = 48.dp, horizontal = 64.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color.Transparent)
            .hazeChild(
                state = hazeState,
                style = HazeDefaults.style(
                    backgroundColor = Colors.White,
                    tint = Colors.White.copy(.6f),
                    blurRadius = 32.dp,
                )
            )
            .border(
                width = Dp.Hairline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Colors.Primary.copy(alpha = .8f),
                        Colors.Primary.copy(alpha = .2f),
                    ),
                ),
                shape = CircleShape
            )
            .padding(bottom = 4.dp)
    ) {
        BottomBarTabs(
            tabs = tabs,
            textColor = Colors.TextColor,
            contentColor = Colors.TextTitleColor,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )
    }
}

@Composable
fun MainTSAppBar(
    hazeState: HazeState,
    title: String = "Welcome to TSPodcast"
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        title = {
            Text(
                title,
                style = TextStyles.Title4,
                textAlign = TextAlign.Center,
                modifier = Modifier
            )
        }, modifier = Modifier
            .hazeChild(
                hazeState,
                HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tints = listOf(
                        HazeTint.Brush(
                            Brush.verticalGradient(
                                listOf(
                                    Colors.White.copy(alpha = 0.8f),
                                    Colors.White.copy(alpha = 0.6f),
                                    Colors.White.copy(alpha = 0.5f),
                                )
                            )
                        )
                    ),
                    blurRadius = 32.dp,
                    noiseFactor = 0.15f,
                )
            )
    )
}


@Composable
@Preview
fun MainScreenPreview() {
    HomeNavigationScreen(
        listTrending = listOf(
            TrendingPodcast.default,
            TrendingPodcast.default,
            TrendingPodcast.default,
            TrendingPodcast.default,
            TrendingPodcast.default,
        ),
        listFav = listOf(
            TrendingPodcast.default,
            TrendingPodcast.default,
            TrendingPodcast.default,
            TrendingPodcast.default,
            TrendingPodcast.default,
        )
    )
}


