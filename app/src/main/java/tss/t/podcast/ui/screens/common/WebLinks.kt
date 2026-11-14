package tss.t.podcast.ui.screens.common

import android.content.Context
import android.content.Intent
import java.text.Normalizer

/**
 * Share links on the TSPodcast landing site. `/podcast/<id>-<slug>` and `/episode/<id>-<slug>`
 * (optionally under `/en/`) open the app through Android App Links (`/.well-known/assetlinks.json`
 * on the site); without the app the site shows an "open in the app" page with the store links.
 */
object WebLinks {
    const val WEB_HOST = "tspodcast.web.app"
    const val WEB_BASE = "https://$WEB_HOST"

    /** A shared web link this app opens: a show or an episode, by PodcastIndex id. */
    sealed interface Target {
        data class Podcast(val feedId: Long) : Target
        data class Episode(val episodeId: Long) : Target
    }

    /** `https://tspodcast.web.app/podcast/123-slug` (or `/en/podcast/…`) → [Target.Podcast] (123), else null. */
    fun parse(uri: android.net.Uri?): Target? {
        uri ?: return null
        if (uri.scheme != "https" && uri.scheme != "http") return null
        if (!uri.host.equals(WEB_HOST, ignoreCase = true)) return null
        val segments = uri.pathSegments.let { if (it.firstOrNull() == "en") it.drop(1) else it }
        if (segments.size < 2) return null
        val id = segments[1].substringBefore('-').toLongOrNull()?.takeIf { it > 0 } ?: return null
        return when (segments[0]) {
            "podcast" -> Target.Podcast(id)
            "episode" -> Target.Episode(id)
            else -> null
        }
    }

    fun podcast(id: Long, title: String?) = "$WEB_BASE/podcast/$id-${slugify(title)}"

    fun episode(id: Long, title: String?) = "$WEB_BASE/episode/$id-${slugify(title)}"

    fun slugify(input: String?, maxLength: Int = 80): String {
        val slug = Normalizer.normalize(input.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(maxLength)
            .trimEnd('-')
        return slug.ifEmpty { "podcast" }
    }
}

/** System share sheet with [text] (a title and a link). */
fun Context.shareText(text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
