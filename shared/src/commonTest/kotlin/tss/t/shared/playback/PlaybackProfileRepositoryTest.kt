package tss.t.shared.playback

import kotlinx.coroutines.test.runTest
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M6-AC6: speed per show, global, "apply to all", surviving a restart. */
class PlaybackProfileRepositoryTest {
    private val store = InMemoryKeyValueStore()
    private val profiles = PlaybackProfileRepository(store)

    @Test
    fun defaultsToOneX() = runTest {
        assertEquals(10, profiles.speedFor(1))
        assertEquals(10, profiles.globalSpeed())
        assertFalse(profiles.isApplyToAll())
    }

    @Test
    fun speedIsPerShowAndSurvivesARestart() = runTest {
        profiles.setSpeed(1, 15)
        assertEquals(15, profiles.speedFor(1))
        assertEquals(10, profiles.speedFor(2))
        assertEquals(15L, store.getLong("playback.speed.feed.1"))

        val restarted = PlaybackProfileRepository(store)
        assertEquals(15, restarted.speedFor(1))
        assertEquals(10, restarted.speedFor(2))
    }

    @Test
    fun applyToAllUsesTheGlobalSpeedAndKeepsPerShowValues() = runTest {
        profiles.setSpeed(1, 15)
        profiles.setApplyToAll(true, 12)
        assertTrue(profiles.isApplyToAll())
        assertEquals(12, profiles.speedFor(1))
        assertEquals(12, profiles.speedFor(2))

        profiles.setSpeed(3, 20)
        assertEquals(20L, store.getLong("playback.speed.global"))
        assertEquals(null, store.getLong("playback.speed.feed.3"), "not written per show while applying to all")
        assertEquals(20, profiles.speedFor(1))

        profiles.setApplyToAll(false, 20)
        assertFalse(profiles.isApplyToAll())
        assertEquals(15, profiles.speedFor(1), "per-show value kept")
        assertEquals(20, profiles.speedFor(2), "no per-show value: the global one")
        assertTrue(PlaybackProfileRepository(store).speedFor(1) == 15)
    }

    @Test
    fun valuesAreClamped() = runTest {
        profiles.setSpeed(1, 99)
        assertEquals(30, profiles.speedFor(1))
        store.putLong("playback.speed.feed.2", 1)
        assertEquals(5, profiles.speedFor(2))
        profiles.setApplyToAll(true, 0)
        assertEquals(5, profiles.globalSpeed())
    }
}
