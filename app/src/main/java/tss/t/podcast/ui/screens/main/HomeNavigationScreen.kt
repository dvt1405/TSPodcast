@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.HazeStyle
import tss.t.hazeandroid.HazeTint
import tss.t.hazeandroid.hazeChild
import tss.t.podcast.LocalListStateScope
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalPullToRefreshState
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.ui.navigations.TSNavigators
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.discorver.DiscoverPodcastsScreen
import tss.t.podcast.ui.screens.favourite.FavouriteScreen
import tss.t.podcast.ui.screens.favourite.FavouriteViewModel
import tss.t.podcast.ui.screens.player.PlayerScreen
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.player.widgets.PlayerWidgetMain
import tss.t.podcast.ui.screens.podcastsdetail.PodcastDetailScreen
import tss.t.podcast.ui.screens.podcastsdetail.PodcastViewModel
import tss.t.podcast.ui.screens.search.SearchScreen
import tss.t.podcast.ui.screens.search.SearchViewModel
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSPopup
import tss.t.sharedresources.R

internal const val FAVOURITE_TAB_INDEX = 0
internal const val HOME_TAB_INDEX = 1
internal const val SEARCH_TAB_INDEX = 2

fun <T> spatialExpressiveSpring() = spring<T>(
    dampingRatio = 0.8f,
    stiffness = 380f
)

fun <T> nonSpatialExpressiveSpring() = spring<T>(
    dampingRatio = 1f,
    stiffness = 1600f
)


@OptIn(ExperimentalSharedTransitionApi::class)
val podcastDetailBoundsTransform = BoundsTransform { _, _ ->
    spatialExpressiveSpring()
}

