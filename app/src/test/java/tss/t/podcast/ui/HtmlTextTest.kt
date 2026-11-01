package tss.t.podcast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tss.t.podcast.ui.screens.common.htmlToText

/** The plain-text fast path of [htmlToText] (the HTML path needs the Android framework). */
class HtmlTextTest {

    @Test
    fun nullAndEmptyBecomeEmpty() {
        assertEquals("", htmlToText(null))
        assertEquals("", htmlToText(""))
    }

    @Test
    fun plainTextSkipsTheParser() {
        val plain = "Golden State Warriors News Today delivers fast, focused daily coverage."
        assertSame(plain, htmlToText(plain))
    }
}
