package tss.t.shared.detail

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tss.t.shared.crash.safeCall
import tss.t.shared.model.Episode
import tss.t.shared.model.Feed
import tss.t.shared.model.TSDataState
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.storage.PodcastStorage
import tss.t.shared.usecase.GetEpisodeByFeedId
import tss.t.shared.usecase.GetPodcastByFeedID

/**
 * The calls behind Podcast detail, like Android's `PodcastViewModel.getEpisodes`
 * (`getEpisodeByFeedId(id).zip(getPodcastByFeedID(id))`, features-spec §3.5):
 * E10 `episodes/byfeedid` and E11 `podcasts/byfeedid` run in parallel, and **only E10** decides
 * between success and error. E11 never fails the result; it only feeds the D6 check.
 *
 * Retry and the 15-minute cache per feed id come from the two M0 use cases: E10 retries any
 * error 3 times, E11 retries HTTP/IO errors but not a body that does not parse. Errors are
 * never cached. No blacklist here, as on Android: the feed already passed it on the screen
 * it was opened from.
 *
 * D6, unless [AppPreferences.allowExplicitContent]:
 * - episodes with `explicit == 1` are dropped and counted in [PodcastDetail.hiddenExplicitCount];
 * - when E11 succeeds with `feed.explicit == true`, every episode is hidden and
 *   [PodcastDetail.isExplicitFeedHidden] is set. When E11 fails, the feed is not hidden.
 *
 * **[Sửa]** offline (M4 §3.3, §7 item 5): when E10 fails and [storage] has saved episodes of the
 * feed (played or favourited earlier), they are returned newest first with
 * [PodcastDetail.isFromCache] set, D6 still applied; with nothing saved the E10 error stands.
 * The database is only read when E10 fails.
 */
class PodcastDetailFeed internal constructor(
    private val getEpisodeByFeedId: GetEpisodeByFeedId,
    private val getPodcastByFeedID: GetPodcastByFeedID,
    private val preferences: AppPreferences,
    private val storage: PodcastStorage,
) {
    suspend fun load(feedId: Long): TSDataState<PodcastDetail> = coroutineScope {
        val id = feedId.toString()
        val episodesCall = async { getEpisodeByFeedId(id) }
        val podcastCall = async { getPodcastByFeedID(id) }
        val episodes = episodesCall.await()
        val podcast = podcastCall.await()

        when (episodes) {
            is TSDataState.Success -> {
                val feed = (podcast as? TSDataState.Success)?.data?.feed
                TSDataState.Success(
                    detail(episodes.data.items, feed, preferences.allowExplicitContent()),
                )
            }

            is TSDataState.Error -> {
                val saved = savedEpisodes(feedId)
                if (saved.isEmpty()) {
                    TSDataState.Error(episodes.exception)
                } else {
                    val feed = (podcast as? TSDataState.Success)?.data?.feed
                    TSDataState.Success(
                        detail(saved, feed, preferences.allowExplicitContent()).copy(isFromCache = true),
                    )
                }
            }

            is TSDataState.Loading -> TSDataState.Loading()
        }
    }

    /** A database error must not hide the E10 error: it counts as "nothing saved". */
    private suspend fun savedEpisodes(feedId: Long): List<Episode> =
        safeCall(TAG, mapOf("feedId" to feedId.toString())) { storage.episodesOfFeed(feedId) }
            .getOrDefault(emptyList())

    private fun detail(items: List<Episode>, feed: Feed?, allowExplicit: Boolean): PodcastDetail {
        if (allowExplicit) {
            return PodcastDetail(items, feed, hiddenExplicitCount = 0, isExplicitFeedHidden = false)
        }
        if (feed?.explicit == true) {
            return PodcastDetail(
                episodes = emptyList(),
                feed = feed,
                hiddenExplicitCount = items.size,
                isExplicitFeedHidden = true,
            )
        }
        val kept = items.filter { it.explicit != EXPLICIT }
        return PodcastDetail(
            episodes = kept,
            feed = feed,
            hiddenExplicitCount = items.size - kept.size,
            isExplicitFeedHidden = false,
        )
    }

    private companion object {
        const val EXPLICIT = 1
        const val TAG = "PodcastDetailFeed"
    }
}

/**
 * Result of [PodcastDetailFeed.load].
 *
 * @property episodes what the screen lists, in API order (saved ones: newest first), after D6.
 * @property feed the E11 feed, or null when E11 failed or the id is unknown.
 * @property hiddenExplicitCount episodes removed by D6; when the whole feed is hidden this is
 *   every episode E10 returned.
 * @property isExplicitFeedHidden E11 marked the feed explicit and D6 is on, so [episodes] is empty.
 * @property isFromCache E10 failed and [episodes] are the ones saved on the device (offline banner).
 */
data class PodcastDetail(
    val episodes: List<Episode>,
    val feed: Feed?,
    val hiddenExplicitCount: Int,
    val isExplicitFeedHidden: Boolean,
    val isFromCache: Boolean = false,
)