@Composable
fun HomeNavigationScreen(
    mainViewModel: MainViewModel,
    screenTitle: String = "TSPodcast",
    selectedTabIndex: Int = 1,
    bottomTabs: List<BottomBarTab> = tabDefaults,
    hazeState: HazeState = remember { HazeState() },
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> }
) {
    val listState = LocalListStateScope.current
    val pullRefreshState = LocalPullToRefreshState.current
    val podcastViewModel = viewModel<PodcastViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
    val playerViewModel: PlayerViewModel =
        viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val playerState by playerViewModel.playerControlState.collectAsState()

    val uiState by mainViewModel.uiState.collectAsState()
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

    val trendingRowState = rememberLazyListState()
    val liveState = rememberPagerState(0) {
        uiState.liveEpisode.size
    }
    val recentFeedState = rememberLazyListState()
    AnimatedContent(
        uiState.route,
        label = "PodcastDetail",
    ) { route ->
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
        ) {
            when (route) {
                is TSNavigators.PodcastDetail -> {
                    PodcastDetailScreen(
                        podcast = route.podcast,
                        playList = route.playList,
                        mainViewModel = mainViewModel,
                        sharedElementKey = uiState.from,
                        podcastViewModel = podcastViewModel
                    )
                }

                is TSNavigators.Player -> {
                    PlayerScreen(
                        playList = route.playList,
                        episode = route.item,
                        podcast = route.podcast!!,
                    )
                }

                is TSNavigators.PlayerFromMini -> {
                    PlayerScreen(route.item)
                }

                else -> {
                    HomeNavigationScreen(
                        listTrending = uiState.listTrending,
                        showLoadingView = uiState.showLoadingView,
                        listFav = uiState.listFav,
                        liveEpisode = uiState.liveEpisode,
                        recentNewFeeds = uiState.recentNewFeeds,
                        recentFeeds = uiState.recentFeeds,
                        screenTitle = screenTitle,
                        selectedTabIndex = selectedTabIndex,
                        bottomTabs = bottomTabs,
                        listState = listState,
                        trendingRowState = trendingRowState,
                        pullRefreshState = pullRefreshState,
                        onRefresh = {
                            mainViewModel.reload()
                        },
                        isRefreshing = uiState.isDataPartLoading,
                        hazeState = hazeState,
                        onTabSelected = onTabSelected,
                        onTrendingClick = {
                            //Navigate to Detail Screen
                            TSNavigators.navigateTo(
                                TSNavigators.PodcastDetail(this)
                            )
                            mainViewModel.setCurrentPodcast(this)
                        },
                        onFavClick = {
                            //Navigate to Detail Screen
                            TSNavigators.navigateTo(
                                TSNavigators.PodcastDetail(this)
                            )
                        },
                        onLiveItemClick = {
                            TSNavigators.navigateTo(
                                TSNavigators.Player(
                                    item = Episode.fromLive(this),
                                    playList = uiState.liveEpisode.flatMap {
                                        it.map {
                                            Episode.fromLive(it)
                                        }
                                    },
                                    podcast = Podcast(
                                        categories = this.categories,
                                        dateCrawled = this.dateCrawled,
                                        datePublished = this.datePublished,
                                        datePublishedPretty = this.datePublishedPretty,
                                        enclosureLength = this.enclosureLength,
                                        enclosureType = this.enclosureType,
                                        enclosureUrl = this.enclosureUrl,
                                        explicit = this.explicit,
                                        feedId = this.feedId,
                                        feedImage = this.feedImage,
                                        feedItunesId = this.feedItunesId,
                                        feedLanguage = this.feedLanguage,
                                        feedTitle = this.title,
                                        guid = this.guid,
                                        id = this.feedId,
                                        image = this.image,
                                        link = this.link,
                                        title = this.title,
                                        description = this.description
                                    )
                                )
                            )
                        },
                        renderCount = uiState.renderCount,
                        recentFeedState = recentFeedState,
                        pagerState = liveState,
                        currentMediaItem = playerState.currentMediaItem
                    )
                }
            }
        }
    }

    AnimatedContent(
        playerState.currentMediaItem to uiState.route,
        label = "Player"
    ) { currentMediaItemAndRoute ->
        val currentMediaItem = currentMediaItemAndRoute.first
        val route = currentMediaItemAndRoute.second
        if (currentMediaItem != null && route !is TSNavigators.Player && route !is TSNavigators.PlayerFromMini) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerWidgetMain(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (route is TSNavigators.PodcastDetail) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                                    .navigationBarsPadding()
                                    .padding(bottom = 72.dp)
                            }
                        )
                        .animateEnterExit(
                            enter = fadeIn() + slideInVertically {
                                it
                            },
                            exit = fadeOut() + slideOutVertically {
                                it
                            }
                        )
                        .shadow(4.dp),
                    title = currentMediaItem.mediaMetadata.title.toString(),
                    image = currentMediaItem.mediaMetadata.artworkUri.toString(),
                    description = currentMediaItem.mediaMetadata.description.toString(),
                    id = currentMediaItem.mediaId,
                    playing = playerState.isPlaying,
                    playPauseClick = {
                        playerViewModel.onPlayPause()
                    },
                    onClick = {
                        TSNavigators.navigateTo(
                            TSNavigators.PlayerFromMini(
                                item = currentMediaItem
                            )
                        )
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeNavigationScreen(
    showLoadingView: Boolean = false,
    listTrending: List<Podcast> = listOf(),
    listFav: List<Podcast> = listOf(),
    liveEpisode: List<List<LiveEpisode>> = emptyList(),
    pagerState: PagerState = rememberPagerState() { liveEpisode.size },
    recentFeeds: List<Podcast> = emptyList(),
    recentNewFeeds: List<Podcast> = emptyList(),
    screenTitle: String = "TSPodcast",
    selectedTabIndex: Int = 1,
    bottomTabs: List<BottomBarTab> = tabDefaults,
    listState: SnapshotStateMap<BottomBarTab, LazyListState> = mutableStateMapOf(),
    trendingRowState: LazyListState = rememberLazyListState(),
    pullRefreshState: SnapshotStateMap<BottomBarTab, PullToRefreshState> = mutableStateMapOf(),
    onRefresh: () -> Unit = {},
    isRefreshing: Map<Int, Boolean> = emptyMap(),
    hazeState: HazeState = remember { HazeState() },
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> },
    onTrendingClick: Podcast.() -> Unit = {},
    onFavClick: Podcast.() -> Unit = {},
    onLiveItemClick: LiveEpisode.() -> Unit = {},
    renderCount: Int = 0,
    recentFeedState: LazyListState = rememberLazyListState(),
    currentMediaItem: MediaItem? = null
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current!!
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val selectedTab = remember(selectedTabIndex) { bottomTabs[selectedTabIndex] }
    val childListState = remember(selectedTab) {
        listState[selectedTab] ?: LazyListState().also {
            listState[selectedTab] = it
        }
    }
    val pullToRefreshState = remember(selectedTab) {
        pullRefreshState[selectedTab] ?: PullToRefreshState().also {
            pullRefreshState[selectedTab] = it
        }
    }
    Scaffold(
        topBar = {
            with(animatedContentScope) {
                with(sharedTransitionScope) {
                    MainTSAppBar(
                        modifier = Modifier
                            .renderInSharedTransitionScopeOverlay(
                                zIndexInOverlay = 4f
                            )
                            .animateEnterExit(
                                enter = fadeIn() + slideInVertically {
                                    -it
                                },
                                exit = fadeOut() + slideOutVertically {
                                    -it
                                }
                            ),
                        hazeState = hazeState,
                        title = screenTitle
                    )
                }
            }
        },
        bottomBar = {
            with(animatedContentScope) {
                with(sharedTransitionScope) {
                    TSBottomNavigation(
                        modifier = Modifier
                            .renderInSharedTransitionScopeOverlay(
                                zIndexInOverlay = 1f
                            )
                            .animateEnterExit(
                                enter = fadeIn() + slideInVertically {
                                    it
                                },
                                exit = fadeOut() + slideOutVertically {
                                    it
                                }
                            ),
                        hazeState = hazeState,
                        selectedTab = selectedTabIndex,
                        tabs = tabDefaults
                    ) { tab, index ->
                        onTabSelected(tab, index)
                    }
                }
            }
        }
    ) { innerPadding ->
        when (selectedTabIndex) {
            FAVOURITE_TAB_INDEX -> {
                FavouriteScreen(
                    listState = childListState,
                    pullToRefreshState = pullToRefreshState,
                    innerPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        end = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = innerPadding.calculateTopPadding(),
                        bottom = if (currentMediaItem == null) {
                            innerPadding.calculateBottomPadding()
                        } else {
                            innerPadding.calculateBottomPadding() + 86.dp
                        }
                    ),
                    viewModel = viewModel<FavouriteViewModel>(LocalViewModelStoreOwner.current!!),
                    onEmptyClick = {
                        onTabSelected(tabDefaults[HOME_TAB_INDEX], HOME_TAB_INDEX)
                    }
                )
            }

            HOME_TAB_INDEX -> {
                DiscoverPodcastsScreen(
                    isRefreshing = isRefreshing,
                    showLoading = showLoadingView,
                    onRefresh = onRefresh,
                    hazeState = hazeState,
                    pullRefreshState = pullToRefreshState,
                    innerPadding = innerPadding,
                    listTrending = listTrending,
                    listFav = listFav,
                    liveEpisode = liveEpisode,
                    pagerState = pagerState,
                    recentFeeds = recentFeeds,
                    recentNewFeeds = recentNewFeeds,
                    onTrendingClick = onTrendingClick,
                    onFavClick = onFavClick,
                    listState = childListState,
                    trendingRowState = trendingRowState,
                    renderCount = renderCount,
                    recentFeedState = recentFeedState,
                    onLiveItemClick = onLiveItemClick,
                    currentMediaItem = currentMediaItem
                )
            }

            else -> {
                val searchViewModel = viewModel<SearchViewModel>(LocalViewModelStoreOwner.current!!)
                val listCategory by searchViewModel.listCategory.collectAsState()
                val listSearch by searchViewModel.listSearch.collectAsState()
                val searchText by remember {
                    searchViewModel.currentSearchText
                }
                SearchScreen(
                    initSearchText = searchText,
                    onSearch = {
                        searchViewModel.performSearch(it)
                    },
                    categories = listCategory,
                    searchResult = listSearch,
                    innerPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        end = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = innerPadding.calculateTopPadding(),
                        bottom = if (currentMediaItem == null) {
                            innerPadding.calculateBottomPadding()
                        } else {
                            innerPadding.calculateBottomPadding() + 86.dp
                        }
                    ),
                    onSearchSelected = { feed ->
                        TSNavigators.navigateTo(TSNavigators.PodcastDetail(Podcast.fromFeed(feed)))
                    }
                )
            }
        }
    }
}

@Composable
private fun TSBottomNavigation(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    selectedTab: Int,
    tabs: List<BottomBarTab> = tabDefaults,
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> }
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 64.dp)
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
                    noiseFactor = 0.5f
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
    modifier: Modifier = Modifier,
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
        }, modifier = modifier
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


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
fun MainScreenPreview() {
    SharedTransitionLayout {
        AnimatedContent(true) {
            if (it) {

            }
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalNavAnimatedVisibilityScope provides this
            ) {
                HomeNavigationScreen(
                    listTrending = listOf(
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                    ),
                    listFav = listOf(
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                    ),
                    recentFeeds = listOf(
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                    ),
                    recentNewFeeds = listOf(
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                        Podcast.default,
                    ),
                    liveEpisode = emptyList()
                )
            }
        }

    }
}


