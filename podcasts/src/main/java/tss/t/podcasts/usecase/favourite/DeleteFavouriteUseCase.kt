package tss.t.podcasts.usecase.favourite

import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.Podcast
import javax.inject.Inject

class DeleteFavouriteUseCase @Inject constructor(
    private val repository: IFavouriteRepository
) {
    suspend operator fun invoke(podcast: Podcast) {
        repository.saveFavourite(
            false, FavouriteDTO(
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
            false, FavouriteDTO(
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
            false, FavouriteDTO(
                id = episode.id.toString(),
                type = MediaType.PodcastEpisode,
                title = episode.title,
                image = episode.image,
                description = episode.description
            )
        )
    }
}