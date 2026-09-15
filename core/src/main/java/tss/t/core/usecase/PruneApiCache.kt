package tss.t.core.usecase

import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.FeedDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.sharedlibrary.crash.Crash
import tss.t.sharedlibrary.crash.safeCall
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

/**
 * Expires API-derived rows from the local database.
 *
 * PodcastIndex's terms of service prohibit creating permanent copies of API
 * content or keeping cached copies longer than the cache header allows, and the
 * API responds with `no-cache, must-revalidate`. The Podcast/Feed/Episode
 * tables previously had no eviction at all, so rows lived until uninstall.
 *
 * Rows the user explicitly favourited are kept, so favourites still open
 * offline; everything else is re-fetched after [TTL_MS].
 */
@Singleton
class PruneApiCache @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val feedDao: FeedDao
) {
    suspend operator fun invoke() {
        safeCall(TAG) {
            val expiredBefore = System.currentTimeMillis() - TTL_MS
            val podcasts = podcastDao.deleteExpired(expiredBefore)
            val episodes = episodeDao.deleteExpired(expiredBefore)
            val feeds = feedDao.deleteExpired(expiredBefore)
            if (podcasts + episodes + feeds > 0) {
                Crash.log("PruneApiCache: removed $podcasts podcasts, $episodes episodes, $feeds feeds")
            }
        }
    }

    companion object {
        private const val TAG = "PruneApiCache"

        /**
         * Deliberately short. The data is a convenience cache for offline
         * browsing, not a datastore.
         */
        val TTL_MS: Long = TimeUnit.DAYS.toMillis(7)
    }
}
