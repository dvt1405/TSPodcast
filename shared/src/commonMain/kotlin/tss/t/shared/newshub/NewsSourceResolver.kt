package tss.t.shared.newshub

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.isBlocked
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.crash.Crash
import tss.t.shared.crash.CrashReporter
import tss.t.shared.model.Feed
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi
import tss.t.shared.network.PodcastIndexHttpException
import tss.t.shared.network.PodcastIndexJson
import tss.t.shared.preferences.KeyValueStore
import tss.t.shared.util.EpochClock
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.days

/** Told once per process and source key that the source could not be shown (M6 §3.8). */
fun interface NewsSourceUnresolvedListener {
    fun onUnresolved(sourceKey: String)
}

/** How a source was found. */
enum class NewsSourcePath {
    /** The device cache (`newsHub.feed.<key>`), younger than 7 days. */
    CACHE,

    /** `podcasts/byitunesid`, on the host of the configured feed URL. */
    ITUNES_ID,

    /** `podcasts/byfeedurl`. */
    FEED_URL,
}

/** Outcome of [NewsSourceResolver.resolve]. */
sealed interface NewsSourceResolution {
    /** The PodcastIndex feed of the source, usable. */
    data class Resolved(val feed: Feed, val path: NewsSourcePath) : NewsSourceResolution

    /** Not on PodcastIndex, dead, or blocked: the source is left out (and reported once). */
    data object Unresolved : NewsSourceResolution

    /** The lookup failed (network, server): the source is left out of this load only. */
    data class Failed(val exception: Throwable) : NewsSourceResolution
}

/**
 * Finds the PodcastIndex feed of a configured source (M6 §3.4, plan §3.1 items 5–6), without
 * ever requesting the publisher's RSS: the app only talks to PodcastIndex.
 *
 * 1. A device cache entry at `newsHub.feed.<key>` younger than [CACHE_TTL] (7 days, by the
 *    injected clock) and made for the same feed URL and iTunes id answers without a request.
 * 2. With an iTunes id: `podcasts/byitunesid`, accepted only when the host of the returned
 *    feed's `url` or `originalUrl` equals the host of the configured `feedUrl` (`www.` ignored).
 *    An iTunes id can be mistyped or reused, and PodcastIndex answers even an unknown id with
 *    some feed, so this check keeps an impostor feed out.
 * 3. No iTunes id, no match, nothing found or an error: `podcasts/byfeedurl`.
 *
 * The result is cached on the device. A feed that is `dead` or blocked by [blacklist] (id,
 * keyword in the title, host, author) counts as unresolved, checked on every call so a new
 * blacklist applies at once. Unresolved sources are logged `news_source_unresolved key=<key>`
 * through [crash] and told to [unresolvedListener] **once per process and key**; a not-found
 * answer is remembered in memory for [NOT_FOUND_TTL] only, never on disk. Network errors are
 * neither cached nor reported.
 */
