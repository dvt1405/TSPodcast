package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.isBlocked
import tss.t.shared.crash.Crash
import tss.t.shared.crash.CrashReporter
import tss.t.shared.model.Podcast
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E6 `podcasts/bymedium?medium=music`: feeds published with `podcast:medium = music`.
 *
 * Not a ranking (the endpoint takes only `medium` and `max`), so the UI presents it as a
 * library, like Android. The items are feed-shaped and are mapped with [Podcast.fromFeed].
 * Blacklist: `feed.id` and `title`.
 *
 * On failure returns `Error(cause, emptyList())` and reports the cause to [crash] with
 * `medium=music`, like Android: the row falls back to a placeholder, so without the report a
 * permanent failure looks like a slow load.
 */
class GetMusicFeeds internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
    private val crash: CrashReporter = Crash,
) {
    suspend operator fun invoke(max: Int = MAX_RESULTS): TSDataState<List<Podcast>> =
        when (val result = api.getPodcastsByMedium(medium = PodcastIndexApi.MEDIUM_MUSIC, max = max)) {
            is TSDataState.Success -> TSDataState.Success(
                result.data.feeds
                    .filterNot { it.isBlocked(blacklist) }
                    .map { Podcast.fromFeed(it) }
            )

            is TSDataState.Error -> {
                crash.record(result.exception, TAG, mapOf(KEY_MEDIUM to PodcastIndexApi.MEDIUM_MUSIC))
                TSDataState.Error(result.exception, emptyList())
            }

            is TSDataState.Loading -> TSDataState.Error(IllegalStateException("bymedium failed"), emptyList())
        }

    companion object {
        const val TAG = "GetMusicFeeds"
        const val KEY_MEDIUM = "medium"
        const val MAX_RESULTS = 40
    }
}
