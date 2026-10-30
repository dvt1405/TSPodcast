package tss.t.shared.usecase

import tss.t.shared.crash.Crash
import tss.t.shared.crash.CrashReporter
import tss.t.shared.crash.safeCall
import tss.t.shared.storage.PodcastDaos
import tss.t.shared.util.EpochClock
import kotlin.time.Duration.Companion.days

/**
 * Expires API-derived rows (Android `core/usecase/PruneApiCache`), run once per launch.
 *
 * PodcastIndex's terms forbid permanent copies of API content and the API answers
 * `no-cache, must-revalidate`, so Podcast/Episode/Feed rows older than [TTL_MS] are deleted,
 * except the ones the user favourited, so favourites still open offline. **[Sửa]** (M4 §3.1):
 * the podcast of a favourited episode is kept too (see `PodcastDao.deleteExpired`).
 *
 * Logs the counts when something was removed; any error is reported and swallowed.
 */
class PruneApiCache(
    private val daos: PodcastDaos,
    private val clock: EpochClock,
    private val crash: CrashReporter = Crash,
) {
    suspend operator fun invoke(): PruneResult = safeCall(TAG, reporter = crash) {
        val expiredBefore = clock.nowMillis() - TTL_MS
        // Podcast first: its [Sửa] clause reads the favourite episode, which this run keeps anyway.
        val podcasts = daos.podcastDao().deleteExpired(expiredBefore)
        val episodes = daos.episodeDao().deleteExpired(expiredBefore)
        val feeds = daos.feedDao().deleteExpired(expiredBefore)
        if (podcasts + episodes + feeds > 0) {
            crash.log("PruneApiCache: removed $podcasts podcasts, $episodes episodes, $feeds feeds")
        }
        PruneResult(podcasts, episodes, feeds)
    }.getOrDefault(PruneResult(0, 0, 0))

    companion object {
        const val TAG = "PruneApiCache"

        /** Deliberately short: a convenience cache for offline browsing, not a datastore. */
        val TTL_MS: Long = 7.days.inWholeMilliseconds
    }
}

/** Rows removed by one [PruneApiCache] run. */
data class PruneResult(val podcasts: Int, val episodes: Int, val feeds: Int)
