@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import tss.t.featureonboarding.OnboardingScreen
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.featureonboarding.SelectFavouriteCategoryScreen
import tss.t.hazeandroid.HazeState
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.main.BottomBarTab
import tss.t.podcast.ui.screens.main.HomeNavigationScreen
import tss.t.podcast.ui.screens.main.tabDefaults
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.theme.PodcastTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels<OnboardingViewModel>()
    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()
    private val playerViewModel: PlayerViewModel by viewModels<PlayerViewModel>()

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mainViewModel.popBackStack()
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        setContent {

            PodcastTheme {
                val listState = remember {
                    mutableStateMapOf<BottomBarTab, LazyListState>()
                }
                val pullRefreshState = remember {
                    mutableStateMapOf<BottomBarTab, PullToRefreshState>()
                }
                var tabIndexSelected by remember {
                    mutableIntStateOf(1)
                }

                val isOnboardingFinished by onboardingViewModel.isOnboardingFinished.collectAsState()
                SharedTransitionLayout {
                    when {
                        !isOnboardingFinished.isOnboardingDone -> {
                            OnboardingScreen(
                                modifier = Modifier,
                                viewModel = onboardingViewModel
                            )
                        }

                        !isOnboardingFinished.isSelectedFavourite -> {
                            SelectFavouriteCategoryScreen(
                                viewmodel = onboardingViewModel
                            )
                        }

                        else -> {
                            CompositionLocalProvider(
                                LocalSharedTransitionScope provides this,
                                LocalListStateScope provides listState,
                                LocalPullToRefreshState provides pullRefreshState
                            ) {
                                HomeNavigationScreen(
                                    mainViewModel = mainViewModel,
                                    screenTitle = tabDefaults[tabIndexSelected].title,
                                    selectedTabIndex = tabIndexSelected,
                                    bottomTabs = tabDefaults,
                                    hazeState = remember { HazeState() },
                                    onTabSelected = { tab, index ->
                                        tabIndexSelected = index
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

}

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalListStateScope = compositionLocalOf<SnapshotStateMap<BottomBarTab, LazyListState>> {
    mutableStateMapOf()
}

@OptIn(ExperimentalMaterial3Api::class)
val LocalPullToRefreshState =
    compositionLocalOf<SnapshotStateMap<BottomBarTab, PullToRefreshState>> {
        mutableStateMapOf()
    }