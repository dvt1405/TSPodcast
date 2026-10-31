package tss.t.shared.blacklist

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tss.t.shared.model.Feed
import tss.t.shared.model.Podcast
import tss.t.shared.preferences.KeyValueStore
import tss.t.shared.util.EpochClock

/**
 * A show the user hid with "Ẩn show này" (M6 §3.5, N1 lite). [title] and [imageUrl] are kept so
 * that Settings → "Show đã ẩn" can list it without a request.
 */
@Serializable
data class HiddenShow(
    val feedId: Long,
    val title: String,
    val imageUrl: String? = null,
    val hiddenAtMillis: Long,
)

/**
 * Shows hidden on this device, a JSON list at [KEY_HIDDEN_SHOWS] of the app's key-value store,
 * so they stay hidden after a relaunch. Local only: nothing is sent anywhere.
 *
 * Home, Search and the news hub drop these feed ids as their last step. A value that does not
 * parse reads as an empty list (the next write replaces it). Writes are serialized, so two quick
 * hides cannot lose one another.
 */
class HiddenShowsRepository(
    private val store: KeyValueStore,
    private val json: Json,
    private val clock: EpochClock,
) {
    private val mutex = Mutex()

    /** Every hidden show, the most recently hidden first. */
    suspend fun all(): List<HiddenShow> = read().sortedByDescending { it.hiddenAtMillis }

    suspend fun hiddenIds(): Set<Long> = read().mapTo(mutableSetOf()) { it.feedId }

    suspend fun isHidden(feedId: Long): Boolean = read().any { it.feedId == feedId }

    /** Hides [feedId]; hiding it again only refreshes the title, image and time (one record). */
    suspend fun hide(feedId: Long, title: String, imageUrl: String?) = mutex.withLock {
        val show = HiddenShow(feedId, title, imageUrl?.takeIf { it.isNotBlank() }, clock.nowMillis())
        write(read().filterNot { it.feedId == feedId } + show)
    }

    suspend fun unhide(feedId: Long) = mutex.withLock {
        val current = read()
        val kept = current.filterNot { it.feedId == feedId }
        if (kept.size != current.size) write(kept)
    }

    private suspend fun read(): List<HiddenShow> {
        val text = store.getString(KEY_HIDDEN_SHOWS) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
    }

    private suspend fun write(shows: List<HiddenShow>) {
        store.putString(KEY_HIDDEN_SHOWS, json.encodeToString(serializer, shows))
    }

    companion object {
        const val KEY_HIDDEN_SHOWS = "hiddenShows"

        private val serializer = ListSerializer(HiddenShow.serializer())
    }
}

/** Podcasts whose `id` or `feedId` is not in [hidden] (E1/E5 send `id`, E6 maps `feedId = id`). */
internal fun List<Podcast>.withoutHidden(hidden: Set<Long>): List<Podcast> =
    if (hidden.isEmpty()) this else filterNot { it.id in hidden || it.feedId in hidden }

/** Feeds whose `id` is not in [hidden]. */
internal fun List<Feed>.withoutHiddenFeeds(hidden: Set<Long>): List<Feed> =
    if (hidden.isEmpty()) this else filterNot { it.id in hidden }
