package tss.t.shared.network

/**
 * PodcastIndex configuration, supplied by the platform: `NativeLib` (securedToken) on
 * Android, `Info.plist` keys filled from `Secrets.xcconfig` on iOS.
 */
interface CredentialsProvider {
    /** API root, e.g. the v1.0 base URL. A missing trailing `/` is tolerated. */
    val baseUrl: String
    val apiKey: String
    val apiSecret: String
    val userAgent: String
}

/**
 * Names of the credentials that are not usable: blank, or still the unexpanded
 * `$(PODCASTINDEX_…)` placeholder that Xcode leaves in `Info.plist` when
 * `Secrets.xcconfig` is missing.
 */
fun CredentialsProvider.missingCredentials(): List<String> = buildList {
    if (!baseUrl.isConfigured()) add("baseUrl")
    if (!apiKey.isConfigured()) add("apiKey")
    if (!apiSecret.isConfigured()) add("apiSecret")
    if (!userAgent.isConfigured()) add("userAgent")
}

private fun String.isConfigured(): Boolean = isNotBlank() && !contains("$(")

/**
 * PodcastIndex credentials are not configured. Returned inside `TSDataState.Error`
 * without any network call; never thrown out of the API.
 *
 * @property missing which of `baseUrl`, `apiKey`, `apiSecret`, `userAgent` are unusable.
 *   Names only, never values.
 */
class MissingCredentialsException(
    val missing: List<String>,
) : IllegalStateException("PodcastIndex credentials missing: ${missing.joinToString()}")

/**
 * The API answered with a non-2xx status. Same role as Android's
 * `TSExceptions(errorCode, description)`.
 */
class PodcastIndexHttpException(
    val code: Int,
    val body: String,
) : Exception("PodcastIndex HTTP $code")
