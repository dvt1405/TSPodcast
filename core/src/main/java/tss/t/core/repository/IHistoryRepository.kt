package tss.t.core.repository

import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast

interface IHistoryRepository {
    suspend fun saveCurrentPlaying(mediaItem: String)

    suspend fun saveCurrentPlaying(
        episode: Episode,
        podcast: Podcast?,
        listItem: List<Episode>
    )

    suspend fun saveCurrentPlaying(
        mediaItemId: String,
        podcast: Podcast,
        listItem: List<Episode>
    )
}