package tss.t.shared.playback

import tss.t.shared.model.Episode
import tss.t.shared.model.LiveEpisode
import tss.t.shared.model.Podcast
import tss.t.shared.preferences.KeyValueStore
import tss.t.shared.storage.PodcastStorage

/**
 * Android `HistoryRepository.saveCurrentPlaying` (features-spec §3.5 "Ghi DB", M4 §3.2):
 *
 * - a tap that starts a list (Detail, Favourite, deep link) writes the whole playlist and the
 *   podcast to Room (`cachedAt = now`) and the id of the episode under `extra:current_item_id`
 *   ([savePlaying]); a Home Live tap does the same through `PlayerViewModel.playLive` ([saveLive]);
 * - moving inside the queue (previous, next, Up next) only changes the key ([saveCurrentId]).
 */
class PlaybackHistory(
    private val store: KeyValueStore,
    private val storage: PodcastStorage,
) {
    /** Android `saveCurrentPlaying(episode, podcast, listItem)`: episodes, podcast, then the key. */
    suspend fun savePlaying(episode: Episode, podcast: Podcast?, playlist: List<Episode>) {
        storage.insertEpisodes(playlist)
        if (podcast != null) storage.insertPodcast(podcast)
        store.putString(KEY_CURRENT_ITEM_ID, episode.id.toString())
    }

    /** Android `playLive`: every live item as an `Episode` and a podcast built from [live]. */
    suspend fun saveLive(live: LiveEpisode, playlist: List<LiveEpisode>) {
        savePlaying(
            episode = Episode.fromLive(live),
            podcast = Podcast.fromLive(live),
            playlist = playlist.map(Episode::fromLive),
        )
    }

    /** Android `saveCurrentPlaying(mediaItem)`: previous/next/Up next, the key only. */
    suspend fun saveCurrentId(itemId: String) {
        store.putString(KEY_CURRENT_ITEM_ID, itemId)
    }

    suspend fun currentItemId(): String? = store.getString(KEY_CURRENT_ITEM_ID)

    companion object {
        /** Key of Android `HistoryRepository.EXTRA_CURRENT_ITEM_ID`. */
        const val KEY_CURRENT_ITEM_ID = "extra:current_item_id"
    }
}
