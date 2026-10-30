package tss.t.shared.blacklist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import tss.t.shared.config.ConfigAPI
import tss.t.shared.config.ConfigKeys
import tss.t.shared.generated.BundledAssets
import kotlin.concurrent.Volatile

/**
 * Feed ids, keywords, hosts and authors that must never be shown (gambling content and spam
 * feeds, features-spec §2, M6 §3.5), the shared port of Android's `BlacklistRepositoryImpl`.
 *
 * Source of `feedId`/`filterText`: Remote Config [ConfigKeys.KEY_BLACK_LIST] when it holds a JSON
 * object, otherwise the bundled `coreApi/src/main/assets/blacklist.json`. The remote value
 * **replaces** the asset, it is not merged with it, like Android.
 *
 * `host`/`author` (M6, not read by Android yet) are decided **per key**: the remote list when the
 * remote object has that key as an array, otherwise the bundled `shared/assets/blacklist_extra.json`.
 * A remote value without the new keys (the UAT one today) must not empty the host list.
 * - [isBlockedHost]: the host of a URL, lowercase and without `www.`, equals a listed host or is
 *   a subdomain of one (`a.ngocbaomedia.com`); a URL without a host never matches.
 * - [isBlockedAuthor]: the trimmed, lowercase name equals a listed one (no substring match:
 *   an author name is short and `contains` would block innocent shows).
 *
 * Differences from Android, on purpose:
 * - The list is loaded synchronously in the constructor, so the very first filter already
 *   sees it. Android loads it in a coroutine from `init` and the first request can slip
 *   through unfiltered.
 * - `filterSpace = true` really removes whitespace; Android dropped the result of `replace`.
 * - Keywords are stored lowercase and blank entries are ignored, so a remote keyword written
 *   with capitals still matches and an empty string cannot block everything.
 */
class BlacklistRepository(
    private val config: ConfigAPI,
    private val json: Json = Json,
) {
    @Volatile
    private var current: Blacklist = load()

    /** Number of distinct blacklisted feed ids currently loaded. */
    val feedIdCount: Int get() = current.feedIds.size

    /** Number of distinct keywords currently loaded. */
    val keywordCount: Int get() = current.keywords.size

    /** Number of distinct blocked hosts currently loaded. */
    val hostCount: Int get() = current.hosts.size

    /** Number of distinct blocked authors currently loaded. */
    val authorCount: Int get() = current.authors.size

    /** Reads the list again, e.g. after Remote Config activated new values. */
    fun reload() {
        current = load()
    }

    fun isInBlacklist(id: String): Boolean = id in current.feedIds

    /**
     * Whether [key] contains a blacklisted keyword, ignoring case. With [filterSpace] the
     * whitespace is removed from both sides first, so "c a s i n o" matches "casino".
     */
    fun isContainKeywordsBlacklist(key: String?, filterSpace: Boolean = false): Boolean {
        if (key.isNullOrEmpty()) return false
        val text = key.lowercase().let { if (filterSpace) it.withoutWhitespace() else it }
        return current.keywords.any { keyword ->
            val needle = if (filterSpace) keyword.withoutWhitespace() else keyword
            needle.isNotEmpty() && text.contains(needle)
        }
    }

    /** Whether [url] is on a blocked host or one of its subdomains. Null or malformed: false. */
    fun isBlockedHost(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return current.hosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    /** Whether [name], trimmed and lowercase, is a blocked author. Null or blank: false. */
    fun isBlockedAuthor(name: String?): Boolean {
        val author = name?.trim()?.lowercase()
        if (author.isNullOrEmpty()) return false
        return author in current.authors
    }

    private fun load(): Blacklist {
        val remote = config.getString(ConfigKeys.KEY_BLACK_LIST)?.let(::parseObject)
        val ids = remote ?: parseObject(BundledAssets.blacklist)
        val extra = parseObject(BundledAssets.blacklistExtra)
        return Blacklist(
            feedIds = ids?.strings(KEY_FEED_ID).orEmpty(),
            keywords = ids?.strings(KEY_FILTER_TEXT).orEmpty().mapTo(mutableSetOf()) { it.lowercase() },
            hosts = (remote.takeIfArray(KEY_HOST) ?: extra)?.strings(KEY_HOST).orEmpty()
                .mapNotNullTo(mutableSetOf(), ::normalizeListedHost),
            authors = (remote.takeIfArray(KEY_AUTHOR) ?: extra)?.strings(KEY_AUTHOR).orEmpty()
                .mapTo(mutableSetOf()) { it.lowercase() },
        )
    }

    /** Null when [text] is not a JSON object, the case where Android falls back to the asset. */
    private fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    /** This object when it has [name] as an array, so that the remote list wins for that key. */
    private fun JsonObject?.takeIfArray(name: String): JsonObject? = this?.takeIf { it[name] is JsonArray }

    /** Like `JSONArray.optString`: strings and numbers are kept as text, anything else skipped. */
    private fun JsonObject.strings(name: String): Set<String> =
        (this[name] as? JsonArray).orEmpty()
            .filter { it is JsonPrimitive && it !is JsonNull }
            .map { (it as JsonPrimitive).content.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun String.withoutWhitespace(): String = filterNot { it.isWhitespace() }

    private class Blacklist(
        val feedIds: Set<String>,
        val keywords: Set<String>,
        val hosts: Set<String>,
        val authors: Set<String>,
    )

    internal companion object {
        const val KEY_FEED_ID = "feedId"
        const val KEY_FILTER_TEXT = "filterText"
        const val KEY_HOST = "host"
        const val KEY_AUTHOR = "author"

        /**
         * The host of an absolute URL (`scheme://[user@]host[:port]/…`), lowercase, without a
         * trailing dot and without a leading `www.`; null when there is none.
         */
        fun hostOf(url: String?): String? {
            val text = url?.trim().orEmpty()
            val schemeEnd = text.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = text.substring(0, schemeEnd)
            if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
            val authority = text.substring(schemeEnd + 3).substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':').trimEnd('.').lowercase()
            return normalizeHost(host)
        }

        /** A host of the list: `*.` and `.` prefixes allowed, a URL is reduced to its host. */
        fun normalizeListedHost(entry: String): String? {
            val text = entry.trim().lowercase()
            if (text.contains("://")) return hostOf(text)
            return normalizeHost(text.removePrefix("*.").trimStart('.').trimEnd('.').substringBefore('/'))
        }

        private fun normalizeHost(host: String): String? {
            val bare = host.removePrefix("www.")
            if (bare.isEmpty() || bare.any { it.isWhitespace() }) return null
            return bare
        }
    }
}
