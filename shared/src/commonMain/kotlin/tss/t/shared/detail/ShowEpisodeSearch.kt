package tss.t.shared.detail

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tss.t.shared.model.Episode
import tss.t.shared.model.TSDataState
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.text.EpisodeFilter
import tss.t.shared.usecase.GetEpisodeByFeedId
import tss.t.shared.usecase.GetPodcastByFeedID

/**
 * The episodes "Tìm trong show" searches (M6 §3.3, N5): up to [MAX_EPISODES] of one show in one
 * E10 request, while Detail keeps listing its 100.
 *
 * - E10 `episodes/byfeedid?max=1000` has its own entry in the 15-minute cache
 *   (`GetEpisodeByFeedId.cacheKey`), so the first query of a Detail screen sends one request and
 *   the next ones (and Detail's own `max=100` entry) are untouched. E11 runs alongside for the
 *   show's total episode count (usually already cached by Detail).
 * - D6 like [PodcastDetailFeed]: unless explicit content is allowed, explicit episodes are
 *   dropped, and an explicit feed (E11) has nothing to search.
 * - Errors are returned as is and not cached; the screen then keeps searching what it shows.
 */
class ShowEpisodeSearch internal constructor(
    private val getEpisodeByFeedId: GetEpisodeByFeedId,
    private val getPodcastByFeedID: GetPodcastByFeedID,
    private val preferences: AppPreferences,
) {
    suspend fun load(feedId: Long): TSDataState<ShowEpisodeIndex> = coroutineScope {
        val id = feedId.toString()
        val episodesCall = async { getEpisodeByFeedId(id, MAX_EPISODES) }
        val podcastCall = async { getPodcastByFeedID(id) }
        val episodes = episodesCall.await()
        val feed = (podcastCall.await() as? TSDataState.Success)?.data?.feed
        when (episodes) {
            is TSDataState.Success -> {
                val items = episodes.data.items
                val allowExplicit = preferences.allowExplicitContent()
                val kept = when {
                    allowExplicit -> items
                    feed?.explicit == true -> emptyList()
                    else -> items.filter { it.explicit != EXPLICIT }
                }
                TSDataState.Success(
                    ShowEpisodeIndex(
                        filter = EpisodeFilter.index(kept),
                        loadedCount = items.size,
                        totalCount = feed?.episodeCount?.takeIf { it > 0 },
                    ),
                )
            }

            is TSDataState.Error -> TSDataState.Error(episodes.exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }
    }

    companion object {
        /** One request covers nearly every show (PodcastIndex allows up to 1 000). */
        const val MAX_EPISODES = 1_000

        private const val EXPLICIT = 1
    }
}

/**
 * Result of [ShowEpisodeSearch.load].
 *
 * @property filter the searchable episodes, after D6, in API order (newest first).
 * @property loadedCount episodes E10 returned (before D6): what the search covers.
 * @property totalCount the show's episode count from E11, null when unknown.
 * @property isTruncated the show has more episodes than were loaded: the screen says the search
 *   only covers the [loadedCount] newest.
 */
class ShowEpisodeIndex(
    val filter: EpisodeFilter,
    val loadedCount: Int,
    val totalCount: Int?,
) {
    val episodes: List<Episode> get() = filter.episodes

    val isTruncated: Boolean get() = totalCount != null && totalCount > loadedCount
}
