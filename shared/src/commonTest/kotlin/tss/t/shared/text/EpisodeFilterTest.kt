package tss.t.shared.text

import tss.t.shared.model.Episode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** M6-AC8: matching every word without accents, in titles and HTML descriptions. */
class EpisodeFilterTest {

    private val episodes = listOf(
        Episode(id = 1, title = "Đọc truyện đêm khuya: Chiếc lá cuối cùng", description = "<p>Truyện ngắn</p>"),
        Episode(id = 2, title = "Thời sự 6h", description = "<p>Tin <b>kinh&nbsp;tế</b> và xã hội</p>"),
        Episode(id = 3, title = "Kinh tế vĩ mô", description = null),
        Episode(id = 4, title = "Ca nhạc", description = "Đọc báo cùng bạn"),
    )

    private fun ids(query: String) = EpisodeFilter.index(episodes).filter(query).map { it.id }

    @Test
    fun matchesWithoutAccents() {
        assertEquals(listOf(1L), ids("doc truyen"))
        assertEquals(listOf(1L), ids("DOC TRUYEN"))
    }

    @Test
    fun everyWordMustMatchInAnyOrder() {
        assertEquals(listOf(1L), ids("truyen dem"))
        assertEquals(listOf(1L), ids("dem truyen"))
        assertEquals(emptyList(), ids("truyen thoi"))
    }

    @Test
    fun accentedQueryMatchesToo() {
        assertEquals(listOf(1L, 4L), ids("dọc"))
        assertEquals(listOf(1L, 4L), ids("Đọc"))
    }

    @Test
    fun wordsOnlyInTheHtmlDescriptionMatch() {
        assertEquals(listOf(2L, 3L), ids("kinh te"))
        assertEquals(listOf(2L), ids("xa hoi"))
        assertEquals(emptyList(), ids("nbsp"), "entities are decoded, tags removed")
        assertEquals(emptyList(), ids("<b>"))
    }

    @Test
    fun blankQueryKeepsEverythingInOrder() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(""))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids("   "))
    }

    @Test
    fun noMatchIsEmpty() {
        assertEquals(emptyList(), ids("bong da"))
    }

    @Test
    fun matchingIndicesFollowTheList() {
        val filter = EpisodeFilter.index(episodes)

        assertEquals(listOf(0, 3), filter.matchingIndices("doc"))
        assertEquals(4, filter.size)
    }

    @Test
    fun filtersAThousandEpisodesUnderAHundredMilliseconds() {
        val many = (1..1_000).map { index ->
            Episode(
                id = index.toLong(),
                title = "Tập $index: Đọc truyện đêm khuya số ${index % 37}",
                description = "<p>Nội dung <b>tập $index</b> về kinh tế, xã hội và đời sống người Việt.</p>",
            )
        }
        val filter = EpisodeFilter.index(many)
        repeat(5) { filter.filter("doc truyen dem") } // warm-up

        val elapsed = measureTime { filter.filter("doc truyen so 12") }

        assertTrue(elapsed.inWholeMilliseconds < 100, "took $elapsed")
        assertTrue(filter.filter("doi song nguoi viet").size == 1_000)
    }
}
