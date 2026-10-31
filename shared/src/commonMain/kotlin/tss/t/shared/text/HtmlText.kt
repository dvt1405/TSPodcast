package tss.t.shared.text

/**
 * HTML descriptions from PodcastIndex as plain text, for list cells (Android renders them with
 * `HtmlCompat.fromHtml`). Tags are removed, `<br>` and the end of `</p>`, `</li>`, `</div>`
 * become line breaks, entities are decoded, runs of spaces collapse and blank lines go away.
 */
object HtmlText {

    fun toPlainText(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val text = html
            .replace(INVISIBLE_BLOCK, "")
            .replace(SOURCE_WHITESPACE, " ")
            .replace(LINE_BREAK, "\n")
            .replace(TAG, "")
        return decodeEntities(text)
            .lineSequence()
            .map { it.replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
        val name = match.groupValues[1]
        val decoded = when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePointToString)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointToString)
            else -> NAMED_ENTITIES[name]
        }
        decoded ?: match.value
    }

    private fun codePointToString(codePoint: Int): String? = when {
        codePoint == 0 || codePoint > 0x10FFFF || codePoint in 0xD800..0xDFFF -> null
        codePoint < 0x10000 -> codePoint.toChar().toString()
        else -> {
            val offset = codePoint - 0x10000
            charArrayOf(
                (0xD800 + (offset shr 10)).toChar(),
                (0xDC00 + (offset and 0x3FF)).toChar(),
            ).concatToString()
        }
    }

    private val INVISIBLE_BLOCK = Regex(
        "<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val SOURCE_WHITESPACE = Regex("\\s+")
    private val LINE_BREAK = Regex("<br\\s*/?>|</(p|li|div)\\s*>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("</?[a-zA-Z!][^>]*>")
    private val SPACES = Regex("[ \\t\\u00A0]+")
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ndash" to "–",
        "mdash" to "—",
        "hellip" to "…",
        "rsquo" to "’",
        "lsquo" to "‘",
        "rdquo" to "”",
        "ldquo" to "“",
        "copy" to "©",
    )
}
