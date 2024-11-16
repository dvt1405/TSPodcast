package tss.t.podcasts.usecase.favourite

import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreapi.models.databaseview.PodcastAndEpisode
import javax.inject.Inject

class SelectPodcastAndEpisodeByFavourite @Inject constructor(
    private val repository: IFavouriteRepository,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) {
    suspend operator fun invoke(
        id: String,
        mediaType: MediaType
    ): PodcastAndEpisode? {
        if (mediaType == MediaType.PodcastEpisode) {
            val ep = episodeDao.selectById(id.toLong()) ?: return null
            return podcastDao.selectAllEpisodeById(ep.feedId)
        } else if (mediaType == MediaType.Podcast) {
            val pc = podcastDao.selectById(id.toLong()) ?: return null
            return podcastDao.selectAllEpisodeById(pc.feedId)
        }
        val fav = repository.getFavouriteItem(id, mediaType) ?: return null
        val items = podcastDao.selectAllEpisodeById(fav.id.toLong())
        return items
    }

    suspend operator fun invoke(
        fav: FavouriteDTO
    ): PodcastAndEpisode? {
        val mediaType = fav.type
        if (mediaType == MediaType.PodcastEpisode) {
            val ep = episodeDao.selectById(fav.id.toLong()) ?: return null
            return podcastDao.selectAllEpisodeById(ep.feedId)
        } else if (mediaType == MediaType.Podcast) {
            val pc = podcastDao.selectById(fav.id.toLong()) ?: return null
            return podcastDao.selectAllEpisodeById(pc.feedId)
        }
        return podcastDao.selectAllEpisodeById(fav.id.toLong())
    }
}