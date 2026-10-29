package tss.t.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HtmlTextTest {

    @Test
    fun paragraphsBreaksAndEntities() {
        assertEquals("A & B\nC's\nD", HtmlText.toPlainText("<p>A &amp; B</p><p>C&#39;s<br/>D</p>"))
    }

    @Test
    fun nullAndEmptyAreEmpty() {
        assertEquals("", HtmlText.toPlainText(null))
        assertEquals("", HtmlText.toPlainText(""))
        assertEquals("", HtmlText.toPlainText("<p> </p>"))
    }

    @Test
    fun numericEntities() {
        assertEquals("A", HtmlText.toPlainText("&#x41;"))
        assertEquals("A", HtmlText.toPlainText("&#65;"))
        assertEquals("🎵", HtmlText.toPlainText("&#x1F3B5;"))
        assertEquals("Tiếng Việt", HtmlText.toPlainText("Ti&#7871;ng Vi&#7879;t"))
    }

    @Test
    fun namedEntities() {
        assertEquals("<a> \"q\" 'x' y", HtmlText.toPlainText("&lt;a&gt;&nbsp;&quot;q&quot; &apos;x&apos; y"))
        assertEquals("&unknown; stays", HtmlText.toPlainText("&unknown; stays"))
    }

    @Test
    fun noTagSurvives() {
        val html = """<div class="x"><a href="https://e.com?a=1&amp;b=2">Link</a> <b>bold</b><img src="i.png"/></div>
            |<ul><li>One</li><li>Two</li></ul><!-- comment --><script>alert(1)</script><style>p{}</style>""".trimMargin()

        val text = HtmlText.toPlainText(html)

        assertEquals("Link bold\nOne\nTwo", text)
        assertFalse(text.contains('<'))
    }

    @Test
    fun whitespaceCollapses() {
        assertEquals("a b\nc", HtmlText.toPlainText("  a \n\t b <br>   <br>  c  "))
    }
}
