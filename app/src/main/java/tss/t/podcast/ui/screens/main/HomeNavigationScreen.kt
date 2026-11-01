@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast.ui.screens.main

import tss.t.sharedlibrary.ui.glass.glassHighlight
import tss.t.sharedlibrary.ui.glass.glassRim
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import tss.t.podcast.ui.theme.Motion
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    // Not playerControlState: that one ticks every 200 ms during playback (position) and
    // recomposed the whole Home, bars and current tab included, five times a second.
    val miniPlayerState by playerViewModel.miniPlayerState.collectAsState()
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
                                enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter(Motion.LONG)) { -it },
                                exit = fadeOut(Motion.exit(Motion.MEDIUM)) + slideOutVertically(Motion.exit(Motion.MEDIUM)) { -it }
                            ),
                        hazeState = hazeState,
                        title = stringResource(screenTitleRes),
                        onSettings = if (selectedTabIndex == HOME_TAB_INDEX) {
                            { parentNavHost.navigate(TSRouter.Settings.route) }
                        } else {
                            null
                        },
                        onHistory = if (selectedTabIndex == FAVOURITE_TAB_INDEX) {
                            { parentNavHost.navigate(TSRouter.History.route) }
                        } else {
                            null
                        }
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
                                enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter(Motion.LONG)) { it },
                                exit = fadeOut(Motion.exit(Motion.MEDIUM)) + slideOutVertically(Motion.exit(Motion.MEDIUM)) { it }
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
            miniPlayerState = miniPlayerState,
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
    miniPlayerState: PlayerViewModel.MiniPlayerState,
    innerPadding: PaddingValues
) {
    val sharedTransScope = LocalSharedTransitionScope.current!!
    val viewmodelStore = LocalViewModelStoreOwner.current!!
    val searchViewModel = viewModel<SearchViewModel>(viewmodelStore)
    val mainViewModel: MainViewModel = viewModel(viewmodelStore)
    val podcastViewModel: PodcastViewModel = viewModel(viewmodelStore)
    val playerViewModel: PlayerViewModel = viewModel(viewmodelStore)
    val historyViewModel: tss.t.podcast.ui.screens.history.HistoryViewModel = viewModel(viewmodelStore)
    val coroutineScope = rememberCoroutineScope()
    val hasMiniPlayer = miniPlayerState.hasMedia
    // Tab content padding: above the mini player when one is showing.
    val tabPadding = remember(innerPadding, hasMiniPlayer) {
        PaddingValues(
            start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
            end = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
            top = innerPadding.calculateTopPadding(),
            bottom = if (hasMiniPlayer) {
                innerPadding.calculateBottomPadding() + 86.dp
            } else {
                innerPadding.calculateBottomPadding()
            }
        )
    }
    NavHost(
        navController = innerNavHost,
        startDestination = TSHomeRouter.Discover.route,
        enterTransition = Motion.tabEnter,
        exitTransition = Motion.tabExit,
        popEnterTransition = Motion.tabEnter,
        popExitTransition = Motion.tabExit,
    ) {
        composable(TSHomeRouter.Discover.route) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransScope,
                LocalNavAnimatedVisibilityScope provides animationScope,
            ) {
                DiscoverPodcastsScreen(
                    uiState = dashboardUIState,
                    hasMiniPlayer = hasMiniPlayer,
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
                    onLocationsChange = mainViewModel::setLocations,
                    continueListening = historyViewModel.continueListening.collectAsState().value,
                    onContinueClick = { entry ->
                        tss.t.podcast.analytics.ContentAnalytics.historyEpisode(
                            entry.mediaId,
                            tss.t.sharedlibrary.analytics.AppEvents.Section.CONTINUE_LISTENING
                        )
                        coroutineScope.launch {
                            playerViewModel.playFromHistory(entry)
                            parentNavHost.navigate(TSRouter.Player.route)
                        }
                    },
                    onSeeHistory = { parentNavHost.navigate(TSRouter.History.route) },
                    onNewsClick = { news ->
                        coroutineScope.launch {
                            playerViewModel.playerEpisode(
                                episode = news.episode,
                                podcast = news.podcast,
                                listItem = listOf(news.episode)
                            )
                            parentNavHost.navigate(TSRouter.Player.route)
                        }
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
            // FavouriteScreen reloads on every visit (its own LaunchedEffect): favourites added
            // from Detail or the Player since the first load must show (features-spec §3.8 [Sửa]).
            FavouriteScreen(
                rootNavHost = parentNavHost,
                listState = rememberLazyListState(),
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                innerPadding = tabPadding,
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
            val searchMode by searchViewModel.searchMode.collectAsState()
            val selectedCategory by searchViewModel.selectedCategory.collectAsState()
            val categoryResults by searchViewModel.categoryResults.collectAsState()
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
                searchMode = searchMode,
                onSearchModeSelected = searchViewModel::setSearchMode,
                selectedCategory = selectedCategory,
                categoryResults = categoryResults,
                primaryLocation = searchViewModel.primaryLocation,
                onCategorySelected = searchViewModel::selectCategory,
                hazeState = hazeState,
                innerPadding = tabPadding,
                onSearchSelected = { feed ->
                    val podcast = Podcast.fromFeed(feed)
                    val inTopic = categoryResults?.contains(feed) == true
                    tss.t.podcast.analytics.ContentAnalytics.podcast(
                        podcast,
                        if (inTopic) tss.t.sharedlibrary.analytics.AppEvents.Section.CATEGORY_RESULTS else tss.t.sharedlibrary.analytics.AppEvents.Section.SEARCH_RESULTS,
                        position = (if (inTopic) categoryResults.orEmpty() else listSearch).indexOf(feed).takeIf { it >= 0 },
                    )
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
                currentMediaItem = miniPlayerState.currentMediaItem,
                isMediaPlaying = miniPlayerState.isPlaying,
                isMediaLoading = miniPlayerState.isLoading,
                listState = listState,
                onPlay = {
                    tss.t.podcast.analytics.ContentAnalytics.radio(it)
                    coroutineScope.launch {
                        playerViewModel.playRadio(it, uiState.data?.listRadio ?: emptyList())
                    }
                },
                onPause = {
                    playerViewModel.onPlayPause()
                },
                onClick = {
                    tss.t.podcast.analytics.ContentAnalytics.radio(it)
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
    val glass = tss.t.sharedlibrary.ui.glass.LocalLiquidGlass.current
    val pill = RoundedCornerShape(100.dp)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            // 64dp a side left ~58dp per tab on a 360dp phone, too narrow for the labels.
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .height(64.dp)
            .then(
                if (glass) Modifier.shadow(
                    16.dp, pill,
                    ambientColor = tss.t.sharedlibrary.ui.glass.GlassTokens.ShadowColor,
                    spotColor = tss.t.sharedlibrary.ui.glass.GlassTokens.ShadowColor
                ) else Modifier
            )
            .clip(pill)
            .background(Color.Transparent)
            .hazeChild(
                state = hazeState,
                style = HazeDefaults.style(
                    backgroundColor = Colors.White,
                    // Liquid Glass: thinner tint so the content shows through, more blur.
                    tint = Colors.White.copy(if (glass) .35f else .6f),
                    blurRadius = if (glass) 24.dp else 32.dp,
                    noiseFactor = if (glass) 0.15f else 0.5f
                )
            )
            .then(
                if (glass) {
                    Modifier
                        .glassHighlight()
                        .glassRim(CircleShape)
                } else {
                    Modifier.border(
                        width = Dp.Hairline,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Colors.Primary.copy(alpha = .8f),
                                Colors.Primary.copy(alpha = .2f),
                            ),
                        ),
                        shape = CircleShape
                    )
                }
            )
            .padding(bottom = 4.dp)
    ) {
        if (glass) {
            // Glass lens that slides under the selected tab.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                val tabWidth = maxWidth / tabs.size
                val offset by androidx.compose.animation.core.animateDpAsState(
                    tabWidth * selectedTab,
                    // Settles without the visible wobble of the old 0.7 damping, and a bit
                    // stiffer so it keeps pace with the tab content's fade-through.
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.86f,
                        stiffness = 500f
                    ),
                    label = "glassLens"
                )
                Box(
                    Modifier
                        // The spring overshoots below 0 when sliding back to the first tab, and
                        // padding rejects negative values (Crashlytics d08c1213). Offset accepts them.
                        .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }
                        .padding(top = 6.dp, bottom = 2.dp)
                        .size(width = tabWidth, height = maxHeight)
                        .padding(horizontal = 6.dp)
                        .clip(pill)
                        .background(Colors.Primary.copy(alpha = 0.10f))
                        .glassRim(pill)
                )
            }
        }
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
    title: String = stringResource(R.string.discorver_screen_title),
    onSettings: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        actions = {
            if (onHistory != null) {
                IconButton(onClick = onHistory) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.History,
                        contentDescription = stringResource(R.string.history_open)
                    )
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_open))
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        title = {
            AnimatedContent(
                targetState = title,
                // Same fade-through as the tab content, no scale (text scaling looks soft).
                transitionSpec = {
                    fadeIn(tween(210, 60, LinearOutSlowInEasing)) togetherWith
                            fadeOut(tween(90)) using SizeTransform(clip = false)
                },
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