package tss.t.podcast.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tss.t.podcast.ui.navigations.TSHomeRouter
import tss.t.podcast.ui.navigations.TSRouter

class ScreenTrackingTest {

    @Test
    fun homeTabsAreNamedByTheInnerRoute() {
        assertEquals("discover", screenName(TSRouter.Main.route, null))
        assertEquals("discover", screenName(TSRouter.Main.route, TSHomeRouter.Discover.route))
        assertEquals("favourites", screenName(TSRouter.Main.route, TSHomeRouter.Favourite.route))
        assertEquals("search", screenName(TSRouter.Main.route, TSHomeRouter.Search.route))
        assertEquals("radio", screenName(TSRouter.Main.route, TSHomeRouter.Radio.route))
    }

    @Test
    fun shellScreensIgnoreTheInnerRoute() {
        assertEquals("player", screenName(TSRouter.Player.route, TSHomeRouter.Search.route))
        assertEquals("podcast_detail", screenName(TSRouter.PodcastDetail.route, null))
        assertEquals("settings", screenName(TSRouter.Settings.route, null))
        assertEquals("onboarding_categories", screenName(TSRouter.SelectFavouriteCategory.route, null))
    }

    @Test
    fun onboardingPagesLogThemselves() {
        assertNull(screenName(TSRouter.Onboarding.route, null))
        assertNull(screenName(null, null))
    }
}
