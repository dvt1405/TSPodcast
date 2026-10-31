package tss.t.podcasts

import tss.t.core.storage.ContentPrefs
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One place that decides whether a show or an episode may be listed: the remote block list
 * (feed ids, keywords, hosts and authors), the shows the user hid, and explicit content, which
 * is hidden unless the user allows it in Settings (iOS D6 parity).
 */
@Singleton
class ContentFilter @Inject constructor(
    private val blacklist: BlacklistRepositoryImpl,
    private val prefs: ContentPrefs,
) {
    private val allowExplicit: Boolean get() = prefs.allowExplicit.value

    /** `clean=true` for PodcastIndex search while explicit content is hidden, else the caller's value. */
    fun clean(requested: Boolean?): Boolean? = if (allowExplicit) requested else true

    fun allowsShow(
        feedId: Long?,
        title: String?,
        description: String?,
        explicit: Boolean,
        url: String? = null,
        author: String? = null,
    ): Boolean {
        if (feedId != null && (blacklist.isInBlacklist(feedId.toString()) || prefs.isHidden(feedId))) return false
        if (blacklist.isContainKeywordsBlacklist(title) || blacklist.isContainKeywordsBlacklist(description, false)) return false
        if (blacklist.isBlockedHost(url) || blacklist.isBlockedAuthor(author)) return false
        return allowExplicit || !explicit
    }

    fun allows(podcast: Podcast): Boolean = allowsShow(
        feedId = podcast.id,
        title = podcast.title,
        description = podcast.description,
        explicit = podcast.explicit == 1,
        url = podcast.link,
    )

    fun allows(feed: Feed): Boolean = allowsShow(
        feedId = feed.id,
        title = feed.title,
        description = feed.description,
        explicit = feed.explicit,
        url = feed.url,
        author = feed.author,
    )

    fun allows(live: LiveEpisode): Boolean = allowsShow(
        feedId = live.feedId,
        title = live.title,
        description = live.description,
        explicit = live.explicit == 1,
    )

    /** Episodes of a show already listed: only the explicit flag is checked. */
    fun allows(episode: Episode): Boolean = allowExplicit || episode.explicit != 1
}
