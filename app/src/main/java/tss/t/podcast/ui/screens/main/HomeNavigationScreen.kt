@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast.ui.screens.main

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import tss.t.coreapi.models.Podcast
import tss.t.featureradio.RadioViewModel
import tss.t.featureradio.ui.RadioScreen
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.HazeStyle
import tss.t.hazeandroid.HazeTint
import tss.t.hazeandroid.hazeChild
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.podcast.R
import tss.t.podcast.ui.navigations.TSHomeRouter
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.discorver.DiscoverPodcastsScreen
import tss.t.podcast.ui.screens.favourite.FavouriteScreen
import tss.t.podcast.ui.screens.favourite.FavouriteViewModel
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.podcastsdetail.PodcastViewModel
import tss.t.podcast.ui.screens.search.SearchScreen
import tss.t.podcast.ui.screens.search.SearchViewModel
import tss.t.podcast.ui.screens.uimodels.main.UIState
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

internal const val FAVOURITE_TAB_INDEX = 0
internal const val HOME_TAB_INDEX = 1
internal const val RADIO_TAB_INDEX = 2
internal const val SEARCH_TAB_INDEX = 3

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


@OptIn(ExperimentalSharedTransitionApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "RestrictedApi")
@Composable
fun HomeNavigationScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    parentNavHost: NavHostController,
    podcastDetailViewModel: PodcastViewModel,
    favViewModel: FavouriteViewModel,
    innerNavHost: NavHostController,
    hazeState: HazeState = remember { HazeState() }
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current!!
    val animatedContentScope = LocalNavAnimatedVisibilityScope.current!!
    val navBackStackEntry by innerNavHost.currentBackStackEntryAsState()
    val screenTitleRes = remember(navBackStackEntry?.destination?.route) {
        when (navBackStackEntry?.destination?.route) {
            TSHomeRouter.Discover.name -> R.string.discorver_screen_title
            TSHomeRouter.Favourite.name -> R.string.favourite_screen_title
            TSHomeRouter.Search.name -> R.string.search_screen_title
            TSHomeRouter.Radio.name -> R.string.radio_screen_title
            else -> R.string.app_name
        }
    }

    val selectedTabIndex = remember(navBackStackEntry?.destination?.route) {
        when (navBackStackEntry?.destination?.route) {
            TSHomeRouter.Discover.name -> HOME_TAB_INDEX
            TSHomeRouter.Favourite.name -> FAVOURITE_TAB_INDEX
            TSHomeRouter.Search.name -> SEARCH_TAB_INDEX
            TSHomeRouter.Radio.name -> RADIO_TAB_INDEX
            else -> HOME_TAB_INDEX
        }
    }

    val dashboardUIState by mainViewModel.uiState.collectAsState()
    val playerControlState by playerViewModel.playerControlState.collectAsState()
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
                                enter = fadeIn() + slideInVertically { -it },
                                exit = fadeOut() + slideOutVertically { -it }
                            ),
                        hazeState = hazeState,
                        title = stringResource(screenTitleRes)
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
                                enter = fadeIn() + slideInVertically { it },
                                exit = fadeOut() + slideOutVertically { it }
                            ),
                        hazeState = hazeState,
                        selectedTab = selectedTabIndex,
                        tabs = tabDefaults
                    ) { tab, index ->
                        bottomBarNavigateTo(index = index, innerNavHost = innerNavHost)
                    }
                }
            }
        }
    ) { innerPadding ->
        HomeNavHost(
            innerNavHost = innerNavHost,
            parentNavHost = parentNavHost,
            favViewModel = favViewModel,
            animationScope = animatedContentScope,
            hazeState = hazeState,
            dashboardUIState = dashboardUIState,
            playerControlState = playerControlState,
            innerPadding = innerPadding
        )
    }

}

@SuppressLint("RestrictedApi")
private fun bottomBarNavigateTo(index: Int, innerNavHost: NavHostController) {
    when (index) {
        HOME_TAB_INDEX -> {
            if (innerNavHost.currentDestination?.route == TSHomeRouter.Discover.route) {
                return
            }
            innerNavHost.popBackStack(TSHomeRouter.Discover.route, inclusive = false)
        }

        FAVOURITE_TAB_INDEX -> {
            findAndPopupTo(innerNavHost, TSHomeRouter.Favourite)
        }

        SEARCH_TAB_INDEX -> {
            findAndPopupTo(innerNavHost, TSHomeRouter.Search)
        }

        RADIO_TAB_INDEX -> {
            findAndPopupTo(innerNavHost, TSHomeRouter.Radio)
        }
    }
}

