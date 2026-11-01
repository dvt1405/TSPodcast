@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import tss.t.podcast.ui.theme.Motion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.ads.AdRenderer
import tss.t.ads.AdsController
import tss.t.ads.LocalAdRenderer
import tss.t.ads.LocalAdsController
import tss.t.podcast.ads.AppAdsHostSignals
import androidx.core.net.toUri
import tss.t.coreapi.Constants
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.podcast.ui.model.HomeEvent
import tss.t.podcast.ui.navigations.TSHomeRouter
import tss.t.podcast.ui.navigations.TSNavGraph
import tss.t.podcast.ui.screens.settings.PrivacyConsentSheet
import tss.t.podcast.shortcuts.AppShortcuts
import tss.t.podcast.ui.screens.common.LocalShowActions
import tss.t.podcast.ui.screens.common.ShowActions
import tss.t.podcast.ui.screens.common.ShowRef
import tss.t.podcast.ui.screens.common.WebLinks
import tss.t.podcast.ui.screens.settings.appVersion
import tss.t.podcast.ui.screens.settings.sendEmail
import tss.t.sharedfirebase.PrivacyConsent
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.podcast.ui.screens.main.BottomBarTab
import tss.t.podcast.ui.screens.player.PlayerViewModel
import tss.t.podcast.ui.screens.player.widgets.PlayerWidgetMain
import tss.t.podcast.ui.screens.podcastsdetail.PodcastViewModel
import tss.t.podcast.ui.theme.PodcastTheme
import tss.t.securedtoken.NativeLib
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
    lateinit var adsController: AdsController

    @Inject
    lateinit var adRenderer: AdRenderer

    @Inject
    lateinit var adsHostSignals: AppAdsHostSignals

    @Inject
    lateinit var tsAnalytics: TSAnalytics

    @Inject
    lateinit var tsRemoteConfig: TSFirebaseRemoteConfig

    @Inject
    lateinit var privacyConsent: PrivacyConsent

    @Inject
    lateinit var playerManager: tss.t.sharedplayer.player.PlayerManager

    @Inject
    lateinit var sharedPref: tss.t.core.storage.SharedPref

    @Inject
    lateinit var uiPrefs: tss.t.core.storage.UiPrefs

    @Inject
    lateinit var languagePrefs: tss.t.core.storage.LanguagePrefs

    @Inject
    lateinit var api: tss.t.coreapi.API

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private var navHostController: NavHostController? = null
    private var homeInnerNavHostController: NavHostController? = null
    private var onBackPressedCallback: OnBackPressedCallback? = null

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )
        if (!NativeLib.isAvailable) {
            // Every API call needs libsecuredtoken.so, and the
            // view models below build the API on first access. Without the
            // library the only useful thing to show is how to get a full install.
            setContent {
                PodcastTheme {
                    BrokenInstallScreen(onOpenStore = ::openStoreListing)
                }
            }
            return
        }
        val backCallback = object : OnBackPressedCallback(true) {
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
        onBackPressedCallback = backCallback
        onBackPressedDispatcher.addCallback(this, backCallback)
        AppShortcuts.publish(this)
        // App language: follows the main podcast location ("Theo khu vực") or the Settings choice,
        // and is applied again whenever it changes (a new main location while following it).
        lifecycleScope.launch {
            languagePrefs.language.collect { language ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    AppLocale.apply(this@MainActivity, language.tag)
                } else if (!AppLocale.matches(resources.configuration, language.tag)) {
                    recreate()
                }
            }
        }
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()
        // UMP consent (form if required) -> Mobile Ads SDK init -> preload.
        // Inside the NativeLib guard on purpose: the broken-install screen
        // above has nothing to monetise.
        adsController.start(this)
        setContent {
            val listState = remember {
                mutableStateMapOf<BottomBarTab, LazyListState>()
            }
            val pullRefreshState = remember {
                mutableStateMapOf<BottomBarTab, PullToRefreshState>()
            }
            val navGraph = rememberNavController()
            navHostController = navGraph
            DisposableEffect(navGraph) {
                // Lets the ad policy know when the full-screen Player is visible.
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    adsHostSignals.currentRoute = destination.route
                }
                navGraph.addOnDestinationChangedListener(listener)
                onDispose { navGraph.removeOnDestinationChangedListener(listener) }
            }
            val innerNavGraph = rememberNavController()
            homeInnerNavHostController = innerNavGraph
            val showActions = remember {
                ShowActions(
                    hide = { mainViewModel.hideShow(it.feedId, it.title, it.image) },
                    report = { reportShow(it) }
                )
            }
            val liquidGlass by uiPrefs.liquidGlass.collectAsState()
            PodcastTheme {
                val windowInset = WindowInsets.systemBars.asPaddingValues()
                val miniPlayerState by playerViewModel.miniPlayerState.collectAsState()
                SharedTransitionLayout {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this,
                        LocalListStateScope provides listState,
                        LocalPullToRefreshState provides pullRefreshState,
                        LocalAnalyticsScope provides tsAnalytics,
                        LocalAdRenderer provides adRenderer,
                        LocalAdsController provides adsController,
                        LocalRemoteConfigScope provides tsRemoteConfig,
                        LocalRemoteConfig provides tsRemoteConfig,
                        LocalShowActions provides showActions,
                        tss.t.sharedlibrary.ui.glass.LocalLiquidGlass provides liquidGlass
                    ) {
                        TSNavGraph(
                            innerNavHost = innerNavGraph,
                            navHost = navGraph
                        )

                        MiniPlayer(
                            miniPlayerState = miniPlayerState,
                            parentNavHost = navGraph,
                            innerPadding = windowInset,
                            playerViewModel = playerViewModel
                        )

                        // New users answer on the onboarding privacy page; the sheet is only
                        // for people who finished onboarding before that page existed.
                        val onboardingStep by onboardingViewModel.onboardingStep.collectAsState()
                        if (onboardingStep.isOnboardingDone) {
                            PrivacyConsentSheet(privacyConsent)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.event.collect {
                when (it) {
                    HomeEvent.ExitApp -> exitToHome()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun openStoreListing() {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri()
        )
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            // No Play Store app (e.g. a device without GMS): fall back to the web listing.
            try {
                startActivity(web)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.broken_install_message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        // Reached from onNewIntent too; playerViewModel needs the API, which
        // needs libsecuredtoken.so. See the guard in onCreate.
        if (!NativeLib.isAvailable) return
        // Opened from the media notification or a link: the user came to
        // listen, so no App Open ad for this foreground (audio-first rule).
        // onNewIntent runs before onResume, where the App Open decision is made.
        if (intent.action == Constants.ACTION_START_FROM_NOTIFICATION || intent.data != null) {
            adsController.onExternalLaunch()
        }
        if (handleShortcut(intent.action)) return
        if (intent.action == Intent.ACTION_VIEW) {
            tss.t.podcast.ui.screens.common.WebLinks.parse(intent.data)?.let(::openWebLink)
            return
        }
        if (intent.action != Constants.ACTION_START_FROM_NOTIFICATION) return
        val data = intent.data ?: return
        // This activity is exported, so any app can send this action. Match on
        // the parsed scheme/host/path rather than a substring of the whole URI,
        // which "http://evil.example/?x=tss://ts.podcast/playing" also satisfied.
        val expected = Constants.DEEPLINK_CURRENT_PLAYING.toUri()
        if (data.scheme != expected.scheme || data.host != expected.host) return
        if (data.path?.trimEnd('/') != expected.path?.trimEnd('/')) return

        val mediaId = runCatching { data.getQueryParameter(Constants.QUERY_MEDIA_ITEM_NAME) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return
        playerViewModel.onRestoreFromNotification(mediaId)
    }

    /**
     * A shared podcast / episode link: the show opens in its detail screen, an episode starts
     * playing in the Player. Waits for the main screen (cold start, onboarding) for up to 20 s.
     */
    private fun openWebLink(target: tss.t.podcast.ui.screens.common.WebLinks.Target) {
        lifecycleScope.launch {
            val podcastAndEpisode = withContext(Dispatchers.IO) {
                runCatching {
                    when (target) {
                        is tss.t.podcast.ui.screens.common.WebLinks.Target.Podcast -> {
                            val feed = (api.getPodcastByFeedId(target.feedId.toString(), null) as? TSDataState.Success)
                                ?.data?.feed?.takeIf { it.id > 0 }
                            feed?.let { Podcast.fromFeed(it) to null }
                        }
                        is tss.t.podcast.ui.screens.common.WebLinks.Target.Episode -> {
                            val episode = (api.getEpisodeById(target.episodeId) as? TSDataState.Success)?.data?.episode
                            val feed = episode?.let {
                                (api.getPodcastByFeedId(it.feedId.toString(), null) as? TSDataState.Success)?.data?.feed
                            }
                            if (episode != null && feed != null) Podcast.fromFeed(feed) to episode else null
                        }
                    }
                }.getOrNull()
            }
            if (podcastAndEpisode == null) {
                Toast.makeText(this@MainActivity, R.string.web_link_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val nav = withTimeoutOrNull(20_000) {
                var controller = navHostController
                while (controller?.currentBackStackEntry?.destination?.route != TSRouter.Main.route) {
                    delay(100)
                    controller = navHostController
                }
                controller
            } ?: return@launch
            val (podcast, episode) = podcastAndEpisode
            mainViewModel.setCurrentPodcast(podcast, from = "web_link")
            if (episode == null) {
                podcastViewModel.getEpisodes(podcast)
                nav.navigate(TSRouter.PodcastDetail.route)
            } else {
                playerViewModel.playerEpisode(episode = episode, podcast = podcast, listItem = listOf(episode))
                nav.navigate(TSRouter.Player.route)
            }
        }
    }

    /** Launcher shortcuts (iOS App Shortcuts parity). True when [action] was one of them. */
    private fun handleShortcut(action: String?): Boolean {
        when (action) {
            AppShortcuts.ACTION_CONTINUE, AppShortcuts.ACTION_PAUSE, AppShortcuts.ACTION_SLEEP_30 ->
                tss.t.sharedlibrary.analytics.Analytics.log(tss.t.sharedlibrary.analytics.AppEvents.shortcutInvoked(action.orEmpty().substringAfterLast('.').lowercase()))
        }
        when (action) {
            AppShortcuts.ACTION_CONTINUE -> {
                adsController.onExternalLaunch()
                val lastId = sharedPref.get<String>(tss.t.podcasts.HistoryRepository.EXTRA_CURRENT_ITEM_ID)
                if (lastId.isNullOrBlank()) {
                    Toast.makeText(this, R.string.shortcut_nothing_to_continue, Toast.LENGTH_SHORT).show()
                } else {
                    playerViewModel.onRestoreFromNotification(lastId)
                }
            }

            AppShortcuts.ACTION_PAUSE -> {
                adsController.onExternalLaunch()
                playerManager.player.pause()
            }

            AppShortcuts.ACTION_SLEEP_30 -> {
                adsController.onExternalLaunch()
                playerManager.setSleepTimer(tss.t.sharedplayer.playback.SleepTimerOption.Minutes(30), origin = "shortcut")
                Toast.makeText(this, R.string.shortcut_sleep_toast, Toast.LENGTH_SHORT).show()
            }

            else -> return false
        }
        return true
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    /**
     * POST_NOTIFICATIONS is declared in the manifest but was never requested, so on API 33+ the
     * media and FCM notifications were silently dropped on a fresh install.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        // Previously this also returned when shouldShowRequestPermissionRationale
        // was true, i.e. after the first denial the app neither asked again nor
        // showed any rationale. Asking again is correct here: the system itself
        // caps repeated prompts, and notifications are the app's primary surface
        // for playback controls.
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Hands the back press back to the system instead of calling finish(), so the platform can run
     * its own back-to-home animation (predictive back is on by default from targetSdk 36).
     */
    private fun exitToHome() {
        onBackPressedCallback?.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
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

    /** "Báo cáo nội dung" (parity with iOS M6): an email with the show id and a web link, sent by the user. */
    private fun reportShow(show: ShowRef) {
        val fromPlayer = tss.t.sharedlibrary.analytics.Analytics.currentScreen == tss.t.sharedlibrary.analytics.AppEvents.Screen.PLAYER
        tss.t.sharedlibrary.analytics.Analytics.log(tss.t.sharedlibrary.analytics.AppEvents.contentReport(if (fromPlayer) "episode" else "feed", show.feedId))
        sendEmail(
            subject = getString(R.string.report_subject, show.title),
            body = getString(
                R.string.report_body,
                show.title,
                show.feedId.toString(),
                WebLinks.podcast(show.feedId, show.title),
                appVersion()
            )
        )
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
private fun BrokenInstallScreen(onOpenStore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.broken_install_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.broken_install_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenStore) {
            Text(text = stringResource(R.string.broken_install_action))
        }
    }
}

@Composable
private fun MiniPlayer(
    miniPlayerState: PlayerViewModel.MiniPlayerState,
    parentNavHost: NavHostController,
    innerPadding: PaddingValues,
    playerViewModel: PlayerViewModel,
) {
    val backStackEntry by parentNavHost.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val paddingBottom = when (route) {
        TSRouter.Main.route -> innerPadding.calculateBottomPadding() + 70.dp
        else -> innerPadding.calculateBottomPadding()
    }
    // Read in the layout phase (offset below): moving with the bottom bar no longer recomposes.
    val animatePadding = animateDpAsState(
        paddingBottom,
        animationSpec = tween(Motion.LONG, easing = Motion.EmphasizedDecelerate),
        label = "MiniPlayerPadding"
    )
    // The glass capsule draws its own shadow outside its shape; a rectangular elevation shadow
    // here would show through the translucent capsule as a grey frame.
    val glass = tss.t.sharedlibrary.ui.glass.LocalLiquidGlass.current
    val currentMediaItem = miniPlayerState.currentMediaItem
    // Keeps the last item on screen while the bar slides away after playback is cleared.
    var shownItem by remember { mutableStateOf(currentMediaItem) }
    if (currentMediaItem != null) shownItem = currentMediaItem

    Box(modifier = Modifier.fillMaxSize()) {
        // Slides down with the full Player rising over it, and back up when it closes.
        AnimatedVisibility(
            visible = currentMediaItem != null && route != TSRouter.Player.route,
            modifier = Modifier
                .zIndex(100f)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -animatePadding.value.roundToPx()) },
            enter = slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it } +
                    fadeIn(tween(Motion.MEDIUM)),
            exit = slideOutVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedAccelerate)) { it } +
                    fadeOut(tween(Motion.SHORT)),
        ) {
            // Keyed by media id: the session hands back an equal-but-new MediaItem now and
            // then (checkFavourite), which used to replay the whole cross-fade.
            AnimatedContent(
                targetState = shownItem,
                contentKey = { it?.mediaId },
                transitionSpec = {
                    fadeIn(tween(Motion.MEDIUM, 60)) togetherWith fadeOut(tween(Motion.SHORT)) using
                            SizeTransform(clip = false)
                },
                label = "MiniPlayerItem"
            ) { item ->
                if (item == null) return@AnimatedContent
                PlayerWidgetMain(
                    modifier = Modifier.then(if (glass) Modifier else Modifier.shadow(1.dp)),
                    title = item.mediaMetadata.title.toString(),
                    image = item.mediaMetadata.artworkUri.toString(),
                    description = item.mediaMetadata.description.toString(),
                    id = item.mediaId,
                    playing = miniPlayerState.isPlaying,
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