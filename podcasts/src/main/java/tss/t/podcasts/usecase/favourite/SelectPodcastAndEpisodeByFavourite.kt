package tss.t.podcasts.usecase.favourite

import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreapi.models.databaseview.PodcastAndEpisode
import tss.t.coreradio.storage.dao.RadioChannelDao
import javax.inject.Inject

class SelectPodcastAndEpisodeByFavourite @Inject constructor(
    private val repository: IFavouriteRepository,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val radioChannelDao: RadioChannelDao
) {
    suspend operator fun invoke(
        id: String,
        mediaType: MediaType
    ): PodcastAndEpisode? {
        // FavouriteDTO.id is a String because radio channels key on a slug or a
        // URL, not a numeric feed id. toLong() threw NumberFormatException on
        // those rows; toLongOrNull() turns it into "not found".
        if (mediaType == MediaType.PodcastEpisode) {
            val ep = episodeDao.selectById(id.toLongOrNull() ?: return null) ?: return null
            return podcastDao.selectAllEpisodeById(ep.feedId)
        } else if (mediaType == MediaType.Podcast) {
            val pc = podcastDao.selectById(id.toLongOrNull() ?: return null) ?: return null
            return podcastDao.selectAllEpisodeById(pc.feedId)
        }
        val fav = repository.getFavouriteItem(id, mediaType) ?: return null
        val items = podcastDao.selectAllEpisodeById(fav.id.toLongOrNull() ?: return null)
        return items
    }

    private suspend fun getPodcastAndEpisode(
        fav: FavouriteDTO
    ): PodcastAndEpisode? {
        val mediaType = fav.type
        if (mediaType == MediaType.PodcastEpisode) {
            val ep = episodeDao.selectById(fav.id.toLongOrNull() ?: return null) ?: return null
            return podcastDao.selectAllEpisodeById(ep.feedId)
        } else if (mediaType == MediaType.Podcast) {
            val pc = podcastDao.selectById(fav.id.toLongOrNull() ?: return null) ?: return null
            return podcastDao.selectAllEpisodeById(pc.feedId)
        }
        return podcastDao.selectAllEpisodeById(fav.id.toLongOrNull() ?: return null)
    }

    suspend operator fun <T> invoke(
        fav: FavouriteDTO,
    ): T {
        return when (fav.type) {
            MediaType.Radio -> radioChannelDao.getAll() as T
            else -> getPodcastAndEpisode(fav) as T
        }
    }
}