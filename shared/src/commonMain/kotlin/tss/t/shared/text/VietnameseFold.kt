package tss.t.shared.text

/**
 * Text reduced for accent-insensitive matching (M6 §3.3, N5): "Đọc truyện" and "doc truyen"
 * fold to the same "doc truyen".
 *
 * Steps: canonical decomposition (NFD, [canonicalDecompose]), combining marks U+0300–U+036F
 * removed, `đ`/`Đ` (which NFD keeps as one letter) → `d`, lowercase, runs of whitespace become
 * one space, trimmed. A precomposed and an already decomposed string fold alike.
 */
object VietnameseFold {

    fun fold(text: String): String {
        if (text.isEmpty()) return ""
        val decomposed = canonicalDecompose(text)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (char in decomposed) {
            when {
                char in COMBINING_MARKS -> Unit
                char.isWhitespace() -> pendingSpace = out.isNotEmpty()
                else -> {
                    if (pendingSpace) {
                        out.append(' ')
                        pendingSpace = false
                    }
                    out.append(if (char == 'đ' || char == 'Đ') 'd' else char.lowercaseChar())
                }
            }
        }
        return out.toString()
    }

    private val COMBINING_MARKS = '̀'..'ͯ'
}

/**
 * Unicode canonical decomposition (NFD): `java.text.Normalizer` on Android,
 * `NSString.decomposedStringWithCanonicalMapping` on iOS.
 */
internal expect fun canonicalDecompose(text: String): String
