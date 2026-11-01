package tss.t.sharedplayer.player

import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.core.models.ListeningHistory
import tss.t.core.repository.IHistoryRepository
import tss.t.core.repository.MediaType
import tss.t.sharedplayer.utils.ext.mediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes what the player plays into the local listening history (Room), off the main thread and
 * in order. [PlayerManager] calls it wherever it saves the resume position: every few seconds
 * while playing, on pause (user, audio focus, headphones unplugged), before switching episode and
 * when the session service goes away.
 */
@Singleton
class ListeningRecorder @Inject constructor(
    private val historyRepository: IHistoryRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun record(item: MediaItem, positionMs: Long, durationMs: Long) {
        val snapshot = item.toHistorySnapshot(positionMs, durationMs) ?: return
        scope.launch { write(snapshot) }
    }

    /**
     * Same as [record] but waits (bounded) for the row to be written: for the last save before
     * the process may die (task swiped away, service destroyed).
     */
    fun recordNow(item: MediaItem, positionMs: Long, durationMs: Long) {
        val snapshot = item.toHistorySnapshot(positionMs, durationMs) ?: return
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(FINAL_WRITE_TIMEOUT_MS) { write(snapshot) }
        }
    }

    private suspend fun write(snapshot: ListeningHistory) {
        try {
            historyRepository.recordProgress(snapshot)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save listening history", e)
        }
    }

    private companion object {
        const val TAG = "ListeningRecorder"
        const val FINAL_WRITE_TIMEOUT_MS = 800L
    }
}

/**
 * The history row for [this] item at [positionMs]; null for what has no place in it (radio,
 * items without an id). Live podcast streams are filtered by the caller, which knows the player.
 */
fun MediaItem.toHistorySnapshot(
    positionMs: Long,
    durationMs: Long,
    now: Long = System.currentTimeMillis(),
): ListeningHistory? {
    if (mediaId.isBlank() || mediaType == MediaType.Radio || mediaType == MediaType.TVChannel) {
        return null
    }
    val metadata = mediaMetadata
    return ListeningHistory(
        mediaId = mediaId,
        feedId = feedId,
        title = (metadata.title ?: metadata.displayTitle)?.toString().orEmpty(),
        podcastTitle = (metadata.albumTitle ?: metadata.artist)?.toString(),
        image = metadata.artworkUri?.toString(),
        mediaUrl = localConfiguration?.uri?.toString(),
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        finished = false,
        lastPlayedAt = now,
    )
}
