package tss.t.podcast.ui.navigations

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tss.t.featureonboarding.OnboardingScreen
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.featureonboarding.SelectFavouriteCategoryScreen
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.favourite.FavouriteViewModel
import tss.t.podcast.ui.screens.main.HomeNavigationScreen
import tss.t.podcast.ui.screens.player.PlayerScreen
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.podcastsdetail.PodcastDetailScreen
import tss.t.podcast.ui.screens.podcastsdetail.PodcastViewModel

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
    NavHost(
        navController = navHost,
        startDestination = startDestination
    ) {
        composable(TSRouter.Main.route) {
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

        composable(route = TSRouter.PodcastDetail.route) {
            val navPodcast = mainViewModel.getCurrentPodcast()
            navPodcast?.let {
                podcastViewModel.getEpisodes(it)
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
                PodcastDetailScreen(
                    navHost = navHost,
                    podcast = navPodcast ?: podcastDetailUIState.podcast!!,
                    playList = playlist,
                    sharedElementKey = dashboardUIState.from,
                    mainViewModel = mainViewModel,
                    podcastViewModel = podcastViewModel
                )
            }
        }

        composable(
            route = TSRouter.Player.route,
            enterTransition = { fadeIn() + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(targetAlpha = 0.1f) + slideOutVertically { it } },
            popExitTransition = { fadeOut(targetAlpha = 0.1f) + slideOutVertically { it } }
        ) {
            val playerUIState by playerViewModel.playerControlState.collectAsState()
            playerViewModel.checkFavourite(playerUIState.currentMediaItem?.mediaId)
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

enum class TSRouter(val route: String) {
    Onboarding("Onboarding"),
    SelectFavouriteCategory("SelectFavouriteCategory"),
    Main("Main"),
    PodcastDetail("PodcastDetail"),
    Player("Player")
}

enum class TSHomeRouter(val route: String) {
    Discover("Discover"),
    Favourite("Favourite"),
    Search("Search"),
    Radio("Radio")
}