package tss.t.podcast.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import tss.t.podcast.ui.screens.common.WebLinks

class WebLinksTest {
    @Test
    fun `share links point to the landing site`() {
        assertEquals("https://tspodcast.web.app/podcast/42-tieng-viet-moi-ngay", WebLinks.podcast(42, "Tiếng Việt mỗi ngày"))
        assertEquals("https://tspodcast.web.app/episode/7-dau-dong-xu", WebLinks.episode(7, "Đầu đồng xu"))
    }

    @Test
    fun `blank titles still give a slug`() {
        assertEquals("https://tspodcast.web.app/podcast/5-podcast", WebLinks.podcast(5, "  "))
    }
}
