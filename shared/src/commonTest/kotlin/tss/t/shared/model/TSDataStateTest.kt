package tss.t.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TSDataStateTest {

    @Test
    fun successHasDataAndNoException() {
        val state: TSDataState<String> = TSDataState.Success("ok")

        assertTrue(state.isSuccess())
        assertEquals("ok", state.data)
        assertNull(state.exceptionOrNull())
    }

    @Test
    fun loadingHasNoException() {
        val state: TSDataState<String> = TSDataState.Loading()

        assertFalse(state.isSuccess())
        assertNull(state.data)
        assertNull(state.exceptionOrNull())
    }

    @Test
    fun errorExposesItsException() {
        val cause = IllegalStateException("boom")
        val state: TSDataState<String> = TSDataState.Error(cause, data = "stale")

        assertFalse(state.isSuccess())
        assertEquals("stale", state.data)
        assertSame(cause, state.exceptionOrNull())
    }
}
