package tss.t.podcast.ui.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.text.HtmlCompat

/**
 * Plain text of an HTML show/episode description, parsed once per string.
 *
 * List rows used to call HtmlCompat.fromHtml in composition, so every recomposition and every
 * row scrolled into view parsed the description again on the main thread. The result is the
 * same text the rows showed before (they appended the Spanned as plain characters).
 */
@Composable
fun rememberHtmlText(
    html: String?,
    flags: Int = HtmlCompat.FROM_HTML_MODE_LEGACY,
): String = remember(html, flags) { htmlToText(html, flags) }

fun htmlToText(html: String?, flags: Int = HtmlCompat.FROM_HTML_MODE_LEGACY): String {
    if (html.isNullOrEmpty()) return ""
    // Many descriptions are already plain text: skip the parser for those (it would only
    // collapse whitespace, so text with line breaks still goes through it).
    if (html.none { it == '<' || it == '&' || it == '\n' || it == '\r' || it == '\t' }) return html
    return HtmlCompat.fromHtml(html, flags).toString()
}

private val TAGS = Regex("<[^>]*>")

/** Tags removed, as LocationSection did inline (with a new Regex per row per composition). */
@Composable
fun rememberStrippedTags(html: String?): String =
    remember(html) { html.orEmpty().replace(TAGS, "").trim() }
