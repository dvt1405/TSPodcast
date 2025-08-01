@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tss.t.ads.ApplovinSdkWrapper
import tss.t.ads.BannerAdsManager
import tss.t.ads.LocalBannerAdsManagerScope
import tss.t.coreapi.Constants
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.podcast.ui.model.HomeEvent
import tss.t.podcast.ui.navigations.TSHomeRouter
import tss.t.podcast.ui.navigations.TSNavGraph
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.main.BottomBarTab
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.player.widgets.PlayerWidgetMain
import tss.t.podcast.ui.screens.podcastsdetail.PodcastViewModel
import tss.t.podcast.ui.theme.PodcastTheme
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedfirebase.LocalRemoteConfigScope
import tss.t.sharedfirebase.TSAnalytics
import tss.t.sharedfirebase.TSFirebaseRemoteConfig
import tss.t.sharedlibrary.utils.LocalRemoteConfig
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels<OnboardingViewModel>()
    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()
    private val playerViewModel: PlayerViewModel by viewModels<PlayerViewModel>()
    private val podcastViewModel: PodcastViewModel by viewModels<PodcastViewModel>()

    @Inject
    lateinit var applovinSdkWrapper: ApplovinSdkWrapper

    @Inject
    lateinit var tsAnalytics: TSAnalytics

    @Inject
    lateinit var bannerAdsManager: BannerAdsManager

    @Inject
    lateinit var tsRemoteConfig: TSFirebaseRemoteConfig

    private var navHostController: NavHostController? = null
    private var homeInnerNavHostController: NavHostController? = null

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navHostController?.currentBackStackEntry?.destination?.route == TSRouter.Main.route) {
                    if (homeInnerNavHostController?.currentBackStackEntry?.destination?.route == TSHomeRouter.Discover.route) {
                        doubleBackToExit()
                    } else {
                        homeInnerNavHostController?.popBackStack(TSHomeRouter.Discover.route, false)
                    }
                } else {
                    navHostController?.popBackStack()
                }
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
            val navGraph = rememberNavController()
            navHostController = navGraph
            val innerNavGraph = rememberNavController()
            homeInnerNavHostController = innerNavGraph
            PodcastTheme {
                val windowInset = WindowInsets.systemBars.asPaddingValues()
                val playerControlState by playerViewModel.playerControlState.collectAsState()
                SharedTransitionLayout {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this,
                        LocalListStateScope provides listState,
                        LocalPullToRefreshState provides pullRefreshState,
                        LocalAnalyticsScope provides tsAnalytics,
                        LocalBannerAdsManagerScope provides bannerAdsManager,
                        LocalRemoteConfigScope provides tsRemoteConfig,
                        LocalRemoteConfig provides tsRemoteConfig
                    ) {
                        TSNavGraph(
                            innerNavHost = innerNavGraph,
                            navHost = navGraph
                        )

                        MiniPlayer(
                            playerControlState = playerControlState,
                            parentNavHost = navGraph,
                            innerPadding = windowInset,
                            playerViewModel = playerViewModel
                        )
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

    private var _pendingExitApp = false
    private var _doubleBackCount = 0
    private fun doubleBackToExit() {
        lifecycleScope.launch {
            if (!_pendingExitApp) {
                _pendingExitApp = true
                _doubleBackCount++
                mainViewModel.emitEvent(HomeEvent.ToastDoubleClickToExit)
                delay(2_000L)
                _pendingExitApp = false
                _doubleBackCount = 0
                return@launch
            }
            _doubleBackCount++
            if (_doubleBackCount >= 2) {
                mainViewModel.emitEvent(HomeEvent.ExitApp)
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


@Composable
private fun MiniPlayer(
    playerControlState: PlayerViewModel.PlayerControlState,
    parentNavHost: NavHostController,
    innerPadding: PaddingValues,
    playerViewModel: PlayerViewModel,
) {
    val backStackEntry by parentNavHost.currentBackStackEntryAsState()
    val paddingBottom = remember(backStackEntry?.destination?.route) {
        when (backStackEntry?.destination?.route) {
            TSRouter.Main.route -> innerPadding.calculateBottomPadding() + 70.dp
            else -> innerPadding.calculateBottomPadding()
        }
    }

    val animatePadding by animateDpAsState(paddingBottom, label = "MiniPlayerPadding")

    AnimatedContent(
        targetState = playerControlState.currentMediaItem,
        label = "Player"
    ) {
        val currentMediaItem = it
        if (currentMediaItem != null && backStackEntry?.destination?.route != TSRouter.Player.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize()
            ) {
                PlayerWidgetMain(
                    modifier = Modifier
                        .zIndex(100f)
                        .padding(bottom = animatePadding)
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it }
                        )
                        .shadow(1.dp),
                    title = currentMediaItem.mediaMetadata.title.toString(),
                    image = currentMediaItem.mediaMetadata.artworkUri.toString(),
                    description = currentMediaItem.mediaMetadata.description.toString(),
                    id = currentMediaItem.mediaId,
                    playing = playerControlState.isPlaying,
                    playPauseClick = {
                        playerViewModel.onPlayPause()
                    },
                    onClick = {
                        parentNavHost.navigate(TSRouter.Player.route)
                    }
                )
            }
        }
    }
}