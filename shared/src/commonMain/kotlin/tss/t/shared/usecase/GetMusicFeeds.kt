package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.isBlocked
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
 * On failure returns `Error(cause, emptyList())`, like Android.
 */
class GetMusicFeeds internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
) {
    suspend operator fun invoke(max: Int = MAX_RESULTS): TSDataState<List<Podcast>> =
        when (val result = api.getPodcastsByMedium(medium = PodcastIndexApi.MEDIUM_MUSIC, max = max)) {
            is TSDataState.Success -> TSDataState.Success(
                result.data.feeds
                    .filterNot { it.isBlocked(blacklist) }
                    .map { Podcast.fromFeed(it) }
            )

            is TSDataState.Error -> TSDataState.Error(result.exception, emptyList())
            is TSDataState.Loading -> TSDataState.Error(IllegalStateException("bymedium failed"), emptyList())
        }

    companion object {
        const val MAX_RESULTS = 40
    }
}
