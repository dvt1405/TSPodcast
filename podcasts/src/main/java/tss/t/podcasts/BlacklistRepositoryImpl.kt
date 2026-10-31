package tss.t.podcasts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tss.t.sharedlibrary.utils.ConfigAPI
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feed ids, keywords, hosts and authors that must never be shown (gambling and spam feeds).
 * Same rules as the iOS port (`shared/.../blacklist/BlacklistRepository.kt`, M6 §3.5):
 *
 * - `feedId` / `filterText`: Remote Config [ConfigKeys.KEY_BLACK_LIST] when it is a JSON object,
 *   otherwise the bundled `blacklist.json`.
 * - `host` / `author`: per key, the remote array when present, otherwise `blacklist_extra.json`.
 * - A host matches itself and its subdomains (`www.` ignored); an author matches exactly
 *   (trimmed, lowercase); keywords match case-insensitively, `filterSpace` really drops spaces.
 *
 * The bundled lists are read synchronously at construction so the first request is already
 * filtered; the remote value replaces them as soon as it is read.
 */
@Singleton
class BlacklistRepositoryImpl @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val remoteConfigAPI: ConfigAPI
) {
    private val _scope by lazy { CoroutineScope(Dispatchers.IO) }

    @Volatile
    private var lists = Lists(emptySet(), emptySet(), emptySet(), emptySet())

    init {
        lists = build(remote = null)
        loadBlacklist()
    }

    fun loadBlacklist() {
        _scope.launch {
            val remote = runCatching { remoteConfigAPI.getJSONObject(ConfigKeys.KEY_BLACK_LIST) }.getOrNull()
            lists = build(remote)
        }
    }

    fun isInBlacklist(id: String): Boolean = id in lists.feedIds

    fun isContainKeywordsBlacklist(key: String?, filterSpace: Boolean = false): Boolean {
        if (key.isNullOrEmpty()) return false
        val text = key.lowercase().let { if (filterSpace) it.filterNot(Char::isWhitespace) else it }
        return lists.keywords.any { text.contains(it) }
    }

    fun isBlockedHost(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return lists.hosts.any { host == it || host.endsWith(".$it") }
    }

    fun isBlockedAuthor(name: String?): Boolean {
        val author = name?.trim()?.lowercase()
        if (author.isNullOrEmpty()) return false
        return author in lists.authors
    }

    private fun build(remote: JSONObject?): Lists {
        val ids = remote ?: asset("blacklist.json")
        val extra = asset("blacklist_extra.json")
        fun pick(key: String) = if (remote?.optJSONArray(key) != null) remote else extra
        return Lists(
            feedIds = ids?.strings("feedId").orEmpty(),
            keywords = ids?.strings("filterText").orEmpty().mapTo(mutableSetOf()) { it.lowercase() },
            hosts = pick("host")?.strings("host").orEmpty().mapNotNullTo(mutableSetOf()) { normalizeHost(it) },
            authors = pick("author")?.strings("author").orEmpty().mapTo(mutableSetOf()) { it.lowercase() },
        )
    }

    private fun asset(name: String): JSONObject? = runCatching {
        JSONObject(context.assets.open(name).bufferedReader().use { it.readText() })
    }.getOrNull()

    private fun JSONObject.strings(name: String): Set<String> {
        val array: JSONArray = optJSONArray(name) ?: return emptySet()
        return (0 until array.length())
            .mapNotNull { array.opt(it)?.takeIf { v -> v is String || v is Number }?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private class Lists(
        val feedIds: Set<String>,
        val keywords: Set<String>,
        val hosts: Set<String>,
        val authors: Set<String>,
    )

    companion object {
        /** Host of an absolute URL, lowercase, without `www.` and a trailing dot; null when none. */
        fun hostOf(url: String?): String? {
            val text = url?.trim().orEmpty()
            if (!text.contains("://")) return null
            val host = runCatching { URI(text).host }.getOrNull() ?: return null
            return normalizeHost(host)
        }

        private fun normalizeHost(host: String): String? =
            host.trim().lowercase().trimEnd('.').removePrefix("www.").takeIf { it.isNotEmpty() }
    }
}
