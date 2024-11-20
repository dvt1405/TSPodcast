package tss.t.podcasts.usecase.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.databaseview.PodcastAndEpisode
import javax.inject.Inject

class GetEpisodeLocalUseCase @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) {
    suspend operator fun invoke(mediaId: Long): Episode? {
        return withContext(Dispatchers.IO) {
            episodeDao.selectById(mediaId)
        }
    }

    suspend fun getRelated(feedId: Long): PodcastAndEpisode? {
        return withContext(Dispatchers.IO) {
            podcastDao.selectAllEpisodeById(feedId)
        }
    }
}