package tss.t.shared.newshub

import kotlinx.serialization.json.Json
import tss.t.shared.config.ConfigAPI
import tss.t.shared.config.ConfigKeys
import tss.t.shared.generated.BundledAssets
import tss.t.shared.playback.SleepTimer
import kotlin.concurrent.Volatile

/**
 * The news hub configuration in effect (M6 §3.4).
 *
 * Source: Remote Config [ConfigKeys.KEY_NEWS_HUB] when it decodes to a config of a supported
 * version, otherwise the bundled `shared/assets/news_hub_default.json`. A broken remote value
 * therefore never takes the hub away. Like the blacklist, the remote value **replaces** the
 * bundled one; nothing is merged.
 *
 * Then, in order:
 * - sources without a key, without an `http(s)` feed URL, or with a key already seen are dropped;
 * - sources whose `confirmedAt` is not a date are dropped, unless [includeUnconfirmed] (a Debug
 *   launch argument only, never true in Release);
 * - `take` below 1 becomes 1, a non-positive `maxAgeHours` becomes 24, the night sleep timer is
 *   kept within 1–180 minutes.
 *
 * [isEnabled] is false when the config says `enabled: false` **or** no news source is left: the
 * hub is then absent (no Home section, no route), never an empty screen.
 */
class NewsHubConfigRepository(
    config: ConfigAPI,
    private val json: Json = NewsHubConfig.ConfigJson,
    private val includeUnconfirmed: Boolean = false,
) {
    private val remoteConfig: ConfigAPI = config

    @Volatile
    private var current: Loaded = load()

    /** The filtered configuration; [NewsHubConfig.Disabled] when the hub is off. */
    val config: NewsHubConfig get() = current.config

    val isEnabled: Boolean get() = current.config.enabled

    /** Whether [config] came from Remote Config rather than the bundled file. */
    val usesRemoteValue: Boolean get() = current.isRemote

    /** Reads Remote Config again, after it activated new values (with `blacklistRepository.reload()`). */
    fun reload() {
        current = load()
    }

    private fun load(): Loaded {
        val remote = NewsHubConfig.parse(remoteConfig.getString(ConfigKeys.KEY_NEWS_HUB), json)
            ?.takeIf { it.version in 1..SUPPORTED_VERSION }
        val raw = remote
            ?: NewsHubConfig.parse(BundledAssets.newsHubDefault, json)
            ?: NewsHubConfig.Disabled
        return Loaded(effective(raw), isRemote = remote != null)
    }

    private fun effective(raw: NewsHubConfig): NewsHubConfig {
        if (!raw.enabled) return NewsHubConfig.Disabled
        val seen = mutableSetOf<String>()
        val sections = listOf(NewsHubConfig.SECTION_NEWS, NewsHubConfig.SECTION_NIGHT).mapNotNull { id ->
            val section = raw.section(id) ?: return@mapNotNull null
            val sources = section.sources
                .filter { it.key.isNotBlank() && it.feedUrl.isHttpUrl() && seen.add(it.key) }
                .filter { includeUnconfirmed || it.isConfirmed }
                .map { it.copy(brief = it.brief?.normalized()) }
            NewsSection(
                id = id,
                sleepTimerMinutes = section.sleepTimerMinutes?.coerceIn(SleepTimer.MIN_MINUTES, SleepTimer.MAX_MINUTES),
                sources = sources,
            ).takeIf { sources.isNotEmpty() }
        }
        val result = NewsHubConfig(version = raw.version, enabled = true, sections = sections)
        return if (result.newsSources.isEmpty()) NewsHubConfig.Disabled else result
    }

    private fun NewsBriefRule.normalized(): NewsBriefRule = copy(
        take = take.coerceAtLeast(1),
        maxAgeHours = maxAgeHours.takeIf { it > 0 } ?: NewsBriefRule.DEFAULT_MAX_AGE_HOURS,
        preferTitle = preferTitle?.takeIf { it.isNotBlank() },
        preferTitleBeforeHour = preferTitleBeforeHour?.coerceIn(0, 24),
    )

    private fun String.isHttpUrl(): Boolean {
        val text = trim().lowercase()
        return (text.startsWith("https://") || text.startsWith("http://")) && text.length > "https://".length
    }

    private class Loaded(val config: NewsHubConfig, val isRemote: Boolean)

    companion object {
        /** The schema this build understands; a remote value of a later version falls back. */
        const val SUPPORTED_VERSION = 1
    }
}
