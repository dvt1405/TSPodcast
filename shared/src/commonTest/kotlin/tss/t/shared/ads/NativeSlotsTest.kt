package tss.t.shared.ads

import kotlin.test.Test
import kotlin.test.assertEquals

/** Port of Android `NativeSlotsTest`, plus the index form Swift uses. */
class NativeSlotsTest {
    private val settings = AdsSettings.defaults(debug = false)

    private fun positions(count: Int, s: AdsSettings = settings): List<Int> =
        NativeSlots.interleave((1..count).toList(), AdPlacement.PodcastDetailEpisodes, s)
            .mapIndexedNotNull { index, row -> if (row is AdSlotRow) index else null }

    @Test
    fun slotsAfterItem3ThenEvery8CappedAt3() {
        assertEquals(listOf(3, 12, 21), positions(30))
        assertEquals(listOf(2, 10, 18), NativeSlots.afterIndices(30, AdPlacement.PodcastDetailEpisodes, settings))
    }

    @Test
    fun keysAreStableAndOrdered() {
        val rows = NativeSlots.interleave((1..30).toList(), AdPlacement.PodcastDetailEpisodes, settings)
            .filterIsInstance<AdSlotRow>()
        assertEquals(listOf("ad-0", "ad-1", "ad-2"), rows.map { it.slotKey })
        assertEquals("podcast_detail_episodes/ad-0", rows.first().lazyKey)
    }

    @Test
    fun shortListsNeverEndOnAnAd() {
        assertEquals(emptyList(), positions(3))
        assertEquals(listOf(3), positions(4))
        assertEquals(emptyList(), NativeSlots.afterIndices(0, AdPlacement.PodcastDetailEpisodes, settings))
    }

    @Test
    fun disabledPlacementOrKillSwitchLeavesTheListUntouched() {
        assertEquals(emptyList(), positions(30, settings.copy(adsEnabled = false)))
        val off = settings.copy(placementsEnabled = settings.placementsEnabled + (AdPlacement.PodcastDetailEpisodes to false))
        assertEquals(emptyList(), positions(30, off))
        assertEquals(emptyList(), positions(30, settings.copy(nativeMaxPerList = 0)))
    }
}
