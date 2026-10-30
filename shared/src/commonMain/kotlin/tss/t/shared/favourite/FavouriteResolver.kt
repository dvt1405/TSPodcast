package tss.t.shared.favourite

import tss.t.shared.model.Episode
import tss.t.shared.model.Podcast
import tss.t.shared.storage.PodcastStorage

/**
 * Port of Android `SelectPodcastAndEpisodeByFavourite` plus the selection that
 * `FavouriteScreen.onFavouriteSelected` does with its result (features-spec §3.8 Tap):
 *
 * - episode favourite → the episode row → its `feedId` → that podcast and every saved episode;
 * - podcast favourite → the podcast row → `selectAllEpisodeById(podcast.feedId)`.
 *
 * Episodes are sorted by `datePublished` descending (E10 order) and [ResolvedFavourite.startIndex]
 * points at the favourite episode, or 0 when it is not in the list (Android plays the first one).
 *
 * Returns null, so the UI can offer to remove the dead row (M4 §3.3 [Sửa]), when the id is not a
 * number (`toLongOrNull`, as Android), a row is missing, or the podcast has no saved episode.
 */
class FavouriteResolver(private val storage: PodcastStorage) {

    suspend fun resolve(item: FavouriteItem): ResolvedFavourite? = resolve(item.type, item.id)

    suspend fun resolve(type: FavouriteType, id: String): ResolvedFavourite? {
        val numericId = id.toLongOrNull() ?: return null
        val feedId = when (type) {
            FavouriteType.PodcastEpisode -> storage.episodeById(numericId)?.feedId
            FavouriteType.Podcast -> storage.podcastById(numericId)?.let { it.feedId ?: it.id }
        } ?: return null
        val saved = storage.podcastWithEpisodes(feedId) ?: return null
        if (saved.episodes.isEmpty()) return null
        val episodes = saved.episodes.sortedWith(compareByDescending<Episode> { it.datePublished }.thenByDescending { it.id })
        val startIndex = when (type) {
            FavouriteType.PodcastEpisode -> episodes.indexOfFirst { it.id == numericId }.coerceAtLeast(0)
            FavouriteType.Podcast -> 0
        }
        return ResolvedFavourite(saved.podcast, episodes, startIndex)
    }
}

/** What a favourite tap plays: [episodes] of [podcast], starting at [startIndex]. */
data class ResolvedFavourite(
    val podcast: Podcast,
    val episodes: List<Episode>,
    val startIndex: Int,
)
