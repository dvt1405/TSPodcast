package tss.t.shared.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import tss.t.shared.favourite.FavouriteType

/**
 * In-memory stand-in for [PodcastDatabase] in `commonTest` (the Android host test cannot build
 * Room without a `Context`). It models the queries closely enough for the logic built on top;
 * the real SQL is tested against Room in `iosTest`. [episodeReads] counts episode queries.
 */
open class FakePodcastDaos : PodcastDaos {
    val podcasts = mutableMapOf<Long, PodcastEntity>()
    val episodes = mutableMapOf<Long, EpisodeEntity>()
    val feeds = mutableMapOf<Long, FeedEntity>()

    /** Newest first, like `ORDER BY rowid DESC`. */
    val favourites = MutableStateFlow<List<FavouriteEntity>>(emptyList())

    var episodeReads = 0
        private set

    private fun favouriteIds() = favourites.value.map { it.id }.toSet()

    private val podcastDao = object : PodcastDao {
        override suspend fun insertRows(rows: List<PodcastEntity>) {
            rows.forEach { podcasts[it.id] = it }
        }

        override suspend fun selectById(id: Long): PodcastEntity? = podcasts[id]

        override suspend fun selectAllEpisodeById(id: Long): PodcastWithEpisodes? =
            podcasts[id]?.let { podcast -> PodcastWithEpisodes(podcast, episodes.values.filter { it.feedId == id }) }

        override suspend fun deleteExpired(expiredBefore: Long): Int {
            val keep = favouriteIds()
            val favouriteEpisodeFeeds = favourites.value
                .filter { it.type == FavouriteType.PodcastEpisode }
                .mapNotNull { episodes[it.id.toLongOrNull()]?.feedId }
                .toSet()
            val expired = podcasts.values.filter {
                it.cachedAt < expiredBefore && it.id.toString() !in keep &&
                    it.feedId.toString() !in keep && it.id !in favouriteEpisodeFeeds
            }
            expired.forEach { podcasts.remove(it.id) }
            return expired.size
        }
    }

    private val episodeDao = object : EpisodeDao {
        override suspend fun insertRows(rows: List<EpisodeEntity>) {
            rows.forEach { episodes[it.id] = it }
        }

        override suspend fun selectById(id: Long): EpisodeEntity? {
            episodeReads++
            return episodes[id]
        }

        override suspend fun selectByFeedId(feedId: Long): List<EpisodeEntity> {
            episodeReads++
            return episodes.values.filter { it.feedId == feedId }
                .sortedWith(compareByDescending<EpisodeEntity> { it.datePublished }.thenByDescending { it.id })
        }

        override suspend fun deleteExpired(expiredBefore: Long): Int {
            val keep = favouriteIds()
            val expired = episodes.values.filter {
                it.cachedAt < expiredBefore && it.id.toString() !in keep && it.feedId.toString() !in keep
            }
            expired.forEach { episodes.remove(it.id) }
            return expired.size
        }
    }

    private val feedDao = object : FeedDao {
        override suspend fun insertRows(rows: List<FeedEntity>) {
            rows.forEach { feeds[it.id] = it }
        }

        override suspend fun selectById(id: Long): FeedEntity? = feeds[id]

        override suspend fun deleteExpired(expiredBefore: Long): Int {
            val keep = favouriteIds()
            val expired = feeds.values.filter { it.cachedAt < expiredBefore && it.id.toString() !in keep }
            expired.forEach { feeds.remove(it.id) }
            return expired.size
        }
    }

    private val favouriteDao = object : FavouriteDao {
        override suspend fun insert(row: FavouriteEntity) {
            favourites.update { current -> listOf(row) + current.filterNot { it.type == row.type && it.id == row.id } }
        }

        override suspend fun delete(type: FavouriteType, id: String): Int {
            val before = favourites.value.size
            favourites.update { current -> current.filterNot { it.type == type && it.id == id } }
            return before - favourites.value.size
        }

        override suspend fun selectById(id: String, type: FavouriteType): FavouriteEntity? =
            favourites.value.firstOrNull { it.type == type && it.id == id }

        override fun observeAll(): Flow<List<FavouriteEntity>> = favourites

        override suspend fun getAll(): List<FavouriteEntity> = favourites.value

        override suspend fun count(): Int = favourites.value.size
    }

    override fun podcastDao(): PodcastDao = podcastDao
    override fun episodeDao(): EpisodeDao = episodeDao
    override fun feedDao(): FeedDao = feedDao
    override fun favouriteDao(): FavouriteDao = favouriteDao
}
