package tss.t.shared.playback

/** What happens when an item plays to its end. */
enum class EndOfItemAction { Advance, Pause }

/**
 * End of an item (M6 §3.1): pause like Android `setPauseAtEndOfMediaItems(true)`, unless the
 * request asked for auto-advance (only "Play all" of the morning brief), there is a next item
 * and the sleep timer does not stop here.
 */
object EndOfItemPolicy {
    fun decide(autoAdvance: Boolean, hasNext: Boolean, timerStopsHere: Boolean): EndOfItemAction =
        if (autoAdvance && hasNext && !timerStopsHere) EndOfItemAction.Advance else EndOfItemAction.Pause
}
