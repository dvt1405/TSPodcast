@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import tss.t.ads.ApplovinSdkWrapper
import tss.t.ads.BannerAdsManager
import tss.t.ads.LocalBannerAdsManagerScope
import tss.t.ads.LocalNativeAdsManagerScope
import tss.t.ads.NativeAdsManager
import tss.t.coreapi.Constants
import tss.t.featureonboarding.OnboardingScreen
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.featureonboarding.SelectFavouriteCategoryScreen
import tss.t.hazeandroid.HazeState
import tss.t.podcast.ui.model.HomeEvent
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.main.BottomBarTab
import tss.t.podcast.ui.screens.main.HomeNavigationScreen
import tss.t.podcast.ui.screens.main.tabDefaults
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.theme.PodcastTheme
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedfirebase.TSAnalytics
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels<OnboardingViewModel>()
    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()
    private val playerViewModel: PlayerViewModel by viewModels<PlayerViewModel>()

    @Inject
    lateinit var applovinSdkWrapper: ApplovinSdkWrapper

    @Inject
    lateinit var tsAnalytics: TSAnalytics

    @Inject
    lateinit var bannerAdsManager: BannerAdsManager

    @Inject
    lateinit var nativeAdsManager: NativeAdsManager


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
        handleIntent(intent)
        setContent {
            val listState = remember {
                mutableStateMapOf<BottomBarTab, LazyListState>()
            }
            val pullRefreshState = remember {
                mutableStateMapOf<BottomBarTab, PullToRefreshState>()
            }

            PodcastTheme {
                val tabIndexSelected by mainViewModel.tabSelected.collectAsState()

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
                                LocalPullToRefreshState provides pullRefreshState,
                                LocalAnalyticsScope provides tsAnalytics,
                                LocalBannerAdsManagerScope provides bannerAdsManager,
                                LocalNativeAdsManagerScope provides nativeAdsManager
                            ) {
                                HomeNavigationScreen(
                                    mainViewModel = mainViewModel,
                                    screenTitle = tabDefaults[tabIndexSelected].title,
                                    selectedTabIndex = tabIndexSelected,
                                    bottomTabs = tabDefaults,
                                    hazeState = remember { HazeState() },
                                    onTabSelected = { tab, index ->
                                        mainViewModel.onTabSelected(index)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.event.collect {
                when (it) {
                    HomeEvent.ExitApp -> finish()
                    HomeEvent.ToastDoubleClickToExit -> Toast.makeText(
                        this@MainActivity,
                        R.string.double_click_to_exit,
                        Toast.LENGTH_SHORT
                    ).show()

                    else -> {}
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applovinSdkWrapper.loadOpenAds()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (action == Constants.ACTION_START_FROM_NOTIFICATION) {
            val data = intent.data ?: return
            if (data.toString().contains(Constants.DEEPLINK_CURRENT_PLAYING)) {
                val mediaId = data.getQueryParameter(Constants.QUERY_MEDIA_ITEM_NAME)
                playerViewModel.onRestoreFromNotification(mediaId)
            }
        }
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