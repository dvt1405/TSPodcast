package tss.t.podcast.ui.navigations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import tss.t.podcast.ui.theme.Motion
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tss.t.featureonboarding.OnboardingScreen
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.featureonboarding.SelectFavouriteCategoryScreen
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.favourite.FavouriteViewModel
import tss.t.podcast.ui.screens.history.HistoryScreen
import tss.t.podcast.ui.screens.history.HistoryViewModel
import tss.t.podcast.ui.screens.main.HomeNavigationScreen
import tss.t.podcast.ui.screens.player.PlayerScreen
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.podcastsdetail.PodcastDetailScreen
import tss.t.podcast.ui.screens.podcastsdetail.PodcastViewModel
import tss.t.podcast.ui.screens.settings.SettingsScreen
import tss.t.podcast.ui.screens.settings.SettingsViewModel

@Composable
fun TSNavGraph(
    navHost: NavHostController = rememberNavController(),
    innerNavHost: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = viewModel(LocalViewModelStoreOwner.current!!),
    mainViewModel: MainViewModel = viewModel(LocalViewModelStoreOwner.current!!),
    playerViewModel: PlayerViewModel = viewModel(LocalViewModelStoreOwner.current!!),
    podcastViewModel: PodcastViewModel = viewModel(LocalViewModelStoreOwner.current!!),
    favViewModel: FavouriteViewModel = viewModel(LocalViewModelStoreOwner.current!!)
) {
    val isOnboardingFinished by onboardingViewModel.onboardingStep.collectAsState()
    val startDestination = when {
        !isOnboardingFinished.isOnboardingDone -> TSRouter.Onboarding.route
        !isOnboardingFinished.isSelectedFavourite -> TSRouter.SelectFavouriteCategory.route
        else -> TSRouter.Main.route
    }
    val viewModelStoreOwner = LocalViewModelStoreOwner.current!!
    tss.t.podcast.analytics.TrackScreens(outer = navHost, inner = innerNavHost)
    NavHost(
        navController = navHost,
        startDestination = startDestination,
        // NavHost's default is a 700 ms cross-fade both ways: long enough to feel sluggish,
        // and both screens (with the glass blur) were drawn for all of it.
        enterTransition = Motion.fadeEnter,
        exitTransition = Motion.fadeExit,
        popEnterTransition = Motion.fadeEnter,
        popExitTransition = Motion.fadeExit,
    ) {
        composable(
            TSRouter.Main.route,
            exitTransition = { if (targetState.isPlayer()) Motion.underSheetExit(this) else Motion.fadeExit(this) },
            popEnterTransition = { if (initialState.isPlayer()) Motion.underSheetPopEnter(this) else Motion.fadeEnter(this) },
        ) {
            podcastViewModel.clearTempListState()
            CompositionLocalProvider(
                LocalNavAnimatedVisibilityScope provides this,
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                HomeNavigationScreen(
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    parentNavHost = navHost,
                    podcastDetailViewModel = podcastViewModel,
                    favViewModel = favViewModel,
                    innerNavHost = innerNavHost,
                )
            }
        }

        composable(TSRouter.Onboarding.route) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                OnboardingScreen(
                    modifier = Modifier,
                    viewModel = onboardingViewModel
                )
            }
        }

        composable(TSRouter.SelectFavouriteCategory.route) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                SelectFavouriteCategoryScreen(
                    viewmodel = onboardingViewModel
                )
            }
        }

        composable(
            route = TSRouter.PodcastDetail.route,
            exitTransition = { if (targetState.isPlayer()) Motion.underSheetExit(this) else Motion.fadeExit(this) },
            popEnterTransition = { if (initialState.isPlayer()) Motion.underSheetPopEnter(this) else Motion.fadeEnter(this) },
        ) {
            val navPodcast = mainViewModel.getCurrentPodcast()
            // Was called in composition, i.e. again on every recomposition of this route
            // (each uiState emission), relaunching the request while it was still loading.
            LaunchedEffect(navPodcast) {
                navPodcast?.let { podcastViewModel.getEpisodes(it) }
            }
            val dashboardUIState by mainViewModel.uiState.collectAsState()
            val podcastDetailUIState by podcastViewModel.uiState.collectAsState()
            val playlist = remember(podcastDetailUIState) {
                if (podcastDetailUIState is PodcastViewModel.PodcastUIState.Success) {
                    (podcastDetailUIState as PodcastViewModel.PodcastUIState.Success).episodes
                } else {
                    emptyList()
                }
            }
            CompositionLocalProvider(
                LocalNavAnimatedVisibilityScope provides this,
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                // Null after process death, when the ViewModel state is back to
                // its default and no podcast was passed through the nav args.
                // Same failure shape the Player route was fixed for in 2cea6ee.
                val podcast = navPodcast ?: podcastDetailUIState.podcast
                if (podcast == null) {
                    LaunchedEffect(Unit) { navHost.popBackStack() }
                    return@CompositionLocalProvider
                }
                PodcastDetailScreen(
                    navHost = navHost,
                    podcast = podcast,
                    playList = playlist,
                    sharedElementKey = dashboardUIState.from,
                    mainViewModel = mainViewModel,
                    podcastViewModel = podcastViewModel
                )
            }
        }

        composable(
            TSRouter.Settings.route,
            enterTransition = Motion.pushEnter,
            exitTransition = Motion.pushExit,
            popEnterTransition = Motion.popEnter,
            popExitTransition = Motion.popExit,
        ) {
            val settingsViewModel: SettingsViewModel = viewModel(viewModelStoreOwner)
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navHost.popBackStack() },
                onOpenHistory = { navHost.navigate(TSRouter.History.route) }
            )
        }

        composable(
            TSRouter.History.route,
            enterTransition = Motion.pushEnter,
            exitTransition = { if (targetState.isPlayer()) Motion.underSheetExit(this) else Motion.pushExit(this) },
            popEnterTransition = { if (initialState.isPlayer()) Motion.underSheetPopEnter(this) else Motion.popEnter(this) },
            popExitTransition = Motion.popExit,
        ) {
            val historyViewModel: HistoryViewModel = viewModel(viewModelStoreOwner)
            val playerUIState by playerViewModel.miniPlayerState.collectAsState()
            val scope = rememberCoroutineScope()
            HistoryScreen(
                viewModel = historyViewModel,
                hasMiniPlayer = playerUIState.currentMediaItem != null,
                onBack = { navHost.popBackStack() },
                onPlay = { entry ->
                    tss.t.podcast.analytics.ContentAnalytics.historyEpisode(
                        entry.mediaId,
                        tss.t.sharedlibrary.analytics.AppEvents.Section.HISTORY
                    )
                    scope.launch {
                        playerViewModel.playFromHistory(entry)
                        navHost.navigate(TSRouter.Player.route)
                    }
                }
            )
        }

        composable(
            route = TSRouter.Player.route,
            enterTransition = Motion.sheetEnter,
            exitTransition = Motion.fadeExit,
            popEnterTransition = Motion.fadeEnter,
            popExitTransition = Motion.sheetExit,
        ) {
            val playerUIState by playerViewModel.miniPlayerState.collectAsState()
            // Was called in composition: with playerControlState ticking every 200 ms that
            // ran a favourite lookup on the IO pool (and another state update) 5x a second.
            val mediaId = playerUIState.currentMediaItem?.mediaId
            LaunchedEffect(mediaId) { playerViewModel.checkFavourite(mediaId) }
            CompositionLocalProvider(
                LocalNavAnimatedVisibilityScope provides this,
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                if (playerUIState.currentMediaItem != null) {
                    PlayerScreen(
                        episode = playerUIState.currentMediaItem!!,
                        viewmodel = playerViewModel,
                        navHost = navHost
                    )
                }
            }
        }
    }

}

private fun NavBackStackEntry.isPlayer() = destination.route == TSRouter.Player.route

enum class TSRouter(val route: String) {
    Onboarding("Onboarding"),
    SelectFavouriteCategory("SelectFavouriteCategory"),
    Main("Main"),
    PodcastDetail("PodcastDetail"),
    Player("Player"),
    Settings("Settings"),
    History("History")
}

enum class TSHomeRouter(val route: String) {
    Discover("Discover"),
    Favourite("Favourite"),
    Search("Search"),
    Radio("Radio")
}