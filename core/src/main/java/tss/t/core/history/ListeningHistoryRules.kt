package tss.t.core.history

import tss.t.core.models.ListeningHistory
import java.util.Calendar
import java.util.TimeZone

/** A day of the History screen: "Hôm nay", "Hôm qua" or a date. */
data class HistoryDay(
    /** Local midnight of the day, wall-clock millis. */
    val dayStartMillis: Long,
    val kind: Kind,
    /** Last played first. */
    val entries: List<ListeningHistory>,
) {
    enum class Kind { Today, Yesterday, Earlier }
}

/**
 * Rules of the listening history and "Nghe tiếp", kept free of Android so they are unit tested.
 * Same thresholds as the per-episode resume position (`PlaybackPositions` in :sharedPlayer):
 * under [MIN_RESUME_POSITION_MS] there is nothing to resume, with less than
 * [FINISHED_REMAINING_MS] left the episode counts as heard ("Đã nghe xong").
 */
object ListeningHistoryRules {
    const val MIN_RESUME_POSITION_MS = 5_000L
    const val FINISHED_REMAINING_MS = 30_000L

    /** Rows kept; older ones are pruned on write. */
    const val MAX_ENTRIES = 500

    /** Cards in the Home "Nghe tiếp" row. */
    const val CONTINUE_LIMIT = 10

    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && durationMs - positionMs < FINISHED_REMAINING_MS

    /**
     * The row to store for a new player [snapshot] of an episode already in the history as
     * [previous] (null for the first play). Keeps what the snapshot does not know (duration before
     * the stream is prepared, artwork, show), and keeps a heard episode marked as heard while a
     * replay is still in its first seconds, so it does not flicker back to "Nghe tiếp".
     */
    fun merge(previous: ListeningHistory?, snapshot: ListeningHistory): ListeningHistory {
        val duration = snapshot.durationMs.takeIf { it > 0L } ?: previous?.durationMs ?: 0L
        val position = snapshot.positionMs.coerceAtLeast(0L)
        val finished = isFinished(position, duration) ||
            (previous?.finished == true && position < MIN_RESUME_POSITION_MS)
        return snapshot.copy(
            feedId = snapshot.feedId ?: previous?.feedId,
            title = snapshot.title.ifBlank { previous?.title.orEmpty() },
            podcastTitle = snapshot.podcastTitle.nonBlank() ?: previous?.podcastTitle,
            image = snapshot.image.nonBlank() ?: previous?.image,
            mediaUrl = snapshot.mediaUrl.nonBlank() ?: previous?.mediaUrl,
            positionMs = position,
            durationMs = duration,
            finished = finished,
        )
    }

    /** Left part-way: worth a "Nghe tiếp" card and a seek on the next play. */
    fun isResumable(entry: ListeningHistory): Boolean =
        !entry.finished && entry.positionMs >= MIN_RESUME_POSITION_MS

    /** Where playing [entry] again starts: its position, or the beginning once heard. */
    fun startPositionMs(entry: ListeningHistory): Long =
        if (isResumable(entry)) entry.positionMs else 0L

    /** 0..1 for the progress bar; 1 once heard, 0 while the length is unknown. */
    fun progress(entry: ListeningHistory): Float {
        if (entry.finished) return 1f
        if (entry.durationMs <= 0L) return 0f
        return (entry.positionMs.toDouble() / entry.durationMs).toFloat().coerceIn(0f, 1f)
    }

    /** Time left, 0 while the length is unknown. */
    fun remainingMs(entry: ListeningHistory): Long =
        if (entry.durationMs <= 0L) 0L else (entry.durationMs - entry.positionMs).coerceAtLeast(0L)

    /**
     * Groups [entries] by the local day they were last played, newest day first, each day newest
     * first. A time in the future (clock changed) counts as today.
     */
    fun groupByDay(
        entries: List<ListeningHistory>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<HistoryDay> {
        if (entries.isEmpty()) return emptyList()
        val today = startOfDay(nowMillis, timeZone)
        val yesterday = Calendar.getInstance(timeZone).run {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -1)
            startOfDay(timeInMillis, timeZone)
        }
        return entries
            .sortedByDescending { it.lastPlayedAt }
            .groupBy { minOf(startOfDay(it.lastPlayedAt, timeZone), today) }
            .map { (day, list) ->
                val kind = when (day) {
                    today -> HistoryDay.Kind.Today
                    yesterday -> HistoryDay.Kind.Yesterday
                    else -> HistoryDay.Kind.Earlier
                }
                HistoryDay(day, kind, list)
            }
            .sortedByDescending { it.dayStartMillis }
    }

    private fun startOfDay(millis: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).run {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
}
