package tss.t.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSlotsTest {
    private val settings = AdsSettings.defaults(debug = false).copy(adsEnabled = true)

    private fun positions(count: Int, s: AdsSettings = settings): List<Int> =
        interleaveNativeSlots((1..count).toList(), AdPlacement.PodcastDetailEpisodes, s)
            .mapIndexedNotNull { index, row -> if (row is AdSlotRow) index else null }

    @Test
    fun `slots after item 3 then every 8, capped at 3`() {
        // Items 1..30: ads after 3, 11, 19 (27 would be the 4th, over the cap).
        assertEquals(listOf(3, 12, 21), positions(30))
    }

    @Test
    fun `keys are stable and ordered`() {
        val rows = interleaveNativeSlots((1..30).toList(), AdPlacement.PodcastDetailEpisodes, settings)
            .filterIsInstance<AdSlotRow>()
        assertEquals(listOf("ad-0", "ad-1", "ad-2"), rows.map { it.slotKey })
    }

    @Test
    fun `short lists never end on an ad`() {
        assertEquals(emptyList<Int>(), positions(3))
        assertEquals(listOf(3), positions(4))
    }

    @Test
    fun `disabled placement or kill switch leaves the list untouched`() {
        assertEquals(emptyList<Int>(), positions(30, settings.copy(adsEnabled = false)))
        val off = settings.copy(
            placementsEnabled = settings.placementsEnabled +
                    (AdPlacement.PodcastDetailEpisodes to false)
        )
        assertEquals(emptyList<Int>(), positions(30, off))
    }
}
