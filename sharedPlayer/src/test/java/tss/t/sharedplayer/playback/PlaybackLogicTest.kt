package tss.t.sharedplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MapStore : PlaybackKeyValue {
    val longs = mutableMapOf<String, Long>()
    val booleans = mutableMapOf<String, Boolean>()
    override fun getLong(key: String) = longs[key]
    override fun putLong(key: String, value: Long) { longs[key] = value }
    override fun getBoolean(key: String) = booleans[key]
    override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
    override fun remove(key: String) { longs.remove(key); booleans.remove(key) }
    override fun removeAll(prefix: String) {
        longs.keys.removeAll { it.startsWith(prefix) }
        booleans.keys.removeAll { it.startsWith(prefix) }
    }
}

class SleepTimerTest {
    private var now = 1_000_000L
    private val timer = SleepTimer { now }

    @Test
    fun minutesFadeInTheLastTenSecondsThenStop() {
        timer.set(SleepTimerOption.Minutes(30))
        assertEquals(1_000_000L + 30 * 60_000L, timer.state.deadlineMillis)

        now += 30 * 60_000L - 20_000L
        assertEquals(SleepTimerAction.None, timer.tick())

        now += 15_000L // 5 s left
        val fade = timer.tick() as SleepTimerAction.Fade
        assertEquals(0.5f, fade.volume, 0.0001f)

        now += 5_000L
        assertEquals(SleepTimerAction.Stop, timer.tick())
        assertEquals(SleepTimerOption.Off, timer.state.option)
        assertEquals(SleepTimerAction.None, timer.tick())
    }

    @Test
    fun manualPauseCancelsAnyTimer() {
        timer.set(SleepTimerOption.Minutes(5))
        timer.onManualPause()
        assertFalse(timer.state.isActive)
        assertNull(timer.state.deadlineMillis)
    }

    @Test
    fun endOfEpisodeStopsOnlyAtTheEndOfAnItem() {
        timer.set(SleepTimerOption.EndOfEpisode)
        now += 3_600_000L
        assertEquals(SleepTimerAction.None, timer.tick())
        assertTrue(timer.onItemEnded())
        assertFalse(timer.state.isActive)
        assertFalse(timer.onItemEnded())
    }

    @Test
    fun minutesOutsideTheRangeAreRejectedAndClampable() {
        assertEquals(1, SleepTimer.clampMinutes(0))
        assertEquals(180, SleepTimer.clampMinutes(181))
        runCatching { SleepTimerOption.Minutes(0) }.let { assertTrue(it.isFailure) }
        runCatching { SleepTimerOption.Minutes(181) }.let { assertTrue(it.isFailure) }
    }

    @Test
    fun remainingTimeIsNeverNegative() {
        timer.set(SleepTimerOption.Minutes(1))
        now += 120_000L
        assertEquals(0L, timer.state.remainingMillis)
    }
}

class PlaybackSpeedTest {
    @Test
    fun clampsNormalizesAndLabels() {
        assertEquals(5, PlaybackSpeed.clamp(1))
        assertEquals(30, PlaybackSpeed.clamp(99))
        assertEquals(15, PlaybackSpeed.normalize(1.46f))
        assertEquals(10, PlaybackSpeed.normalize(Float.NaN))
        assertEquals("1.5×", PlaybackSpeed.label(15))
        assertEquals("0.8×", PlaybackSpeed.label(8))
        assertEquals(2.0f, PlaybackSpeed.rate(20), 0f)
    }
}

class PlaybackPositionsTest {
    private val store = MapStore()
    private val positions = PlaybackPositions(store)

    @Test
    fun savesAndResumesAMidEpisodePosition() {
        positions.record("42", isLive = false, positionMs = 600_000L, durationMs = 3_600_000L)
        assertEquals(600_000L, positions.resumePositionMs("42", isLive = false))
    }

    @Test
    fun firstSecondsAndAlmostFinishedEpisodesStartFromZero() {
        positions.record("42", isLive = false, positionMs = 600_000L, durationMs = 3_600_000L)
        positions.record("42", isLive = false, positionMs = 3_580_000L, durationMs = 3_600_000L)
        assertEquals(0L, positions.resumePositionMs("42", isLive = false))

        positions.record("7", isLive = false, positionMs = 4_000L, durationMs = 3_600_000L)
        assertEquals(0L, positions.resumePositionMs("7", isLive = false))
    }

    @Test
    fun liveStreamsAreNeverSaved() {
        positions.record("radio", isLive = true, positionMs = 600_000L, durationMs = 0L)
        assertTrue(store.longs.isEmpty())
        store.putLong(PlaybackPositions.key("radio"), 600_000L)
        assertEquals(0L, positions.resumePositionMs("radio", isLive = true))
    }

    @Test
    fun unknownDurationStillSaves() {
        positions.record("9", isLive = false, positionMs = 60_000L, durationMs = 0L)
        assertEquals(60_000L, positions.resumePositionMs("9", isLive = false))
    }

    @Test
    fun forgettingOneEpisodeKeepsTheOthers() {
        positions.record("1", isLive = false, positionMs = 60_000L, durationMs = 0L)
        positions.record("2", isLive = false, positionMs = 90_000L, durationMs = 0L)
        positions.forget("1")
        assertEquals(0L, positions.resumePositionMs("1", isLive = false))
        assertEquals(90_000L, positions.resumePositionMs("2", isLive = false))
    }

    @Test
    fun clearingTheHistoryForgetsEveryPositionButKeepsSpeeds() {
        positions.record("1", isLive = false, positionMs = 60_000L, durationMs = 0L)
        positions.record("2", isLive = false, positionMs = 90_000L, durationMs = 0L)
        PlaybackProfiles(store).setSpeed(feedId = 5L, tenths = 15)
        positions.forgetAll()
        assertEquals(0L, positions.resumePositionMs("1", isLive = false))
        assertEquals(0L, positions.resumePositionMs("2", isLive = false))
        assertEquals(15, PlaybackProfiles(store).speedFor(5L))
    }
}

class PlaybackProfilesTest {
    private val store = MapStore()
    private val profiles = PlaybackProfiles(store)

    @Test
    fun speedIsRememberedPerShowWithAGlobalDefault() {
        assertEquals(10, profiles.speedFor(1L))
        profiles.setSpeed(1L, 15)
        assertEquals(15, profiles.speedFor(1L))
        assertEquals(10, profiles.speedFor(2L))
    }

    @Test
    fun applyToAllMakesTheGlobalSpeedWinAndKeepsPerShowValues() {
        profiles.setSpeed(1L, 15)
        profiles.setApplyToAll(true, currentTenths = 12)
        assertEquals(12, profiles.speedFor(1L))
        assertEquals(12, profiles.speedFor(2L))
        profiles.setSpeed(2L, 20)
        assertEquals(20, profiles.speedFor(1L))

        profiles.setApplyToAll(false, currentTenths = 20)
        assertEquals(15, profiles.speedFor(1L))
        assertEquals(20, profiles.speedFor(3L))
    }

    @Test
    fun itemsWithoutAShowUseTheGlobalSpeed() {
        profiles.setSpeed(null, 8)
        assertEquals(8, profiles.speedFor(null))
        assertEquals(8, profiles.speedFor(5L))
    }

    @Test
    fun storedValuesAreClampedOnRead() {
        store.putLong(PlaybackProfiles.feedKey(1L), 99L)
        assertEquals(30, profiles.speedFor(1L))
    }
}
