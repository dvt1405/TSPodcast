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
 * Feed ids and keywords that must never be shown (gambling content, features-spec §2), the
 * shared port of Android's `BlacklistRepositoryImpl`.
 *
 * Source: Remote Config [ConfigKeys.KEY_BLACK_LIST] when it holds a JSON object, otherwise
 * the bundled `coreApi/src/main/assets/blacklist.json`. The remote value **replaces** the
 * asset, it is not merged with it, like Android.
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

    private fun load(): Blacklist =
        config.getString(ConfigKeys.KEY_BLACK_LIST)?.let(::parse)
            ?: parse(BundledAssets.blacklist)
            ?: Blacklist(emptySet(), emptySet())

    /** Null when [text] is not a JSON object, the case where Android falls back to the asset. */
    private fun parse(text: String): Blacklist? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        return Blacklist(
            feedIds = root.strings("feedId"),
            keywords = root.strings("filterText").mapTo(mutableSetOf()) { it.lowercase() },
        )
    }

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
    )
}
