package tss.t.sharedplayer.library

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.IHistoryRepository
import tss.t.core.repository.MediaType
import tss.t.core.storage.SharedPref
import tss.t.coreapi.Constants
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.HistoryRepository
import tss.t.podcasts.usecase.GetEpisodeByFeedId
import tss.t.podcasts.usecase.GetTrendingPodcasts
import tss.t.podcasts.usecase.SearchPodcasts
import tss.t.sharedplayer.player.PlayerManager
import tss.t.sharedresources.R
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The requested node does not exist (anymore), e.g. an unfavourited show. */
class LibraryItemNotFoundException(mediaId: String) :
    IllegalArgumentException("No library item for $mediaId")

/**
 * Content of the car browse tree (Android Auto, later Android Automotive OS), built from the
 * repositories the phone app already uses. Ids: [LibraryIds].
 *
 * Every function may hit Room or the network and must be called from a coroutine; they switch to
 * [Dispatchers.IO] themselves. Network errors are thrown, so the session can answer with an error
 * the car shows ("Không tải được..."), instead of an empty list that looks like "no content".
 */
@Singleton
@UnstableApi
class MediaLibraryCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favouriteRepository: IFavouriteRepository,
    private val historyRepository: IHistoryRepository,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val sharedPref: SharedPref,
    private val getTrendingPodcasts: GetTrendingPodcasts,
    private val getEpisodeByFeedId: GetEpisodeByFeedId,
    private val searchPodcasts: SearchPodcasts,
    private val playerManager: PlayerManager,
) {
    /** Shows seen while browsing (trending, search), by feed id, for titles and artwork. */
    private val podcasts = ConcurrentHashMap<Long, Podcast>()

    /** Episodes of the last few shows opened in the car, newest first, with the load time. */
    private val feedEpisodes = object : LinkedHashMap<Long, CachedEpisodes>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedEpisodes>?) =
            size > MAX_CACHED_FEEDS
    }

    private class CachedEpisodes(val loadedAt: Long, val episodes: List<Episode>)

    private val searchCache = ConcurrentHashMap<String, List<Podcast>>()

    // ---------------------------------------------------------------------------------------
    // Browse
    // ---------------------------------------------------------------------------------------

    fun root(): MediaItem = folderNode(LibraryIds.ROOT, context.getString(R.string.car_root_title))

    fun recentRoot(): MediaItem =
        folderNode(LibraryIds.RECENT_ROOT, context.getString(R.string.car_tab_continue))

    /** Root tabs. Auto shows browsable root children as tabs (max 4). */
    fun rootChildren(): List<MediaItem> = listOf(
        folderNode(
            LibraryIds.CONTINUE,
            context.getString(R.string.car_tab_continue),
            childrenStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        ),
        folderNode(
            LibraryIds.FAVOURITES,
            context.getString(R.string.car_tab_favourites),
            childrenStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        ),
        folderNode(
            LibraryIds.TRENDING,
            context.getString(R.string.car_tab_trending),
            childrenStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        ),
    )

    suspend fun children(parentId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        when (parentId) {
            LibraryIds.ROOT -> rootChildren()
            LibraryIds.RECENT_ROOT -> listOfNotNull(lastEpisode()?.let { browseItem(it) })
            LibraryIds.CONTINUE -> continueListening()
            LibraryIds.FAVOURITES -> favourites()
            LibraryIds.TRENDING -> trending()
            else -> {
                val feedId = LibraryIds.podcastIdOf(parentId)
                    ?: throw LibraryItemNotFoundException(parentId)
                val show = showTitle(feedId)
                episodesOf(feedId).map { it.toBrowseItem(show, resumeOf(it)) }
            }
        }
    }

    suspend fun item(mediaId: String): MediaItem = withContext(Dispatchers.IO) {
        when {
            mediaId == LibraryIds.ROOT -> root()
            mediaId == LibraryIds.RECENT_ROOT -> recentRoot()
            LibraryIds.isRootChild(mediaId) -> rootChildren().first { it.mediaId == mediaId }
            else -> {
                LibraryIds.podcastIdOf(mediaId)?.let { feedId ->
                    return@withContext podcastFor(feedId)?.toNode()
                        ?: throw LibraryItemNotFoundException(mediaId)
                }
                val episode = LibraryIds.episodeIdOf(mediaId)?.let { findEpisode(it) }
                    ?: throw LibraryItemNotFoundException(mediaId)
                browseItem(episode)
            }
        }
    }

    /** Shows matching [query] (browsable), like the phone search. Cached per query. */
    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        searchShows(query).map { it.toNode() }
    }

    private suspend fun continueListening(): List<MediaItem> {
        val last = lastEpisode() ?: return emptyList()
        val podcast = podcastFor(last.feedId)
        return listOfNotNull(browseItem(last), podcast?.toNode())
    }

    private suspend fun favourites(): List<MediaItem> =
        favouriteRepository.getListFavourite().mapNotNull { fav ->
            when (fav.type) {
                MediaType.Podcast -> fav.id.toLongOrNull()?.let { podcastNode(it, fav.title, fav.image) }
                MediaType.PodcastEpisode -> fav.id.toLongOrNull()?.let { findEpisode(it) }?.let { browseItem(it) }
                // Radio needs a playable link resolved per channel (coreRadio). TODO(car M3).
                MediaType.Radio, MediaType.TVChannel -> null
            }
        }

    private suspend fun trending(): List<MediaItem> {
        val since = ((System.currentTimeMillis() - Constants.A_DAY * 7L) / 1000).toInt()
        return when (val state = getTrendingPodcasts(max = TRENDING_MAX, since = since)) {
            is TSDataState.Success -> state.data.items.onEach { podcasts[it.id] = it }.map { it.toNode() }
            is TSDataState.Error -> throw state.exception
            is TSDataState.Loading -> emptyList()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------------------------------

    /**
     * Turns what the car asked to play into a playable queue:
     * - one episode → that show's episodes, starting at it, from its saved position;
     * - a voice search ("Ok Google, play X on TSPodcast") → newest episode of the first show
     *   found; an empty query means "continue";
     * - items that already carry a URI (the phone's own controller) pass through unchanged.
     */
    suspend fun resolve(
        items: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): MediaItemsWithStartPosition = withContext(Dispatchers.IO) {
        if (items.all { it.localConfiguration != null }) {
            return@withContext MediaItemsWithStartPosition(items, startIndex, startPositionMs)
        }
        val first = items.firstOrNull() ?: throw LibraryItemNotFoundException("<empty>")
        val query = first.requestMetadata.searchQuery
        if (items.size == 1 && query != null) {
            return@withContext playFromSearch(query)
        }
        if (items.size == 1) {
            val mediaId = first.mediaId
            LibraryIds.podcastIdOf(mediaId)?.let { feedId ->
                val newest = episodesOf(feedId).firstOrNull()
                    ?: throw LibraryItemNotFoundException(mediaId)
                return@withContext queueFor(newest)
            }
            val episode = LibraryIds.episodeIdOf(mediaId)?.let { findEpisode(it) }
                ?: throw LibraryItemNotFoundException(mediaId)
            return@withContext queueFor(episode)
        }
        // Several items (e.g. a queue handed over by a controller): resolve one by one.
        MediaItemsWithStartPosition(resolveEach(items), startIndex, startPositionMs)
    }

    /**
     * Items added to the queue ("add to queue" from a controller): each id becomes one playable
     * item, without expanding an episode into its whole show like [resolve] does.
     */
    suspend fun resolveEach(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        items.map { item ->
            if (item.localConfiguration != null) return@map item
            val episode = LibraryIds.episodeIdOf(item.mediaId)?.let { findEpisode(it) }
                ?: throw LibraryItemNotFoundException(item.mediaId)
            episode.toPlayableMediaItem(showTitle(episode.feedId))
        }
    }

    /**
     * Playback resumption ("play" pressed in the car with nothing loaded): the last episode the
     * user played, in its show's queue, from the saved position. Null when nothing was played yet.
     *
     * @param forPlayback false when only metadata is wanted (one item, no network).
     */
    suspend fun resumption(forPlayback: Boolean): MediaItemsWithStartPosition? =
        withContext(Dispatchers.IO) {
            val last = lastEpisode() ?: return@withContext null
            if (!forPlayback) {
                val item = last.toPlayableMediaItem(showTitle(last.feedId))
                return@withContext MediaItemsWithStartPosition(listOf(item), 0, resumeOf(last))
            }
            queueFor(last, remember = false)
        }

    private suspend fun playFromSearch(query: String): MediaItemsWithStartPosition {
        if (query.isBlank()) {
            return resumption(forPlayback = true) ?: throw LibraryItemNotFoundException("<continue>")
        }
        val show = searchShows(query).firstOrNull() ?: throw LibraryItemNotFoundException(query)
        val newest = episodesOf(show.id).firstOrNull() ?: throw LibraryItemNotFoundException(query)
        return queueFor(newest)
    }

    private suspend fun queueFor(
        episode: Episode,
        remember: Boolean = true,
    ): MediaItemsWithStartPosition {
        val list = runCatching { episodesOf(episode.feedId) }.getOrDefault(emptyList())
            .ifEmpty { listOf(episode) }
        val queue = if (list.any { it.id == episode.id }) list else listOf(episode) + list
        val podcast = podcastFor(episode.feedId)
        val album = podcast?.title ?: podcast?.feedTitle
        val startIndex = queue.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        if (remember) {
            // Same bookkeeping as the phone (PlayerViewModel.playerEpisode): Room rows for the
            // queue and show, and the id behind "Tiếp tục nghe" / the Continue shortcut.
            runCatching { historyRepository.saveCurrentPlaying(episode, podcast, queue) }
        }
        return MediaItemsWithStartPosition(
            queue.map { it.toPlayableMediaItem(album) },
            startIndex,
            resumeOf(episode).takeIf { it > 0L } ?: C.TIME_UNSET,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------------------------

    private suspend fun lastEpisode(): Episode? {
        val id = runCatching { sharedPref.get<String>(HistoryRepository.EXTRA_CURRENT_ITEM_ID) }
            .getOrNull()
            ?.toLongOrNull()
            ?: return null
        return findEpisode(id)
    }

    private suspend fun findEpisode(id: Long): Episode? {
        synchronized(feedEpisodes) {
            feedEpisodes.values.forEach { cached ->
                cached.episodes.firstOrNull { it.id == id }?.let { return it }
            }
        }
        return episodeDao.selectById(id)
    }

    private suspend fun podcastFor(feedId: Long): Podcast? =
        podcasts[feedId] ?: podcastDao.selectById(feedId)?.also { podcasts[feedId] = it }

    private suspend fun showTitle(feedId: Long): String? =
        podcastFor(feedId)?.let { it.title ?: it.feedTitle }

    /** A show's episodes, newest first: network (cached [EPISODES_TTL_MS]), then Room. */
    private suspend fun episodesOf(feedId: Long): List<Episode> {
        val now = System.currentTimeMillis()
        synchronized(feedEpisodes) { feedEpisodes[feedId] }
            ?.takeIf { now - it.loadedAt < EPISODES_TTL_MS }
            ?.let { return it.episodes }
        val remote = runCatching {
            withTimeout(NETWORK_TIMEOUT_MS) { getEpisodeByFeedId(feedId.toString()).first() }
        }.getOrElse { TSDataState.Error(it) }
        val episodes = when (remote) {
            is TSDataState.Success -> remote.data.items
            else -> podcastDao.selectAllEpisodeById(feedId)?.episode.orEmpty()
                .ifEmpty { throw (remote as? TSDataState.Error)?.exception ?: LibraryItemNotFoundException("podcast:$feedId") }
        }.sortedByDescending { it.datePublished }
        synchronized(feedEpisodes) { feedEpisodes[feedId] = CachedEpisodes(now, episodes) }
        return episodes
    }

    private suspend fun searchShows(query: String): List<Podcast> {
        val key = query.trim().lowercase()
        searchCache[key]?.let { return it }
        val result = when (val state = searchPodcasts(query = query.trim(), max = SEARCH_MAX)) {
            is TSDataState.Success -> state.data.feeds.map { Podcast.fromFeed(it) }
            is TSDataState.Error -> throw state.exception
            is TSDataState.Loading -> emptyList()
        }
        result.forEach { podcasts[it.id] = it }
        if (searchCache.size > MAX_CACHED_SEARCHES) searchCache.clear()
        searchCache[key] = result
        return result
    }

    private suspend fun browseItem(episode: Episode): MediaItem =
        episode.toBrowseItem(showTitle(episode.feedId), resumeOf(episode))

    private fun resumeOf(episode: Episode): Long = playerManager.resumePositionMs(episode.id.toString())

    companion object {
        private const val MAX_CACHED_FEEDS = 20
        private const val MAX_CACHED_SEARCHES = 20
        private const val TRENDING_MAX = 50
        private const val SEARCH_MAX = 30
        private const val NETWORK_TIMEOUT_MS = 15_000L
        private const val EPISODES_TTL_MS = 15 * 60 * 1000L
    }
}
