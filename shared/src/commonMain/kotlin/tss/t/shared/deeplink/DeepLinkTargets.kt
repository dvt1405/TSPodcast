package tss.t.shared.deeplink

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.isBlockedSource
import tss.t.shared.crash.safeCallOrNull
import tss.t.shared.model.Episode
import tss.t.shared.model.Feed
import tss.t.shared.model.Podcast
import tss.t.shared.model.TSDataState
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.storage.PodcastStorage
import tss.t.shared.usecase.GetEpisodeById
import tss.t.shared.usecase.GetPodcastByFeedID

/**
 * What a [DeepLink] opens (M4 §4.5, features-spec §3.12). A link comes from outside the app, so
 * unlike a tap on a list it has not passed the blacklist or D6 yet; this class applies both.
 *
 * - [podcast]: the podcast of `/podcast/<feedId>` for the Detail route (title and cover). E11
 *   `podcasts/byfeedid` (15-minute cache), else the podcast saved earlier (offline). A blacklisted
 *   feed is refused, including one on a blocked host or by a blocked author (M6 §3.5). An explicit feed is **not** refused here: Detail already shows its
 *   "explicit hidden" state (M2).
 * - [episode]: E12 `episodes/byid` of `/episode/<id>`, plus the podcast to save and show with it
 *   (E11, else the saved row, else built from the episode). Refused when the feed id or a keyword
 *   in the title/description is blacklisted (the E2 rule for episodes), and, unless explicit
 *   content is allowed (D6), when the episode or its feed is explicit, since it would play at once.
 *
 * A refusal is `Error(`[DeepLinkRefusedException]`)`; an API failure keeps its own exception.
 */
class DeepLinkTargets internal constructor(
    private val getEpisodeById: GetEpisodeById,
    private val getPodcastByFeedID: GetPodcastByFeedID,
    private val blacklist: BlacklistRepository,
    private val preferences: AppPreferences,
    private val storage: PodcastStorage,
) {
    suspend fun podcast(feedId: Long): TSDataState<Podcast> {
        if (blacklist.isInBlacklist(feedId.toString())) return refused(Reason.Blacklisted)
        val feed = feed(feedId)
        if (feed != null) {
            if (feed.isBlocked()) return refused(Reason.Blacklisted)
            return TSDataState.Success(Podcast.fromFeed(feed))
        }
        val saved = savedPodcast(feedId)
            ?: return TSDataState.Error(NoSuchElementException("podcast link: no feed $feedId"))
        if (saved.isBlocked()) return refused(Reason.Blacklisted)
        return TSDataState.Success(saved)
    }

    suspend fun episode(episodeId: Long): TSDataState<EpisodeLinkTarget> {
        val episode = when (val result = getEpisodeById(episodeId)) {
            is TSDataState.Success -> result.data
            is TSDataState.Error -> return TSDataState.Error(result.exception)
            is TSDataState.Loading -> return TSDataState.Error(IllegalStateException("episodes/byid failed"))
        }
        if (episode.isBlocked()) return refused(Reason.Blacklisted)

        val feed = feed(episode.feedId)
        if (feed?.isBlocked() == true) return refused(Reason.Blacklisted)
        if (!preferences.allowExplicitContent() && (episode.explicit == EXPLICIT || feed?.explicit == true)) {
            return refused(Reason.Explicit)
        }
        val podcast = feed?.let(Podcast::fromFeed)
            ?: savedPodcast(episode.feedId)
            ?: podcastOf(episode)
        return TSDataState.Success(EpisodeLinkTarget(podcast, episode))
    }

    /** The E11 feed, or null when E11 fails or does not know the id. */
    private suspend fun feed(feedId: Long): Feed? =
        (getPodcastByFeedID(feedId.toString()) as? TSDataState.Success)?.data?.feed

    private suspend fun savedPodcast(feedId: Long): Podcast? =
        safeCallOrNull(TAG, mapOf("feedId" to feedId.toString())) { storage.podcastById(feedId) }

    private fun Feed.isBlocked(): Boolean =
        blacklist.isInBlacklist(id.toString()) ||
            blacklist.isContainKeywordsBlacklist(title) ||
            isBlockedSource(blacklist)

    private fun Podcast.isBlocked(): Boolean =
        blacklist.isInBlacklist(id.toString()) ||
            blacklist.isContainKeywordsBlacklist(title) ||
            isBlockedSource(blacklist)

    private fun Episode.isBlocked(): Boolean =
        blacklist.isInBlacklist(feedId.toString()) ||
            blacklist.isContainKeywordsBlacklist(title) ||
            blacklist.isContainKeywordsBlacklist(description) ||
            isBlockedSource(blacklist)

    private fun <T : Any> refused(reason: Reason): TSDataState<T> =
        TSDataState.Error(DeepLinkRefusedException(reason))

    /** Why a link was refused (logged by the app, never shown: the alert is the same). */
    enum class Reason { Blacklisted, Explicit }

    companion object {
        private const val EXPLICIT = 1
        private const val TAG = "DeepLinkTargets"

        /** The podcast of [episode] when neither E11 nor the database has it (`id = feedId`). */
        fun podcastOf(episode: Episode): Podcast = Podcast(
            id = episode.feedId,
            feedId = episode.feedId,
            feedImage = episode.feedImage,
            feedItunesId = episode.feedItunesId,
            feedLanguage = episode.feedLanguage,
            image = episode.feedImage,
            explicit = episode.explicit,
        )
    }
}

/** An episode link: play [episode] and open the Detail of [podcast] (like a favourite tap). */
data class EpisodeLinkTarget(
    val podcast: Podcast,
    val episode: Episode,
)

/** The link points to something the app must not show (blacklist, or explicit with D6 on). */
class DeepLinkRefusedException(val reason: DeepLinkTargets.Reason) :
    Exception("deep link refused: $reason")