@SuppressLint("RestrictedApi")
private fun findAndPopupTo(
    innerNavHost: NavHostController,
    tsHomeRouter: TSHomeRouter
) {
    if (innerNavHost.currentDestination?.route == tsHomeRouter.route) {
        return
    }
    innerNavHost.currentBackStackEntry?.destination
    val isInBackstack = innerNavHost.popBackStack(tsHomeRouter.route, false)
    if (!isInBackstack) {
        innerNavHost.navigate(tsHomeRouter.route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeNavHost(
    innerNavHost: NavHostController,
    parentNavHost: NavHostController,
    favViewModel: FavouriteViewModel,
    animationScope: AnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current!!,
    hazeState: HazeState,
    dashboardUIState: UIState,
    playerControlState: PlayerViewModel.PlayerControlState,
    innerPadding: PaddingValues
) {
    val sharedTransScope = LocalSharedTransitionScope.current!!
    val viewmodelStore = LocalViewModelStoreOwner.current!!
    val searchViewModel = viewModel<SearchViewModel>(viewmodelStore)
    val mainViewModel: MainViewModel = viewModel(viewmodelStore)
    val podcastViewModel: PodcastViewModel = viewModel(viewmodelStore)
    val playerViewModel: PlayerViewModel = viewModel(viewmodelStore)
    val coroutineScope = rememberCoroutineScope()
    NavHost(
        navController = innerNavHost,
        startDestination = TSHomeRouter.Discover.route,
    ) {
        composable(TSHomeRouter.Discover.route) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransScope,
                LocalNavAnimatedVisibilityScope provides animationScope,
            ) {
                DiscoverPodcastsScreen(
                    uiState = dashboardUIState,
                    playerControlState = playerControlState,
                    hazeState = hazeState,
                    innerPadding = innerPadding,
                    showLoading = dashboardUIState.showLoadingView,
                    onRefresh = {
                        mainViewModel.reload()
                    },
                    onTrendingClick = {
                        mainViewModel.setCurrentPodcast(this)
                        parentNavHost.navigate(TSRouter.PodcastDetail.route)
                    },
                    onFavClick = {
                        mainViewModel.setCurrentPodcast(this)
                        parentNavHost.navigate(TSRouter.PodcastDetail.route)
                    },
                    onLiveItemClick = {
                        coroutineScope.launch {
                            playerViewModel.playLive(
                                liveEpisode = this@DiscoverPodcastsScreen,
                                listItem = dashboardUIState.liveEpisode.flatten()
                            )
                            parentNavHost.navigate(TSRouter.Player.route)
                        }
                    },
                )
            }
        }

        composable(TSHomeRouter.Favourite.route) {
            FavouriteScreen(
                rootNavHost = parentNavHost,
                listState = rememberLazyListState(),
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                innerPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                    top = innerPadding.calculateTopPadding(),
                    bottom = if (playerControlState.currentMediaItem == null) {
                        innerPadding.calculateBottomPadding()
                    } else {
                        innerPadding.calculateBottomPadding() + 86.dp
                    }
                ),
                pullToRefreshState = rememberPullToRefreshState(),
                viewModel = favViewModel,
                hazeState = hazeState,
                onEmptyClick = {
                    innerNavHost.popBackStack(
                        route = TSHomeRouter.Discover.route,
                        inclusive = false,
                        saveState = false
                    )
                }
            )
        }

        composable(TSHomeRouter.Search.route) {
            val listCategory by searchViewModel.listCategory.collectAsState()
            val listSearch by searchViewModel.listSearch.collectAsState()
            val searchText by rememberSaveable {
                searchViewModel.currentSearchText
            }
            SearchScreen(
                initSearchText = searchText,
                onSearch = {
                    searchViewModel.performSearch(it)
                },
                categories = listCategory,
                searchResult = listSearch,
                hazeState = hazeState,
                innerPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                    top = innerPadding.calculateTopPadding(),
                    bottom = if (playerControlState.currentMediaItem == null) {
                        innerPadding.calculateBottomPadding()
                    } else {
                        innerPadding.calculateBottomPadding() + 86.dp
                    }
                ),
                onSearchSelected = { feed ->
                    val podcast = Podcast.fromFeed(feed)
                    mainViewModel.setCurrentPodcast(podcast)
                    podcastViewModel.getEpisodes(podcast)
                    parentNavHost.navigate(TSRouter.PodcastDetail.route)
                }
            )
        }

        composable(TSHomeRouter.Radio.route) {
            val radioViewModel = viewModel<RadioViewModel>(viewmodelStore)
            val uiState by radioViewModel.uiState.collectAsState()
            val listState by radioViewModel.listState
            RadioScreen(
                modifier = Modifier,
                contentPadding = innerPadding,
                hazeState = hazeState,
                radioUISate = uiState,
                currentMediaItem = playerControlState.currentMediaItem,
                isMediaPlaying = playerControlState.isPlaying,
                isMediaLoading = playerControlState.isLoading,
                listState = listState,
                onPlay = {
                    coroutineScope.launch {
                        playerViewModel.playRadio(it, uiState.data?.listRadio ?: emptyList())
                    }
                },
                onPause = {
                    playerViewModel.onPlayPause()
                },
                onClick = {
                    coroutineScope.launch {
                        playerViewModel.playRadio(it, uiState.data?.listRadio ?: emptyList())
                        parentNavHost.navigate(TSRouter.Player.route)
                    }
                }
            )
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
    title: String = stringResource(R.string.discorver_screen_title)
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        title = {
            AnimatedContent(
                targetState = title,
                label = "TitleAppBar"
            ) {
                Text(
                    text = it,
                    style = TextStyles.Title4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                )
            }
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