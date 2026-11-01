package tss.t.podcasts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.core.history.ListeningHistoryRules
import tss.t.core.models.ListeningHistory
import tss.t.core.repository.IHistoryRepository
import tss.t.core.storage.SharedPref
import tss.t.core.storage.dao.FavouriteDao
import tss.t.core.storage.dao.ListeningHistoryDao
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
    private val favouriteDao: FavouriteDao,
    private val listeningHistoryDao: ListeningHistoryDao,
) : IHistoryRepository {

    /** Read-merge-write of one row must not interleave with another update of the same row. */
    private val historyLock = Mutex()
    private var writesSincePrune = PRUNE_EVERY

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

    override fun observeHistory(): Flow<List<ListeningHistory>> = listeningHistoryDao.observeAll()

    override fun observeContinueListening(limit: Int): Flow<List<ListeningHistory>> =
        listeningHistoryDao.observeUnfinished(ListeningHistoryRules.MIN_RESUME_POSITION_MS, limit)

    override suspend fun latestResumable(): ListeningHistory? =
        listeningHistoryDao.latestUnfinished(ListeningHistoryRules.MIN_RESUME_POSITION_MS)

    override suspend fun latestPlayed(): ListeningHistory? = listeningHistoryDao.latest()

    override suspend fun historyEntry(mediaId: String): ListeningHistory? =
        listeningHistoryDao.selectById(mediaId)

    override suspend fun recordProgress(snapshot: ListeningHistory) {
        if (snapshot.mediaId.isBlank()) return
        historyLock.withLock {
            val previous = listeningHistoryDao.selectById(snapshot.mediaId)
            listeningHistoryDao.upsert(ListeningHistoryRules.merge(previous, snapshot))
            // Pruning is a full scan; a write lands every few seconds while playing.
            if (previous == null || ++writesSincePrune >= PRUNE_EVERY) {
                writesSincePrune = 0
                listeningHistoryDao.prune(ListeningHistoryRules.MAX_ENTRIES)
            }
        }
    }

    override suspend fun removeFromHistory(mediaId: String) {
        historyLock.withLock { listeningHistoryDao.delete(mediaId) }
        // The "last played" key behind the Continue shortcut and the car's resumption.
        if (runCatching { sharedPref.get<String>(EXTRA_CURRENT_ITEM_ID) }.getOrNull() == mediaId) {
            sharedPref.clear(EXTRA_CURRENT_ITEM_ID)
        }
    }

    override suspend fun clearHistory() {
        historyLock.withLock { listeningHistoryDao.clear() }
        sharedPref.clear(EXTRA_CURRENT_ITEM_ID)
    }

    companion object {
        const val EXTRA_CURRENT_ITEM_ID = "extra:current_item_id"
        private const val PRUNE_EVERY = 50
    }
}
