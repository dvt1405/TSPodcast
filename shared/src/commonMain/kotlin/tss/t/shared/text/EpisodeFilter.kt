package tss.t.shared.text

import tss.t.shared.model.Episode

/**
 * "Tìm trong show" (M6 §3.3, N5): the episodes of one show matched against a typed query, without
 * accents or case ([VietnameseFold]).
 *
 * [index] folds every title and description (HTML turned into text by [HtmlText]) once; [filter]
 * then only folds the query, splits it into words and keeps the episodes that contain **every**
 * word in their title or description, in their original order (newest first from the API). A
 * blank query keeps everything.
 */
class EpisodeFilter private constructor(
    val episodes: List<Episode>,
    private val haystacks: List<String>,
) {
    val size: Int get() = episodes.size

    fun filter(query: String): List<Episode> = matchingIndices(query).map { episodes[it] }

    /** Positions in [episodes] of the matches, ascending. */
    fun matchingIndices(query: String): List<Int> {
        val words = VietnameseFold.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return episodes.indices.toList()
        return haystacks.indices.filter { index ->
            val haystack = haystacks[index]
            words.all { haystack.contains(it) }
        }
    }

    companion object {
        fun index(episodes: List<Episode>): EpisodeFilter = EpisodeFilter(
            episodes = episodes,
            haystacks = episodes.map { episode ->
                VietnameseFold.fold(episode.title + "\n" + HtmlText.toPlainText(episode.description))
            },
        )
    }
}
