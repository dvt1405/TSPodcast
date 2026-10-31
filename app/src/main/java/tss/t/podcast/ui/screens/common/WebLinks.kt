package tss.t.podcast.ui.screens.common

import android.content.Context
import android.content.Intent
import java.text.Normalizer

/**
 * Links into the listening web app (Vercel), same paths and slugs as `web/src/lib/slug.ts`, so a
 * shared podcast or episode opens in a browser for people without the app.
 */
object WebLinks {
    const val WEB_BASE = "https://tspodcast.vercel.app"

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
