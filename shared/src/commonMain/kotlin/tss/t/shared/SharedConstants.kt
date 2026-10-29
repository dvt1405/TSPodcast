package tss.t.shared

/**
 * Same values as Android's `tss.t.sharedresources.SharedConstants` (SharedConstantsTest
 * compares them with that file). Swift reads the User-Agent from here instead of copying it.
 */
object SharedConstants {
    /** Browser User-Agent sent with artwork requests: some image hosts refuse unknown clients. */
    const val USER_AGENT_WEB_VALUE =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    const val USER_AGENT_KEY = "User-Agent"
    const val REFERER_KEY = "Referer"
}
