package tss.t.shared.newshub

import tss.t.shared.model.Episode
import tss.t.shared.text.VietnameseFold

/**
 * "Điểm tin sáng" (M6 §3.4): a playlist of the latest bulletins, picked by fixed rules. No AI,
 * no rewritten title or description: every item is a publisher's own episode.
 *
 * @property items in play order.
 * @property totalDurationSeconds sum of the episodes' `duration` (0 when PodcastIndex has none).
 */
class MorningBrief(val items: List<NewsEpisode>) {
    val totalDurationSeconds: Long = items.sumOf { it.episode.duration.coerceAtLeast(0L) }

    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        /** Vietnam's UTC+7, fixed (no daylight saving): "before 11:00" is the publisher's clock. */
        const val VN_OFFSET_MILLIS: Long = 7L * MILLIS_PER_HOUR

        val Empty = MorningBrief(emptyList())

        /**
         * Builds the brief from [sources] at [nowMillis]:
         * - sources in the given (config) order; sources without a brief rule are skipped;
         * - per source, episodes published at most `maxAgeHours` before [nowMillis];
         * - when the rule has a `preferTitle` and the hour in UTC+7 is before
         *   `preferTitleBeforeHour` (or no hour is set), the newest episode whose title contains
         *   it, accents and case ignored ([VietnameseFold]), comes first; without one, newest first;
         * - the first `take` episodes; a source with none in its window is left out.
         */
        fun build(sources: List<ResolvedSource>, nowMillis: Long): MorningBrief {
            val hour = vietnamHour(nowMillis)
            val items = sources.flatMap { source ->
                val rule = source.source.brief ?: return@flatMap emptyList()
                val maxAgeMillis = rule.maxAgeHours.coerceAtLeast(1) * MILLIS_PER_HOUR
                val recent = source.episodes
                    .distinctBy { it.id }
                    .filter { nowMillis - it.publishedMillis() <= maxAgeMillis }
                    .sortedByDescending { it.datePublished }
                val preferred = rule.preferTitle
                    ?.takeIf { rule.preferTitleBeforeHour?.let { before -> hour < before } ?: true }
                    ?.let(VietnameseFold::fold)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { needle -> recent.firstOrNull { VietnameseFold.fold(it.title).contains(needle) } }
                val ordered = if (preferred == null) recent else listOf(preferred) + recent.filter { it !== preferred }
                ordered.take(rule.take.coerceAtLeast(1)).map { NewsEpisode(source.key, source.feed, it) }
            }
            return MorningBrief(items)
        }

        /** Hour of the day (0–23) in UTC+7 at [nowMillis]. */
        fun vietnamHour(nowMillis: Long): Int =
            (nowMillis + VN_OFFSET_MILLIS).floorDiv(MILLIS_PER_HOUR).mod(24L).toInt()
    }
}

private const val MILLIS_PER_HOUR = 3_600_000L

/** PodcastIndex sends `datePublished` in epoch seconds. */
internal fun Episode.publishedMillis(): Long = datePublished * 1_000L
