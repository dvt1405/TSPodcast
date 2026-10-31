package tss.t.shared.newshub

import tss.t.shared.model.Episode
import tss.t.shared.model.Feed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** M6-AC16: "Điểm tin sáng" picks bulletins by fixed rules, at a fixed clock. */
class MorningBriefTest {

    private var nextId = 1L

    private fun episode(feedId: Long, title: String, hoursAgo: Double, duration: Long = 300, now: Long = NOW) = Episode(
        id = nextId++,
        feedId = feedId,
        title = title,
        datePublished = (now - (hoursAgo * HOUR).toLong()) / 1000,
        duration = duration,
    )

    private fun source(key: String, feedId: Long, rule: NewsBriefRule?, vararg episodes: Episode) = ResolvedSource(
        source = NewsSource(key = key, feedUrl = "https://$key.example.test/rss", confirmedAt = "2026-11-02", brief = rule),
        feed = Feed(id = feedId, title = key),
        episodes = episodes.sortedByDescending { it.datePublished },
    )

    private val thoiSuRule = NewsBriefRule(take = 1, maxAgeHours = 24, preferTitle = "Thời sự 6h", preferTitleBeforeHour = 11)

    private fun MorningBrief.titles() = items.map { it.episode.title }

    @Test
    fun followsConfigOrderAndEachSourceRules() {
        val sources = listOf(
            source("vov_tinnhanh", 2, NewsBriefRule(1, 24), episode(2, "Tin nhanh cũ", 5.0), episode(2, "Tin nhanh mới", 1.0)),
            source("vnexpress_homnay", 5, NewsBriefRule(1, 48), episode(5, "VnExpress 30h", 30.0)),
            source("tuoitre", 6, NewsBriefRule(2, 24), episode(6, "TT 1", 2.0), episode(6, "TT 2", 3.0), episode(6, "TT 3", 4.0)),
            source("vov_kinhte", 4, NewsBriefRule(1, 24), episode(4, "Kinh tế 25h", 25.0)),
            source("baophapluat", 7, NewsBriefRule(1, 24), episode(7, "Pháp luật", 23.9)),
        )

        val brief = MorningBrief.build(sources, NOW)

        assertEquals(listOf("Tin nhanh mới", "VnExpress 30h", "TT 1", "TT 2", "Pháp luật"), brief.titles())
        assertEquals(
            listOf("vov_tinnhanh", "vnexpress_homnay", "tuoitre", "tuoitre", "baophapluat"),
            brief.items.map { it.sourceKey },
            "a source with only a 25-hour-old episode is left out",
        )
        assertEquals(listOf(2L, 5L, 6L, 6L, 7L), brief.items.map { it.feed.id }, "each item carries its own feed")
    }

    @Test
    fun beforeElevenTheMorningBulletinGoesFirst() {
        val sixAm = episode(1, "Thời sự 6h 30/10/2026", 1.0)
        val newer = episode(1, "Tin nóng lúc 6h45", 0.25)
        val brief = MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, sixAm, newer)), NOW)

        assertEquals(7, MorningBrief.vietnamHour(NOW))
        assertEquals(listOf("Thời sự 6h 30/10/2026"), brief.titles())
    }

    @Test
    fun atNoonTheNewestEpisodeIsPicked() {
        val noon = NOW + 5 * HOUR
        val sixAm = episode(1, "Thời sự 6h 30/10/2026", 6.0, now = noon)
        val newer = episode(1, "Thời sự 12h 30/10/2026", 0.1, now = noon)
        val brief = MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, sixAm, newer)), noon)

        assertEquals(12, MorningBrief.vietnamHour(noon))
        assertEquals(listOf("Thời sự 12h 30/10/2026"), brief.titles())
    }

    @Test
    fun elevenSharpNoLongerPrefers() {
        val eleven = NOW + 4 * HOUR
        val sixAm = episode(1, "Thời sự 6h", 5.0, now = eleven)
        val newer = episode(1, "Thời sự 11h", 0.0, now = eleven)

        assertEquals(listOf("Thời sự 11h"), MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, sixAm, newer)), eleven).titles())
        assertEquals(
            listOf("Thời sự 6h"),
            MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, sixAm, newer)), eleven - 1).titles(),
        )
    }

    @Test
    fun preferredTitleIgnoresAccentsAndCase() {
        val sixAm = episode(1, "THOI SU 6H sáng nay", 1.0)
        val newer = episode(1, "Tin khác", 0.5)

        assertEquals(listOf("THOI SU 6H sáng nay"), MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, sixAm, newer)), NOW).titles())
    }

    @Test
    fun withoutAPreferredEpisodeTheNewestIsPicked() {
        val older = episode(1, "Thời sự 18h hôm qua", 13.0)
        val newer = episode(1, "Tin sáng", 1.0)

        assertEquals(listOf("Tin sáng"), MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, older, newer)), NOW).titles())
    }

    @Test
    fun preferredEpisodeOutsideTheWindowIsNotUsed() {
        val stale = episode(1, "Thời sự 6h 28/10", 49.0)
        val recent = episode(1, "Tin sáng", 1.0)

        assertEquals(listOf("Tin sáng"), MorningBrief.build(listOf(source("vov_thoisu", 1, thoiSuRule, stale, recent)), NOW).titles())
    }

    @Test
    fun takeTwoKeepsThePreferredFirstThenTheNewest() {
        val rule = thoiSuRule.copy(take = 2)
        val sixAm = episode(1, "Thời sự 6h", 1.0)
        val a = episode(1, "A", 0.5)
        val b = episode(1, "B", 0.7)

        assertEquals(listOf("Thời sự 6h", "A"), MorningBrief.build(listOf(source("vov_thoisu", 1, rule, sixAm, a, b)), NOW).titles())
    }

    @Test
    fun noSourceInItsWindowGivesAnEmptyBrief() {
        val brief = MorningBrief.build(listOf(source("vov_kinhte", 4, NewsBriefRule(1, 24), episode(4, "Cũ", 30.0))), NOW)

        assertTrue(brief.isEmpty)
        assertEquals(0L, brief.totalDurationSeconds)
        assertTrue(MorningBrief.build(emptyList(), NOW).isEmpty)
    }

    @Test
    fun sourcesWithoutABriefRuleAreSkipped() {
        val brief = MorningBrief.build(listOf(source("hanoi_doctruyen", 8, null, episode(8, "Truyện", 1.0))), NOW)

        assertTrue(brief.isEmpty)
    }

    @Test
    fun totalDurationIsTheSum() {
        val brief = MorningBrief.build(
            listOf(
                source("a", 1, NewsBriefRule(1, 24), episode(1, "A", 1.0, duration = 600)),
                source("b", 2, NewsBriefRule(2, 24), episode(2, "B1", 1.0, duration = 125), episode(2, "B2", 2.0, duration = 0)),
            ),
            NOW,
        )

        assertEquals(725L, brief.totalDurationSeconds)
    }

    @Test
    fun vietnamHourIsUtcPlusSeven() {
        assertEquals(0, MorningBrief.vietnamHour(NOW - 7 * HOUR))
        assertEquals(23, MorningBrief.vietnamHour(NOW - 7 * HOUR - 1))
        assertEquals(10, MorningBrief.vietnamHour(NOW + 4 * HOUR - 1))
    }

    private companion object {
        const val HOUR = 3_600_000L

        /** 2026-10-30 07:00 UTC+7. */
        const val NOW = 1_793_318_400_000L
    }
}
