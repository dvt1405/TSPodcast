package tss.t.shared.deeplink

/** Where a shared web link points (seo-web-dashboard §A.3 URL shapes). */
sealed class DeepLink {
    /** `/podcast/<feedId>-<slug>`: open the podcast detail. */
    data class Podcast(val feedId: Long) : DeepLink()

    /** `/episode/<episodeId>-<slug>`: play the episode and open its podcast (like a favourite tap). */
    data class Episode(val episodeId: Long) : DeepLink()
}

/** Result of [parseIdSlug]: `123-some-title` → `IdSlug(123, "some-title")`. */
data class IdSlug(val id: Long, val slug: String)

/**
 * Reads Universal Links of the web app (features-spec §3.12, seo §A.10), so iOS routes the same
 * URLs as the site and, later, Android App Links:
 *
 * - `https://<host>/podcast/<id>-<slug>` → [DeepLink.Podcast]
 * - `https://<host>/episode/<id>-<slug>` → [DeepLink.Episode]
 * - the same under the `/en` locale prefix; one trailing `/` is ignored; query and fragment too.
 *
 * Anything else is null: another scheme (`http`, Android's internal `tss://ts.podcast/...`), a
 * host not in [allowedHosts] (compared without case), user info, a port other than 443, extra
 * path segments, or a segment [parseIdSlug] rejects. With no allowed host every link is null
 * (plan §7 item 6: no domain yet).
 */
class DeepLinkParser(allowedHosts: List<String>) {

    private val hosts: Set<String> = allowedHosts
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun parse(url: String): DeepLink? {
        if (hosts.isEmpty()) return null
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0 || !url.substring(0, schemeEnd).equals(HTTPS, ignoreCase = true)) return null

        val afterScheme = url.substring(schemeEnd + 3)
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) afterScheme.length else it }
        if (!isAllowedAuthority(afterScheme.substring(0, authorityEnd))) return null

        val rest = afterScheme.substring(authorityEnd)
        val path = rest.substring(0, rest.indexOfFirst { it == '?' || it == '#' }.let { if (it < 0) rest.length else it })
        if (!path.startsWith("/")) return null

        var segments = path.substring(1).split('/')
        if (segments.size > 1 && segments.last().isEmpty()) segments = segments.dropLast(1)
        if (segments.firstOrNull() == LOCALE_EN) segments = segments.drop(1)
        if (segments.size != 2) return null

        val id = parseIdSlug(segments[1])?.id ?: return null
        return when (segments[0]) {
            PODCAST -> DeepLink.Podcast(id)
            EPISODE -> DeepLink.Episode(id)
            else -> null
        }
    }

    private fun isAllowedAuthority(authority: String): Boolean {
        if (authority.contains('@')) return false
        val colon = authority.lastIndexOf(':')
        val host = if (colon < 0) authority else authority.substring(0, colon)
        if (colon >= 0 && authority.substring(colon + 1) != HTTPS_PORT) return false
        return host.lowercase() in hosts
    }

    companion object {
        private const val HTTPS = "https"
        private const val HTTPS_PORT = "443"
        private const val LOCALE_EN = "en"
        private const val PODCAST = "podcast"
        private const val EPISODE = "episode"

        // The web's `/^(\d{1,15})(?:-(.*))?$/`; `[0-9]` because JavaScript's `\d` is ASCII only.
        private val ID_SLUG = Regex("^([0-9]{1,15})(?:-(.*))?$")

        /**
         * Port of web `parseIdSlug` (`web/src/lib/slug.ts`): percent-decodes [segment]
         * (`decodeURIComponent`; malformed escapes are null), then expects 1 to 15 digits,
         * optionally followed by `-` and any slug. The id must be greater than 0. The slug is
         * not checked: links keep working after a title change.
         */
        fun parseIdSlug(segment: String): IdSlug? {
            val decoded = percentDecode(segment) ?: return null
            val match = ID_SLUG.find(decoded) ?: return null
            val id = match.groupValues[1].toLongOrNull() ?: return null
            if (id <= 0) return null
            return IdSlug(id, match.groupValues[2])
        }

        /** `decodeURIComponent`: `%XX` bytes as UTF-8, null on a bad escape or invalid UTF-8. */
        private fun percentDecode(text: String): String? {
            if ('%' !in text) return text
            val bytes = ArrayList<Byte>(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%') {
                    if (i + 2 >= text.length) return null
                    val hi = text[i + 1].digitToIntOrNull(16) ?: return null
                    val lo = text[i + 2].digitToIntOrNull(16) ?: return null
                    bytes += ((hi shl 4) or lo).toByte()
                    i += 3
                } else {
                    // Copy one code point (a surrogate pair stays together) as UTF-8.
                    val end = if (c.isHighSurrogate() && i + 1 < text.length) i + 2 else i + 1
                    text.substring(i, end).encodeToByteArray().forEach { bytes += it }
                    i = end
                }
            }
            return runCatching { bytes.toByteArray().decodeToString(throwOnInvalidSequence = true) }.getOrNull()
        }
    }
}
