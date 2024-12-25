package tss.t.podcasts.usecase.favourite

import androidx.media3.common.MediaItem
import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.Podcast
import tss.t.podcasts.utils.ext.toFavouriteDTO
import javax.inject.Inject

class SaveFavouriteUseCase @Inject constructor(
    private val repository: IFavouriteRepository
) {
    suspend operator fun invoke(podcast: Podcast) {
        repository.saveFavourite(
            true, FavouriteDTO(
                id = podcast.id.toString(),
                type = MediaType.Podcast,
                title = podcast.title,
                image = podcast.image,
                description = podcast.description
            )
        )
    }

    suspend operator fun invoke(feed: Feed) {
        repository.saveFavourite(
            true, FavouriteDTO(
                id = feed.id.toString(),
                type = MediaType.Podcast,
                title = feed.title,
                image = feed.image,
                description = feed.description
            )
        )
    }

    suspend operator fun invoke(episode: Episode) {
        repository.saveFavourite(
            true, FavouriteDTO(
                id = episode.id.toString(),
                type = MediaType.PodcastEpisode,
                title = episode.title,
                image = episode.image,
                description = episode.description
            )
        )
    }

    suspend operator fun invoke(mediaItem: MediaItem) {
        repository.saveFavourite(
            isFav = true,
            favouriteDTO = mediaItem.toFavouriteDTO()
        )
    }
}