package tss.t.sharedplayer.library

/**
 * Media ids of the browse tree that Android Auto (and later Android Automotive OS) shows.
 *
 * ```
 * root
 * ├── continue          "Tiếp tục nghe": the last episode + its show
 * ├── favourites        "Yêu thích": favourite shows (browsable) and episodes (playable)
 * └── trending          "Thịnh hành": trending shows (browsable)
 *     └── podcast:<id>  the episodes of one show (playable)
 * ```
 *
 * Episodes keep the id the phone player already uses (`"${episode.id}"`), so the resume
 * position, favourite state and "Tiếp tục nghe" stay shared between phone and car. Every
 * browsable node has a prefix, so a bare number is always an episode.
 */
object LibraryIds {
    const val ROOT = "root"

    /** Root handed to System UI / Auto when they ask for the "recent" root (playback resumption). */
    const val RECENT_ROOT = "recent_root"

    const val CONTINUE = "continue"
    const val FAVOURITES = "favourites"
    const val TRENDING = "trending"

    private const val PODCAST_PREFIX = "podcast:"

    /** Root tabs in display order. Auto shows at most 4 tabs. */
    val ROOT_CHILDREN = listOf(CONTINUE, FAVOURITES, TRENDING)

    fun podcast(feedId: Long): String = PODCAST_PREFIX + feedId

    /** The show id of a `podcast:<id>` node, or null for any other id. */
    fun podcastIdOf(mediaId: String): Long? =
        mediaId.takeIf { it.startsWith(PODCAST_PREFIX) }
            ?.removePrefix(PODCAST_PREFIX)
            ?.toLongOrNull()

    /** The episode id of a playable item (a bare number), or null for a browsable node. */
    fun episodeIdOf(mediaId: String): Long? = mediaId.toLongOrNull()

    fun isRootChild(mediaId: String): Boolean = mediaId in ROOT_CHILDREN
}

/** One page of [items]; an empty list past the end (the browse callbacks are paginated). */
fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return emptyList()
    val from = page.toLong() * pageSize
    if (from >= size) return emptyList()
    val to = minOf(size.toLong(), from + pageSize).toInt()
    return subList(from.toInt(), to)
}
