package tss.t.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tss.t.core.models.ListeningHistory
import java.util.Calendar
import java.util.TimeZone

class ListeningHistoryRulesTest {

    private fun entry(
        id: String = "42",
        position: Long = 0L,
        duration: Long = 0L,
        finished: Boolean = false,
        at: Long = 0L,
        image: String? = null,
        url: String? = "https://example.com/42.mp3",
        podcast: String? = null,
        feedId: Long? = null,
    ) = ListeningHistory(
        mediaId = id,
        feedId = feedId,
        title = "Episode $id",
        podcastTitle = podcast,
        image = image,
        mediaUrl = url,
        positionMs = position,
        durationMs = duration,
        finished = finished,
        lastPlayedAt = at,
    )

    // -------------------------------------------------------------------------------------
    // Save: merging player snapshots
    // -------------------------------------------------------------------------------------

    @Test
    fun firstSnapshotIsStoredAsItIs() {
        val merged = ListeningHistoryRules.merge(null, entry(position = 300_000L, duration = 3_600_000L, at = 1L))
        assertEquals(300_000L, merged.positionMs)
        assertEquals(3_600_000L, merged.durationMs)
        assertFalse(merged.finished)
        assertTrue(ListeningHistoryRules.isResumable(merged))
    }

    @Test
    fun unknownDurationAndMissingDetailsKeepThePreviousValues() {
        val previous = entry(
            position = 60_000L, duration = 3_600_000L, image = "cover.jpg", podcast = "Show", feedId = 7L
        )
        // A restored item before it is prepared: no duration, no artwork in the snapshot.
        val merged = ListeningHistoryRules.merge(previous, entry(position = 90_000L, url = null))
        assertEquals(90_000L, merged.positionMs)
        assertEquals(3_600_000L, merged.durationMs)
        assertEquals("cover.jpg", merged.image)
        assertEquals("Show", merged.podcastTitle)
        assertEquals(7L, merged.feedId)
        assertEquals("https://example.com/42.mp3", merged.mediaUrl)
    }

    @Test
    fun lessThanThirtySecondsLeftCountsAsHeard() {
        val merged = ListeningHistoryRules.merge(null, entry(position = 3_580_000L, duration = 3_600_000L))
        assertTrue(merged.finished)
        assertFalse(ListeningHistoryRules.isResumable(merged))
        assertEquals(0L, ListeningHistoryRules.startPositionMs(merged))
        assertEquals(1f, ListeningHistoryRules.progress(merged))
    }

    @Test
    fun aReplayStaysHeardDuringItsFirstSecondsThenBecomesResumable() {
        val heard = entry(position = 3_590_000L, duration = 3_600_000L, finished = true)
        val replayStart = ListeningHistoryRules.merge(heard, entry(position = 1_000L, duration = 3_600_000L))
        assertTrue(replayStart.finished)
        val replaying = ListeningHistoryRules.merge(replayStart, entry(position = 120_000L, duration = 3_600_000L))
        assertFalse(replaying.finished)
        assertEquals(120_000L, ListeningHistoryRules.startPositionMs(replaying))
    }

    @Test
    fun theFirstSecondsAreNotWorthResuming() {
        val merged = ListeningHistoryRules.merge(null, entry(position = 3_000L, duration = 3_600_000L))
        assertFalse(merged.finished)
        assertFalse(ListeningHistoryRules.isResumable(merged))
        assertEquals(0L, ListeningHistoryRules.startPositionMs(merged))
    }

    @Test
    fun negativePositionsAreClamped() {
        assertEquals(0L, ListeningHistoryRules.merge(null, entry(position = -5L)).positionMs)
    }

    // -------------------------------------------------------------------------------------
    // Restore: progress and time left
    // -------------------------------------------------------------------------------------

    @Test
    fun progressAndRemainingTime() {
        val half = entry(position = 1_800_000L, duration = 3_600_000L)
        assertEquals(0.5f, ListeningHistoryRules.progress(half), 0.0001f)
        assertEquals(1_800_000L, ListeningHistoryRules.remainingMs(half))
        val unknown = entry(position = 60_000L, duration = 0L)
        assertEquals(0f, ListeningHistoryRules.progress(unknown))
        assertEquals(0L, ListeningHistoryRules.remainingMs(unknown))
        assertEquals(60_000L, ListeningHistoryRules.startPositionMs(unknown))
    }

    // -------------------------------------------------------------------------------------
    // Grouping by day
    // -------------------------------------------------------------------------------------

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).run {
            clear()
            set(year, month - 1, day, hour, minute)
            timeInMillis
        }

    @Test
    fun groupsIntoTodayYesterdayAndEarlierDays() {
        val now = at(2026, 11, 1, 9)
        val entries = listOf(
            entry(id = "a", at = at(2026, 11, 1, 0, 5)),
            entry(id = "b", at = at(2026, 10, 31, 23, 59)),
            entry(id = "c", at = at(2026, 11, 1, 8)),
            entry(id = "d", at = at(2026, 10, 28, 12)),
            entry(id = "e", at = at(2026, 10, 31, 0, 0)),
            entry(id = "f", at = at(2025, 12, 31, 22)),
        )
        val days = ListeningHistoryRules.groupByDay(entries, now, zone)

        assertEquals(
            listOf(HistoryDay.Kind.Today, HistoryDay.Kind.Yesterday, HistoryDay.Kind.Earlier, HistoryDay.Kind.Earlier),
            days.map { it.kind }
        )
        assertEquals(listOf("c", "a"), days[0].entries.map { it.mediaId })
        assertEquals(listOf("b", "e"), days[1].entries.map { it.mediaId })
        assertEquals(listOf("d"), days[2].entries.map { it.mediaId })
        assertEquals(listOf("f"), days[3].entries.map { it.mediaId })
        assertEquals(at(2026, 10, 28, 0), days[2].dayStartMillis)
    }

    @Test
    fun aTimeInTheFutureCountsAsToday() {
        val now = at(2026, 11, 1, 9)
        val days = ListeningHistoryRules.groupByDay(listOf(entry(at = at(2026, 11, 3, 10))), now, zone)
        assertEquals(1, days.size)
        assertEquals(HistoryDay.Kind.Today, days[0].kind)
    }

    @Test
    fun emptyHistoryHasNoDays() {
        assertTrue(ListeningHistoryRules.groupByDay(emptyList(), 0L, zone).isEmpty())
    }
}
