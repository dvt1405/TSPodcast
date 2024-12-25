package tss.t.podcasts

import tss.t.core.repository.IHistoryRepository
import tss.t.core.storage.SharedPref
import tss.t.core.storage.dao.FavouriteDao
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val sharedPref: SharedPref,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val favouriteDao: FavouriteDao
) : IHistoryRepository {
    override suspend fun saveCurrentPlaying(mediaItem: String) {
        sharedPref.save(EXTRA_CURRENT_ITEM_ID, mediaItem)
    }

    override suspend fun saveCurrentPlaying(
        episode: Episode,
        podcast: Podcast?,
        listItem: List<Episode>
    ) {
        episodeDao.inserts(listItem)
        if (podcast != null) {
            podcastDao.insert(podcast)
        }
        sharedPref.save(EXTRA_CURRENT_ITEM_ID, episode.id.toString())
    }

    override suspend fun saveCurrentPlaying(
        mediaItemId: String,
        podcast: Podcast,
        listItem: List<Episode>
    ) {
        sharedPref.save(EXTRA_CURRENT_ITEM_ID, mediaItemId)
        episodeDao.inserts(listItem)
        podcastDao.insert(podcast)
    }

    companion object {
        const val EXTRA_CURRENT_ITEM_ID = "extra:current_item_id"
    }
}