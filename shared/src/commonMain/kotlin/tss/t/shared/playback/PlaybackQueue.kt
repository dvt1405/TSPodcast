package tss.t.shared.playback

/**
 * The playlist handed to the player and the position in it (Android sets the whole list on
 * ExoPlayer and seeks to the tapped item, `PlayerManager.playMedia`). Immutable: every move
 * returns a new queue, or null when the move is not possible, like `hasNextMediaItem()` /
 * `hasPreviousMediaItem()` guarding `onSkipToNext` / `onSkipToPrevious`.
 */
class PlaybackQueue private constructor(
    val items: List<PlayableItem>,
    val index: Int,
) {
    val current: PlayableItem get() = items[index]

    val size: Int get() = items.size

    val hasPrevious: Boolean get() = index > 0

    val hasNext: Boolean get() = index < items.lastIndex

    fun next(): PlaybackQueue? = select(index + 1)

    fun previous(): PlaybackQueue? = select(index - 1)

    /** The same items with [newIndex] current, or null when it is out of range. */
    fun select(newIndex: Int): PlaybackQueue? =
        if (newIndex in items.indices) PlaybackQueue(items, newIndex) else null

    override fun equals(other: Any?): Boolean =
        other is PlaybackQueue && other.index == index && other.items == items

    override fun hashCode(): Int = 31 * items.hashCode() + index

    override fun toString(): String = "PlaybackQueue(index=$index, size=${items.size})"

    companion object {
        /** A queue starting at [startIndex] (clamped to the list), or null for an empty list. */
        fun of(items: List<PlayableItem>, startIndex: Int): PlaybackQueue? {
            if (items.isEmpty()) return null
            return PlaybackQueue(items.toList(), startIndex.coerceIn(0, items.lastIndex))
        }
    }
}
