package tss.t.shared.playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.shared.preferences.KeyValueStore

/**
 * What Android's `HistoryRepository.saveCurrentPlaying` records when an item starts: the id of
 * the current item under `extra:current_item_id` (persisted), plus the played episodes. Android
 * writes the episodes and the podcast to Room; until Room KMP lands (M4) they stay in memory
 * here ([recentItems], newest first, at most [MAX_RECENT_ITEMS]).
 */
class PlaybackHistory(private val store: KeyValueStore) {
    private val mutex = Mutex()
    private val recent = ArrayDeque<PlayableItem>()

    suspend fun saveCurrent(item: PlayableItem) {
        store.putString(KEY_CURRENT_ITEM_ID, item.id)
        mutex.withLock {
            recent.removeAll { it.id == item.id }
            recent.addFirst(item)
            while (recent.size > MAX_RECENT_ITEMS) recent.removeLast()
        }
    }

    suspend fun currentItemId(): String? = store.getString(KEY_CURRENT_ITEM_ID)

    suspend fun recentItems(): List<PlayableItem> = mutex.withLock { recent.toList() }

    companion object {
        /** Key of Android `HistoryRepository.EXTRA_CURRENT_ITEM_ID`. */
        const val KEY_CURRENT_ITEM_ID = "extra:current_item_id"
        const val MAX_RECENT_ITEMS = 100
    }
}
