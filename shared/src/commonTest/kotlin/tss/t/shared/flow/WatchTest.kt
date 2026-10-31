package tss.t.shared.flow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The helper Swift observes shared flows with (M4 §4.2). */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchTest {

    @Test
    fun deliversEveryValueUntilClosed() = runTest {
        val source = MutableStateFlow(1)
        val seen = mutableListOf<Int>()

        val handle = watch(source, UnconfinedTestDispatcher(testScheduler)) { seen += it }
        source.value = 2
        source.value = 3
        handle.close()
        source.value = 4
        handle.close()

        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test
    fun anErrorEndsTheStreamWithoutThrowing() = runTest {
        val seen = mutableListOf<Int>()
        val failing = flow {
            emit(1)
            error("boom")
        }

        watch(failing, UnconfinedTestDispatcher(testScheduler)) { seen += it }

        assertEquals(listOf(1), seen)
    }
}
