package tss.t.shared.preferences

import kotlinx.coroutines.test.runTest
import tss.t.shared.network.PodcastIndexJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** M1-AC2: same order of checks as `TSNavGraph`. */
class StartDestinationTest {

    private suspend fun destination(onboardingFinished: Boolean?, hasSelectFavourite: Boolean?): StartDestination {
        val store = InMemoryKeyValueStore()
        onboardingFinished?.let { store.putBoolean(AppPreferences.KEY_ONBOARDING_FINISHED, it) }
        hasSelectFavourite?.let { store.putBoolean(AppPreferences.KEY_HAS_SELECT_FAVOURITE_CATEGORY, it) }
        return AppPreferences(store, PodcastIndexJson).startDestination()
    }

    @Test
    fun emptyStoreStartsAtOnboarding() = runTest {
        assertEquals(StartDestination.Onboarding, destination(null, null))
    }

    @Test
    fun onboardingFinishedOnlyGoesToCategorySelection() = runTest {
        assertEquals(StartDestination.SelectFavouriteCategory, destination(true, null))
        assertEquals(StartDestination.SelectFavouriteCategory, destination(true, false))
    }

    @Test
    fun bothFlagsGoToMain() = runTest {
        assertEquals(StartDestination.Main, destination(true, true))
    }

    @Test
    fun favouritesWithoutOnboardingStillStartAtOnboarding() = runTest {
        assertEquals(StartDestination.Onboarding, destination(null, true))
        assertEquals(StartDestination.Onboarding, destination(false, true))
    }
}
