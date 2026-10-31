package tss.t.shared.playback

import tss.t.shared.util.EpochClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M6-AC3: the sleep timer state machine (M6 §3.1, audio-playback-ios §7). */
class SleepTimerTest {
    private val t0 = 1_700_000_000_000L
    private var now = t0
    private val timer = SleepTimer(EpochClock { now })

    @Test
    fun startsOff() {
        assertEquals(SleepTimerState(SleepTimerOption.Off, null, null), timer.state)
        assertEquals(SleepTimerAction.None, timer.tick())
    }

    @Test
    fun minutesCountDownFadeLinearlyThenStop() {
        timer.set(SleepTimerOption.Minutes(30))
        assertEquals(1_800_000L, timer.state.remainingMillis)
        assertEquals(t0 + 1_800_000L, timer.state.deadlineMillis)

        now = t0 + 1_789_999
        assertEquals(SleepTimerAction.None, timer.tick())
        assertEquals(10_001L, timer.state.remainingMillis)

        now = t0 + 1_790_000
        assertEquals(SleepTimerAction.Fade(1.0f), timer.tick())

        now = t0 + 1_795_000
        assertEquals(SleepTimerAction.Fade(0.5f), timer.tick())

        now = t0 + 1_799_000
        assertEquals(SleepTimerAction.Fade(0.1f), timer.tick())

        now = t0 + 1_800_000
        assertEquals(SleepTimerAction.Stop, timer.tick())
        assertEquals(SleepTimerOption.Off, timer.state.option)
        assertNull(timer.state.deadlineMillis)
        assertEquals(SleepTimerAction.None, timer.tick(), "stops only once")
    }

    @Test
    fun aLateTickStillStops() {
        timer.set(SleepTimerOption.Minutes(5))
        now = t0 + 3_600_000
        assertEquals(SleepTimerAction.Stop, timer.tick())
        assertEquals(SleepTimerOption.Off, timer.state.option)
    }

    @Test
    fun manualPauseCancelsWhileCounting() {
        timer.set(SleepTimerOption.Minutes(15))
        now = t0 + 60_000
        timer.onManualPause()
        assertEquals(SleepTimerOption.Off, timer.state.option)
        now = t0 + 900_000
        assertEquals(SleepTimerAction.None, timer.tick())
    }

    @Test
    fun manualPauseCancelsWhileFading() {
        timer.set(SleepTimerOption.Minutes(1))
        now = t0 + 55_000
        assertEquals(SleepTimerAction.Fade(0.5f), timer.tick())
        timer.onManualPause()
        assertEquals(SleepTimerOption.Off, timer.state.option)
        now = t0 + 61_000
        assertEquals(SleepTimerAction.None, timer.tick())
    }

    @Test
    fun endOfEpisodeNeverTicksAndStopsAtTheEndOfTheItem() {
        timer.set(SleepTimerOption.EndOfEpisode)
        assertNull(timer.state.deadlineMillis)
        assertNull(timer.state.remainingMillis)
        now = t0 + 100 * 3_600_000L
        assertEquals(SleepTimerAction.None, timer.tick())
        assertEquals(SleepTimerOption.EndOfEpisode, timer.state.option)

        assertTrue(timer.onItemEnded())
        assertEquals(SleepTimerOption.Off, timer.state.option)
        assertFalse(timer.onItemEnded(), "only once")
    }

    @Test
    fun minutesTimerDoesNotStopAtTheEndOfAnItem() {
        timer.set(SleepTimerOption.Minutes(30))
        assertFalse(timer.onItemEnded())
        assertEquals(SleepTimerOption.Minutes(30), timer.state.option)
        assertFalse(SleepTimer(EpochClock { now }).onItemEnded(), "off")
    }

    @Test
    fun minutesOutsideTheRangeThrow() {
        assertFailsWith<IllegalArgumentException> { timer.set(SleepTimerOption.Minutes(0)) }
        assertFailsWith<IllegalArgumentException> { timer.set(SleepTimerOption.Minutes(181)) }
        assertEquals(SleepTimerOption.Off, timer.state.option)
        timer.set(SleepTimerOption.Minutes(1))
        timer.set(SleepTimerOption.Minutes(180))
        assertEquals(180 * 60_000L, timer.state.remainingMillis)
    }

    @Test
    fun clampMinutesForFreeValues() {
        assertEquals(1, SleepTimer.clampMinutes(0))
        assertEquals(1, SleepTimer.clampMinutes(-5))
        assertEquals(45, SleepTimer.clampMinutes(45))
        assertEquals(180, SleepTimer.clampMinutes(500))
    }

    @Test
    fun presets() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60), SleepTimer.PRESET_MINUTES)
    }

    @Test
    fun settingAgainReplacesTheDeadline() {
        timer.set(SleepTimerOption.Minutes(30))
        now = t0 + 600_000
        timer.set(SleepTimerOption.Minutes(5))
        assertEquals(t0 + 600_000 + 300_000, timer.state.deadlineMillis)
        assertEquals(300_000L, timer.state.remainingMillis)

        timer.set(SleepTimerOption.EndOfEpisode)
        assertNull(timer.state.deadlineMillis)
        timer.set(SleepTimerOption.Off)
        assertEquals(SleepTimerState(SleepTimerOption.Off, null, null), timer.state)
    }

    @Test
    fun remainingIsNeverNegative() {
        timer.set(SleepTimerOption.Minutes(5))
        now = t0 + 400_000
        assertEquals(0L, timer.state.remainingMillis)
    }
}
