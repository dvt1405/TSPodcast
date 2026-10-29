package tss.t.shared.blacklist

import tss.t.shared.model.Feed
import tss.t.shared.model.LiveResponse
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TrendingPodcastRes

/*
 * Which fields each endpoint checks, taken from Android (the union of what
 * PodcastsRepositoryImpl and the use case on top of it filter, M0 §3.4):
 *
 * | Endpoint          | Blocked when                                               |
 * |-------------------|------------------------------------------------------------|
 * | E1 trending       | id or feedId listed, or a keyword in title or description  |
 * | E2 live           | feedId listed, or a keyword in title or description        |
 * | E5 recent/feeds   | id listed, or a keyword in title or description            |
 * | E6, E7, E8        | feed.id listed, or a keyword in title (not description)    |
 * | E9, E10, E11      | not filtered                                               |
 *
 * `count` always becomes the number of items left.
 */

/** E1 `podcasts/trending`. */
internal fun TrendingPodcastRes.filterTrending(blacklist: BlacklistRepository): TrendingPodcastRes {
    val kept = items.filterNot { podcast ->
        blacklist.isInBlacklist(podcast.id.toString()) ||
            podcast.feedId?.let { blacklist.isInBlacklist(it.toString()) } == true ||
            blacklist.isContainKeywordsBlacklist(podcast.title) ||
            blacklist.isContainKeywordsBlacklist(podcast.description)
    }
    return copy(count = kept.size, items = kept)
}

/** E5 `recent/feeds`. */
internal fun TrendingPodcastRes.filterRecentFeeds(blacklist: BlacklistRepository): TrendingPodcastRes {
    val kept = items.filterNot { podcast ->
        blacklist.isInBlacklist(podcast.id.toString()) ||
            blacklist.isContainKeywordsBlacklist(podcast.title) ||
            blacklist.isContainKeywordsBlacklist(podcast.description)
    }
    return copy(count = kept.size, items = kept)
}

/** E2 `episodes/live`. */
internal fun LiveResponse.filterLive(blacklist: BlacklistRepository): LiveResponse {
    val kept = items.filterNot { episode ->
        blacklist.isInBlacklist(episode.feedId.toString()) ||
            blacklist.isContainKeywordsBlacklist(episode.title) ||
            blacklist.isContainKeywordsBlacklist(episode.description)
    }
    return copy(count = kept.size, items = kept)
}

/** E7 `search/byterm` and E8 `search/music/byterm`. */
internal fun SearchResponse.filterFeeds(blacklist: BlacklistRepository): SearchResponse {
    val kept = feeds.filterNot { it.isBlocked(blacklist) }
    return copy(count = kept.size, feeds = kept)
}

/** E6 `podcasts/bymedium`, and every feed-shaped result (E7, E8). */
internal fun Feed.isBlocked(blacklist: BlacklistRepository): Boolean =
    blacklist.isInBlacklist(id.toString()) || blacklist.isContainKeywordsBlacklist(title)
