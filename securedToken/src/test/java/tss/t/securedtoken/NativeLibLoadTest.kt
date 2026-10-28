package tss.t.securedtoken

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The host JVM has no libsecuredtoken.so, which is the same situation as an
 * install missing its ABI split. Touching [NativeLib] must not throw from the
 * class initializer (Crashlytics 23322637b369c9bf5c2c9c89e0572eaa).
 */
class NativeLibLoadTest {
    @Test
    fun missingLibrary_isReportedInsteadOfThrown() {
        assertFalse(NativeLib.isAvailable)
        assertNotNull(NativeLib.loadError)
        // Non-native members stay usable.
        assertNotNull(NativeLib.sha1Hash("tss"))
    }
}
