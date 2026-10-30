package tss.t.shared.home

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tss.t.shared.blacklist.HiddenShowsRepository
import tss.t.shared.blacklist.withoutHidden
import tss.t.shared.model.LiveEpisode
import tss.t.shared.model.Podcast
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.usecase.GetLiveEpisodes
import tss.t.shared.usecase.GetMusicFeeds
import tss.t.shared.usecase.GetRecentFeeds
import tss.t.shared.usecase.GetTrendingPodcasts
import tss.t.shared.util.EpochClock

/**
 * The calls behind Home/Discover, with the parameters of Android's `MainViewModel.getTrending`
 * (features-spec §3.4). Each section loads on its own, so one failure never blocks another.
 *
 * Every list is blacklist-filtered by its use case and then, unless
 * [AppPreferences.allowExplicitContent], loses its explicit items (D6). E1 trending and E5
 * recent feeds do not send `explicit`, so their items always pass that filter (an API limit,
 * see handoff). Last, the shows the user hid ([HiddenShowsRepository], M6 §3.5) are dropped:
 * podcasts by `id`/`feedId`, live items by `feedId`. E3 `recent/episodes` and E4
 * `recent/newfeeds` are never called ([Sửa]).
 */
class HomeFeed internal constructor(
    private val getTrendingPodcasts: GetTrendingPodcasts,
    private val getMusicFeeds: GetMusicFeeds,
    private val getLiveEpisodes: GetLiveEpisodes,
    private val getRecentFeeds: GetRecentFeeds,
    private val preferences: AppPreferences,
    private val hiddenShows: HiddenShowsRepository,
    private val clock: EpochClock,
) {
    /**
     * "Podcasts thịnh hành" (E1, last 7 days, no category) and "Có thể bạn yêu thích" (E1, last
     * 10 days, `cat = favouriteCatIds`), requested in parallel, with `MainViewModel`'s fallback:
     * when only one of them succeeds, both sections show its list; when both fail, both are
     * `Error` (the screen offers a retry instead of Android's empty lists).
     */
    suspend fun trendingSections(favouriteCatIds: String?): TrendingSections = coroutineScope {
        val nowSeconds = clock.nowMillis() / 1_000
        val trendingCall = async {
            getTrendingPodcasts(max = MAX_TRENDING, since = nowSeconds - TRENDING_WINDOW_SECONDS)
        }
        val forYouCall = async {
            getTrendingPodcasts(
                max = MAX_TRENDING,
                since = nowSeconds - FOR_YOU_WINDOW_SECONDS,
                cat = favouriteCatIds,
            )
        }
        val allowExplicit = preferences.allowExplicitContent()
        val hidden = hiddenShows.hiddenIds()
        val trending = trendingCall.await().toPodcasts(allowExplicit, hidden)
        val forYou = forYouCall.await().toPodcasts(allowExplicit, hidden)

        when {
            trending is TSDataState.Success && forYou is TSDataState.Success -> TrendingSections(trending, forYou)
            trending is TSDataState.Success -> TrendingSections(trending, trending)
            forYou is TSDataState.Success -> TrendingSections(forYou, forYou)
            else -> TrendingSections(trending, forYou)
        }
    }

    /** "Kho nhạc": E6 `medium=music`, at most 40 feeds. */
    suspend fun music(): TSDataState<List<Podcast>> {
        val allowExplicit = preferences.allowExplicitContent()
        return when (val result = getMusicFeeds(max = MAX_MUSIC)) {
            is TSDataState.Success -> TSDataState.Success(
                result.data.filterExplicit(allowExplicit).withoutHidden(hiddenShows.hiddenIds()),
            )
            else -> result
        }
    }

    /** "Live Podcasts": E2, at most 100 items. */
    suspend fun live(): TSDataState<List<LiveEpisode>> {
        val allowExplicit = preferences.allowExplicitContent()
        return when (val result = getLiveEpisodes(max = MAX_LIVE)) {
            is TSDataState.Success -> {
                val hidden = hiddenShows.hiddenIds()
                TSDataState.Success(
                    result.data.items.filter { (allowExplicit || it.explicit != EXPLICIT) && it.feedId !in hidden },
                )
            }

            is TSDataState.Error -> TSDataState.Error(result.exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }
    }

    /** "Podcasts xu hướng gần đây": E5 `recent/feeds`, `cat = favouriteCatIds`, no `notcat`. */
    suspend fun recent(favouriteCatIds: String?): TSDataState<List<Podcast>> {
        val allowExplicit = preferences.allowExplicitContent()
        val result = getRecentFeeds(max = MAX_RECENT, lang = LANG, cat = favouriteCatIds, notcat = null)
        return result.toPodcasts(allowExplicit, hiddenShows.hiddenIds())
    }

    private fun TSDataState<TrendingPodcastRes>.toPodcasts(
        allowExplicit: Boolean,
        hidden: Set<Long>,
    ): TSDataState<List<Podcast>> = when (this) {
        is TSDataState.Success -> TSDataState.Success(data.items.filterExplicit(allowExplicit).withoutHidden(hidden))
        is TSDataState.Error -> TSDataState.Error(exception)
        is TSDataState.Loading -> TSDataState.Loading()
    }

    private fun List<Podcast>.filterExplicit(allowExplicit: Boolean): List<Podcast> =
        if (allowExplicit) this else filter { it.explicit != EXPLICIT }

    companion object {
        const val MAX_TRENDING = 100
        const val MAX_MUSIC = 40
        const val MAX_LIVE = 100
        const val MAX_RECENT = 100
        const val LANG = "vi"

        /** `now − 7 days`, in seconds (`MainViewModel`: `Constants.A_DAY * 7`). */
        const val TRENDING_WINDOW_SECONDS = 7L * 24 * 60 * 60

        /** `now − 10 days`, in seconds. */
        const val FOR_YOU_WINDOW_SECONDS = 10L * 24 * 60 * 60

        private const val EXPLICIT = 1
    }
}

/** Result of [HomeFeed.trendingSections]: one state per section, after the fallback. */
data class TrendingSections(
    val trending: TSDataState<List<Podcast>>,
    val forYou: TSDataState<List<Podcast>>,
)
