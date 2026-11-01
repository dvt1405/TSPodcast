package tss.t.podcast.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import tss.t.podcast.ui.navigations.TSHomeRouter
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.sharedlibrary.analytics.Analytics
import tss.t.sharedlibrary.analytics.AppEvents.Screen

/**
 * GA4 `screen_view` for every destination of the two nav hosts (the app shell and the Home
 * tabs inside `Main`). Firebase's automatic screen reporting is off (sharedFirebase manifest):
 * with one Activity it would only ever say "MainActivity".
 */
@Composable
fun TrackScreens(outer: NavHostController, inner: NavHostController) {
    val outerEntry by outer.currentBackStackEntryAsState()
    val innerEntry by inner.currentBackStackEntryAsState()
    val screen = screenName(outerEntry?.destination?.route, innerEntry?.destination?.route)
    LaunchedEffect(screen) {
        if (screen != null) Analytics.screen(screen)
    }
}

/**
 * A sheet or dialog shown over a screen: `screen_view` for it, and for the screen below again
 * when it closes (so ad and error events after it carry the right `screen`).
 */
@Composable
fun TrackScreen(name: String) {
    DisposableEffect(name) {
        val below = Analytics.currentScreen
        Analytics.screen(name)
        onDispose {
            if (below != null && Analytics.currentScreen == name) Analytics.screen(below)
        }
    }
}

/** Screen name of a pair of routes; null when the screen logs its own steps (onboarding). */
internal fun screenName(outerRoute: String?, innerRoute: String?): String? = when (outerRoute) {
    null -> null
    TSRouter.Main.route -> when (innerRoute) {
        TSHomeRouter.Favourite.route -> Screen.FAVOURITES
        TSHomeRouter.Search.route -> Screen.SEARCH
        TSHomeRouter.Radio.route -> Screen.RADIO
        else -> Screen.DISCOVER
    }
    // OnboardingViewModel logs onboarding_1 … onboarding_n per page.
    TSRouter.Onboarding.route -> null
    TSRouter.SelectFavouriteCategory.route -> Screen.ONBOARDING_CATEGORIES
    TSRouter.PodcastDetail.route -> Screen.PODCAST_DETAIL
    TSRouter.Player.route -> Screen.PLAYER
    TSRouter.Settings.route -> Screen.SETTINGS
    else -> outerRoute.lowercase()
}
