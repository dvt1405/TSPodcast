package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.BlacklistRepositoryImpl
import tss.t.sharedlibrary.crash.Crash
import javax.inject.Inject

/**
 * Feeds published with `podcast:medium = music`.
 *
 * Deliberately **not** called "trending": `podcasts/bymedium` accepts only
 * `medium`, `max` and `pretty`, so the result has no ranking and no genre
 * filter. PodcastIndex exposes no chart or top-N endpoint, and
 * `podcasts/trending?cat=Music` returns podcasts *about* music rather than
 * playable music feeds. Presenting this list as a ranking would be inventing
 * one, so the UI labels it as a library.
 *
 * The endpoint returns **feed-shaped** objects, which is why this maps through
 * [Podcast.fromFeed]. Binding it straight to [Podcast] fails at runtime:
 * `Feed.explicit` is a boolean while `Podcast.explicit` is an int, so Gson
 * threw "Expected an int but was BOOLEAN" and the row silently rendered
 * placeholders forever.
 */
class GetMusicFeeds @Inject constructor(
    private val repository: IPodcastRepository,
    private val blacklist: BlacklistRepositoryImpl
) {
    suspend operator fun invoke(max: Int = MAX_RESULTS): TSDataState<List<Podcast>> {
        val state = repository.getPodcastsByMedium(
            medium = MEDIUM_MUSIC,
            max = max
        )
        if (state !is TSDataState.Success) {
            // Reported rather than swallowed: the UI falls back to a shimmer
            // placeholder, so without this a permanent failure is
            // indistinguishable from a slow load.
            (state as? TSDataState.Error)?.exception?.let {
                Crash.record(it, TAG, mapOf("medium" to MEDIUM_MUSIC))
            }
            return TSDataState.Error(
                (state as? TSDataState.Error)?.exception
                    ?: IllegalStateException("bymedium failed"),
                emptyList()
            )
        }
        val list = state.data.feeds
            .filterNot { feed ->
                blacklist.isInBlacklist(feed.id.toString())
                        || blacklist.isContainKeywordsBlacklist(feed.title)
            }
            .map { Podcast.fromFeed(it) }
        return TSDataState.Success(list)
    }

    companion object {
        private const val TAG = "GetMusicFeeds"
        private const val MEDIUM_MUSIC = "music"
        private const val MAX_RESULTS = 40
    }
}
