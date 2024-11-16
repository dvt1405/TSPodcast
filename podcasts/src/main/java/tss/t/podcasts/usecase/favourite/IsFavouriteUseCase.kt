package tss.t.podcasts.usecase.favourite

import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.Podcast
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
}