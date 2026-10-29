package tss.t.shared

import tss.t.shared.fixtures.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The constants must match `sharedResources/.../SharedConstants.kt` character for character. */
class SharedConstantsTest {

    private fun androidValue(name: String): String {
        val match = Regex("""$name\s*=\s*"([^"]*)"""").find(Fixtures.sharedConstants)
        return assertNotNull(match, "$name not found in SharedConstants.kt").groupValues[1]
    }

    @Test
    fun userAgentMatchesAndroid() {
        assertEquals(androidValue("USER_AGENT_WEB_VALUE"), SharedConstants.USER_AGENT_WEB_VALUE)
        assertEquals(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36",
            SharedConstants.USER_AGENT_WEB_VALUE,
        )
    }

    @Test
    fun headerNamesMatchAndroid() {
        assertEquals(androidValue("USER_AGENT_KEY"), SharedConstants.USER_AGENT_KEY)
        assertEquals(androidValue("REFERER_KEY"), SharedConstants.REFERER_KEY)
    }
}
