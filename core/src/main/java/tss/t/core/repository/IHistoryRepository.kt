package tss.t.core.repository

import android.media.browse.MediaBrowser.MediaItem
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast

interface IHistoryRepository {
    suspend fun saveCurrentPlaying(
        episode: Episode,
        podcast: Podcast?,
        listItem: List<Episode>
    )

    suspend fun saveCurrentPlaying(
        episode: MediaItem,
        podcast: Podcast,
        listItem: List<Episode>
    )
}