package tss.t.podcasts.usecase.history

import androidx.media3.common.MediaItem
import tss.t.core.repository.IHistoryRepository
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import javax.inject.Inject

class SaveCurrentPlayingUseCase @Inject constructor(
    private val historyRepository: IHistoryRepository
) {
    suspend operator fun invoke(
        episode: Episode,
        podcast: Podcast?,
        playList: List<Episode>
    ) {
        historyRepository.saveCurrentPlaying(
            episode = episode,
            podcast = podcast,
            listItem = playList
        )
    }

    suspend operator fun invoke(
        mediaItem: MediaItem,
    ) {
        historyRepository.saveCurrentPlaying(
            mediaItem = mediaItem.mediaId
        )
    }
}