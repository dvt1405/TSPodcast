package tss.t.shared.storage

import tss.t.shared.model.Episode
import tss.t.shared.model.Feed
import tss.t.shared.model.Podcast
import tss.t.shared.util.EpochClock

/**
 * The API cache tables (Podcast, Episode, Feed) in API-model terms. Every write stamps
 * `cachedAt = clock.nowMillis()` and REPLACEs, like Android's `PodcastDao`/`EpisodeDao`/`FeedDao`
 * `insert`/`inserts` (which read `System.currentTimeMillis()`); [clock] makes it testable.
 * Rows older than `PruneApiCache.TTL_MS` are removed at launch.
 */
class PodcastStorage(
    private val daos: PodcastDaos,
    private val clock: EpochClock,
) {
    suspend fun insertPodcast(podcast: Podcast) = insertPodcasts(listOf(podcast))

    suspend fun insertPodcasts(podcasts: List<Podcast>) {
        val now = clock.nowMillis()
        daos.podcastDao().insertRows(podcasts.map { it.toEntity(now) })
    }

    suspend fun insertEpisode(episode: Episode) = insertEpisodes(listOf(episode))

    suspend fun insertEpisodes(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        val now = clock.nowMillis()
        daos.episodeDao().insertRows(episodes.map { it.toEntity(now) })
    }

    suspend fun insertFeed(feed: Feed) = insertFeeds(listOf(feed))

    suspend fun insertFeeds(feeds: List<Feed>) {
        val now = clock.nowMillis()
        daos.feedDao().insertRows(feeds.map { it.toEntity(now) })
    }

    suspend fun podcastById(id: Long): Podcast? = daos.podcastDao().selectById(id)?.toModel()

    suspend fun episodeById(id: Long): Episode? = daos.episodeDao().selectById(id)?.toModel()

    suspend fun feedById(id: Long): Feed? = daos.feedDao().selectById(id)?.toModel()

    /** Android `selectAllEpisodeById`: podcast [id] and its saved episodes (Room order), or null. */
    suspend fun podcastWithEpisodes(id: Long): PodcastAndEpisodes? =
        daos.podcastDao().selectAllEpisodeById(id)?.let { row ->
            PodcastAndEpisodes(row.podcast.toModel(), row.episodes.map { it.toModel() })
        }

    /** Saved episodes of [feedId], newest first. */
    suspend fun episodesOfFeed(feedId: Long): List<Episode> =
        daos.episodeDao().selectByFeedId(feedId).map { it.toModel() }
}

/** Android `PodcastAndEpisode` in API-model terms. */
data class PodcastAndEpisodes(
    val podcast: Podcast,
    val episodes: List<Episode>,
)
