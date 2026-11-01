package tss.t.podcast.analytics

import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast
import tss.t.coreradio.models.RadioChannel
import tss.t.podcasts.NewsItem
import tss.t.sharedlibrary.analytics.Analytics
import tss.t.sharedlibrary.analytics.AppEvents
import tss.t.sharedlibrary.analytics.AppEvents.ContentType

/**
 * GA4 `select_content` for the taps on Home, Search, Favourites and Radio, with the section
 * (see [AppEvents.Section]) and, for the location chart and news by location, the region.
 */
object ContentAnalytics {

    fun podcast(podcast: Podcast, section: String, region: String? = null, position: Int? = null) =
        Analytics.log(AppEvents.selectContent(ContentType.PODCAST, podcast.id, section, region, position))

    fun episode(episode: Episode, section: String) =
        Analytics.log(AppEvents.selectContent(ContentType.EPISODE, episode.id, section))

    fun live(live: LiveEpisode, section: String) =
        Analytics.log(AppEvents.selectContent(ContentType.LIVE, live.id, section))

    fun news(item: NewsItem, region: String?) =
        Analytics.log(
            AppEvents.selectContent(ContentType.NEWS, item.episode.id, AppEvents.Section.NEWS, region)
        )

    fun radio(channel: RadioChannel, section: String = AppEvents.Section.RADIO) =
        Analytics.log(AppEvents.selectContent(ContentType.RADIO, channel.channelId, section))

    fun share(contentType: String, itemId: Any?) = Analytics.log(AppEvents.share(contentType, itemId))

    /** Wraps a `Podcast.() -> Unit` click so it logs first. */
    fun onPodcast(section: String, click: Podcast.() -> Unit): Podcast.() -> Unit = {
        podcast(this, section)
        click(this)
    }
}
