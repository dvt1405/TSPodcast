package tss.t.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** M6-AC4: pause at the end like `setPauseAtEndOfMediaItems(true)`, advance only when asked. */
class EndOfItemPolicyTest {
    @Test
    fun pausesByDefault() {
        assertEquals(EndOfItemAction.Pause, EndOfItemPolicy.decide(autoAdvance = false, hasNext = true, timerStopsHere = false))
    }

    @Test
    fun autoAdvanceMovesOnWhenThereIsANextItem() {
        assertEquals(EndOfItemAction.Advance, EndOfItemPolicy.decide(autoAdvance = true, hasNext = true, timerStopsHere = false))
    }

    @Test
    fun autoAdvancePausesAtTheLastItem() {
        assertEquals(EndOfItemAction.Pause, EndOfItemPolicy.decide(autoAdvance = true, hasNext = false, timerStopsHere = false))
    }

    @Test
    fun endOfEpisodeTimerWins() {
        assertEquals(EndOfItemAction.Pause, EndOfItemPolicy.decide(autoAdvance = true, hasNext = true, timerStopsHere = true))
    }
}
