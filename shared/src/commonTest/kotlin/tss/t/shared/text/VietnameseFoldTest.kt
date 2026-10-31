package tss.t.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** M6-AC8: accent and case insensitive text for "Tìm trong show". */
class VietnameseFoldTest {

    @Test
    fun removesAccentsAndCase() {
        assertEquals("doc truyen", VietnameseFold.fold("Đọc truyện"))
        assertEquals("dai", VietnameseFold.fold("ĐÀI"))
        assertEquals("thoi su 6h", VietnameseFold.fold("Thời sự 6h"))
        assertEquals("nguoi", VietnameseFold.fold("NGƯỜI"))
        assertEquals("tieng viet co dau: a a a a a a e o o u u y d", VietnameseFold.fold("Tiếng Việt có dấu: ă â á à ả ã ê ô ơ ư ứ ỹ đ"))
    }

    @Test
    fun collapsesAndTrimsWhitespace() {
        assertEquals("kinh te", VietnameseFold.fold("  Kinh   tế "))
        assertEquals("a b c", VietnameseFold.fold("a\tb\n\nc"))
        assertEquals("", VietnameseFold.fold("   "))
        assertEquals("", VietnameseFold.fold(""))
    }

    @Test
    fun decomposedInputFoldsLikePrecomposed() {
        // "Đọc truyện" with every mark as a separate combining character.
        val decomposed = "Đọc truyện"
        val precomposed = "Đọc truyện"

        assertEquals(VietnameseFold.fold(precomposed), VietnameseFold.fold(decomposed))
        assertEquals("doc truyen", VietnameseFold.fold(decomposed))
    }

    @Test
    fun keepsDigitsPunctuationAndOtherScripts() {
        assertEquals("tap 12: on dinh (p.1)", VietnameseFold.fold("Tập 12: Ổn định (P.1)"))
        assertEquals("cafe", VietnameseFold.fold("Café"))
    }
}
