package tss.t.podcasts.usecase.favourite

import androidx.media3.common.MediaItem
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.Podcast
import tss.t.podcasts.utils.ext.mediaTypeConvert
import javax.inject.Inject

class IsFavouriteUseCase @Inject constructor(
    private val repository: IFavouriteRepository
) {
    suspend operator fun invoke(podcast: Podcast): Boolean {
        return repository.isFavourite(
            podcast.id.toString(),
            MediaType.Podcast
        )
    }

    suspend operator fun invoke(feed: Feed): Boolean {
        return repository.isFavourite(
            feed.id.toString(),
            MediaType.Podcast
        )
    }

    suspend operator fun invoke(episode: Episode): Boolean {
        return repository.isFavourite(
            episode.id.toString(),
            MediaType.PodcastEpisode
        )
    }

    suspend operator fun invoke(mediaItem: MediaItem): Boolean {
        return repository.isFavourite(
            mediaItem.mediaId,
            mediaItem.mediaTypeConvert()
        )
    }
}