class NewsSourceResolver internal constructor(
    private val api: PodcastIndexApi,
    private val store: KeyValueStore,
    private val clock: EpochClock,
    private val blacklist: BlacklistRepository,
    private val json: Json = PodcastIndexJson,
    private val crash: CrashReporter = Crash,
) {
    /** Set by the app to send the `news_source_unresolved` event. */
    @Volatile
    var unresolvedListener: NewsSourceUnresolvedListener? = null

    private val notFound = InMemoryTtlCache<String, Unit>(NOT_FOUND_TTL, clock)
    private val reportedMutex = Mutex()
    private val reported = mutableSetOf<String>()

    suspend fun resolve(source: NewsSource): NewsSourceResolution {
        val resolved = cached(source)?.let { NewsSourceResolution.Resolved(it, NewsSourcePath.CACHE) }
            ?: when {
                notFound.get(source.identity()) != null -> return unresolved(source)
                else -> when (val found = lookUp(source)) {
                    is Lookup.Found -> {
                        save(source, found.feed)
                        NewsSourceResolution.Resolved(found.feed, found.path)
                    }

                    Lookup.NotFound -> {
                        notFound.put(source.identity(), Unit)
                        return unresolved(source)
                    }

                    is Lookup.Failure -> return NewsSourceResolution.Failed(found.exception)
                }
            }
        if (resolved.feed.dead != 0 || resolved.feed.isBlocked(blacklist)) return unresolved(source)
        return resolved
    }

    private suspend fun lookUp(source: NewsSource): Lookup {
        val expectedHost = BlacklistRepository.hostOf(source.feedUrl)
        source.itunesId?.let { itunesId ->
            val feed = (api.getPodcastByItunesId(itunesId) as? TSDataState.Success)?.data?.feed
            if (feed != null && feed.id > 0 && expectedHost != null && feed.isOnHost(expectedHost)) {
                return Lookup.Found(feed, NewsSourcePath.ITUNES_ID)
            }
        }
        return when (val result = api.getPodcastByFeedUrl(source.feedUrl.trim())) {
            is TSDataState.Success -> result.data.feed?.takeIf { it.id > 0 }
                ?.let { Lookup.Found(it, NewsSourcePath.FEED_URL) }
                ?: Lookup.NotFound

            is TSDataState.Error -> {
                val exception = result.exception
                if (exception is PodcastIndexHttpException && exception.code in NOT_FOUND_CODES) {
                    Lookup.NotFound
                } else {
                    Lookup.Failure(exception)
                }
            }

            is TSDataState.Loading -> Lookup.Failure(IllegalStateException("Loading"))
        }
    }

    private fun Feed.isOnHost(host: String): Boolean =
        BlacklistRepository.hostOf(url) == host || BlacklistRepository.hostOf(originalUrl) == host

    private suspend fun cached(source: NewsSource): Feed? {
        val text = store.getString(cacheKey(source.key)) ?: return null
        val entry = runCatching { json.decodeFromString(CachedNewsFeed.serializer(), text) }.getOrNull()
            ?: return null
        val age = clock.nowMillis() - entry.savedAtMillis
        val sameSource = entry.feedUrl == source.feedUrl && entry.itunesId == source.itunesId
        return entry.feed.takeIf { sameSource && age in 0..CACHE_TTL.inWholeMilliseconds }
    }

    private suspend fun save(source: NewsSource, feed: Feed) {
        val entry = CachedNewsFeed(source.feedUrl, source.itunesId, clock.nowMillis(), feed)
        store.putString(cacheKey(source.key), json.encodeToString(CachedNewsFeed.serializer(), entry))
    }

    private suspend fun unresolved(source: NewsSource): NewsSourceResolution.Unresolved {
        val first = reportedMutex.withLock { reported.add(source.key) }
        if (first) {
            crash.log("news_source_unresolved key=${source.key}")
            unresolvedListener?.onUnresolved(source.key)
        }
        return NewsSourceResolution.Unresolved
    }

    private fun NewsSource.identity(): String = "$key|$feedUrl|$itunesId"

    private sealed interface Lookup {
        class Found(val feed: Feed, val path: NewsSourcePath) : Lookup
        data object NotFound : Lookup
        class Failure(val exception: Throwable) : Lookup
    }

    companion object {
        /** How long a resolved feed id is reused without asking PodcastIndex again. */
        val CACHE_TTL = 7.days

        /** How long a "not on PodcastIndex" answer is reused, in memory only. */
        val NOT_FOUND_TTL = InMemoryTtlCache.API_CACHE_TTL

        /** PodcastIndex answers an unknown feed URL with 400. */
        private val NOT_FOUND_CODES = setOf(400, 404)

        fun cacheKey(sourceKey: String): String = "newsHub.feed.$sourceKey"
    }
}

/** What `newsHub.feed.<key>` holds: the feed found for that URL and iTunes id, and when. */
@Serializable
internal data class CachedNewsFeed(
    val feedUrl: String,
    val itunesId: Long? = null,
    val savedAtMillis: Long,
    val feed: Feed,
)
