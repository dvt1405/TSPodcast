package tss.t.coreapi

import androidx.annotation.IntRange
import retrofit2.http.GET
import retrofit2.http.Query
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveResponse
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.SearchByPersonRes
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.StatCurrent
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes

interface API {
    /**
     * This call returns all of the feeds that match the search terms in the title, author or owner of the feed.
     * @param query Terms to search for
     * ```
     * Examples: batman university
     * ```
     * @param type Only returns feeds with a value block of the specified type. Use any to return feeds with any value block.
     * Allowed: any ┃ lightning ┃ hive ┃ webmonetization
     *```
     * Examples: ∅
     * ```
     * @param max Maximum number of results to return. [Min 1┃Max 1000](max://)
     * ```
     * Examples: 10
     * ```
     * @param aponly Only returns feeds with an [itunesId](itunesId://).
     * @param clean If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
     * @param similar If present, include similar matches in search response. For search/byterm, prioritizes title matches.
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * Parameter shall not have a value

     * @see <a href="https://api.podcastindex.org/api/1.0/search/byterm?q=batman+university&pretty">Examples</a>
     * */
    @GET("search/byterm")
    fun searchPodcasts(
        @Query("q") query: String,
        @Query("max") max: Int = 100, //min 1 max 100
        @Query("val") type: String? = null,
        @Query("aponly") aponly: Boolean = false, //Only returns feeds with an itunesId.
        @Query("clean") clean: Boolean? = null, //If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
        @Query("similar") similar: Boolean? = true,
        @Query("fulltext") fulltext: Boolean? = true,
        @Query("pretty") pretty: Boolean = false
    ): TSDataState<SearchResponse>

    /**
     * This call returns all of the feeds where the title of the feed matches the search term (ignores case).
     * Example "everything everywhere daily" will match the podcast [Everything Everywhere Daily](https://podcastindex.org/podcast/437685) by "everything everywhere" will not.
     *
     * @param query Terms to search for
     * ```
     * Examples: batman university
     * ```
     * @param type (val) Allowed: any ┃ lightning ┃ hive ┃ webmonetization
     * Only returns feeds with a value block of the specified type. Use any to return feeds with any value block.
     * ```
     * Examples: ∅
     * ```
     * @param max [Min 1┃Max 1000](max://) Maximum number of results to return.
     * ```
     * Examples: 10
     * ```
     * @param clean If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
     * Parameter shall not have a value
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * Parameter shall not have a value
     * @param similar If present, include similar matches in search response. For search/byterm, prioritizes title matches.
     *
     * @return [SearchResponse]
     * ```
     * {
     * status: enum
     * Indicates API request status
     *
     * Allowed: true┃false
     * feeds: [{
     * List of feeds matching request
     *
     * id: integer
     * The internal PodcastIndex.org Feed ID.
     *
     * podcastGuid: string
     * The GUID from the podcast:guid tag in the feed. This value is a unique, global identifier for the podcast.
     *
     * See the namespace spec for guid for details.
     *
     * title: string
     * Name of the feed
     *
     * url: URL
     * Current feed URL
     *
     * originalUrl: URL
     * The URL of the feed, before it changed to the current url value.
     *
     * link: URL
     * The channel-level link in the feed
     *
     * description: string
     * The channel-level description
     *
     * Uses the longer of the possible fields in the feed: <description>, <itunes:summary> and <content:encoded>
     *
     * author: string
     * The channel-level author element.
     *
     * Usually iTunes specific, but could be from another namespace if not present.
     *
     * ownerName: string
     * The channel-level owner:name element.
     *
     * Usually iTunes specific, but could be from another namespace if not present.
     *
     * image: URL
     * The channel-level image element.
     *
     * artwork: URL
     * The seemingly best artwork we can find for the feed.
     *
     * Might be the same as image in most instances.
     *
     * lastUpdateTime: integer
     * The channel-level pubDate for the feed, if it’s sane.
     *
     * If not, this is a heuristic value, arrived at by analyzing other parts of the feed, like item-level pubDates.
     *
     * lastCrawlTime: integer
     * The last time we attempted to pull this feed from its url.
     *
     * lastParseTime: integer
     * The last time we tried to parse the downloaded feed content.
     *
     * lastGoodHttpStatusTime: integer
     * Timestamp of the last time we got a "good", meaning non-4xx/non-5xx, status code when pulling this feed from its url.
     *
     * lastHttpStatus: integer
     * The last http status code we got when pulling this feed from its url.
     *
     * You will see some made up status codes sometimes. These are what we use to track state within the feed puller. These all start with 9xx.
     *
     * contentType: string
     * The Content-Type header from the last time we pulled this feed from its url.
     *
     * itunesId: integer┃null
     * The iTunes ID of this feed if there is one, and we know what it is.
     *
     * generator: string
     * The channel-level generator element if there is one.
     *
     * language: string
     * The channel-level language specification of the feed.
     *
     * Languages accord with the RSS Language Spec.
     *
     * explicit: boolean
     * Is feed marked as explicit
     *
     * type: integer
     * Type of source feed where:
     *
     * 0: RSS
     * 1: Atom
     * Allowed: 0┃1
     * medium: string
     * The value of the podcast:medium attribute for the feed.
     *
     * See the medium description in the Podcast Namespace for more information.
     *
     * dead: integer
     * At some point, we give up trying to process a feed and mark it as dead. This is usually after 1000 errors without a successful pull/parse cycle. Once the feed is marked dead, we only check it once per month.
     *
     * episodeCount: integer
     * Number of episodes for this feed known to the index.
     *
     * crawlErrors: integer
     * The number of errors we’ve encountered trying to pull a copy of the feed. Errors are things like a 500 or 404 response, a server timeout, bad encoding, etc.
     *
     * parseErrors: integer
     * The number of errors we’ve encountered trying to parse the feed content. Errors here are things like not well-formed xml, bad character encoding, etc.
     *
     * We fix many of these types of issues on the fly when parsing. We only increment the errors count when we can’t fix it.
     *
     * categories: {...}
     * An array of categories, where the index is the Category ID and the value is the Category Name.
     *
     * All Category numbers and names are returned by the categories/list endpoint.
     *
     * locked: integer
     * Tell other podcast platforms whether they are allowed to import this feed. A value of 1 means that any attempt to import this feed into a new platform should be rejected.
     *
     * Contains the value of the feed's channel-level podcast:locked tag where:
     *
     * 0: 'no'
     * 1: 'yes'
     * Allowed: 0┃1
     * imageUrlHash: integer
     * A CRC32 hash of the image URL with the protocol (http://, https://) removed.
     *
     * newestItemPubdate: integer
     * The time the most recent episode in the feed was published.
     *
     * Note: some endpoints use newestItemPubdate while others use newestItemPublishTime. They return the same information. See https://github.com/Podcastindex-org/api/issues/3 to track when the property name is updated.
     *
     * }]
     * count: integer
     * Number of items returned in request
     *
     * query: string
     * Search terms passed to request
     *
     * description: string
     * Description of the response
     *
     * }
     * ```
     * */
    @GET("search/bytitle")
    fun searchPodcastsByTitle(
        @Query("q") query: String,
        @Query("val") type: String,
        @Query("max") max: Int, //min 1 max 100
        @Query("clean") clean: Boolean? = null, //If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
        @Query("similar") similar: Boolean? = true,
        @Query("fulltext") fulltext: Boolean? = true,
        @Query("pretty") pretty: Boolean = false
    ): TSDataState<SearchResponse>


    /**
     * This call returns all of the episodes where the specified person is mentioned.
     *
     * It searches the following fields:
     *
     * Person tags
     * Episode title
     * Episode description
     * Feed owner
     * Feed author
     * ```
     * Examples:
     *
     * https://api.podcastindex.org/api/1.0/search/byperson?q=adam%20curry&pretty
     * https://api.podcastindex.org/api/1.0/search/byperson?q=Martin+Mouritzen&pretty
     * https://api.podcastindex.org/api/1.0/search/byperson?q=Klaus+Schwab&pretty
     * ```
     * @param query Terms to search for
     * ```
     * Examples: batman university
     * ```
     * @param type (val) Allowed: any ┃ lightning ┃ hive ┃ webmonetization
     * Only returns feeds with a value block of the specified type. Use any to return feeds with any value block.
     * ```
     * Examples: ∅
     * ```
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * Parameter shall not have a value
     * @return [SearchByPersonRes]
     * ```
     * SCHEMA
     * {
     * status: enum
     * Indicates API request status
     *
     * Allowed: true┃false
     * items: [{
     * List of episodes matching request
     *
     * id: integer
     * The internal PodcastIndex.org episode ID.
     *
     * title: string
     * Name of the episode
     *
     * link: URL
     * The channel-level link in the feed
     *
     * description: string
     * The item-level description of the episode.
     *
     * Uses the longer of the possible fields in the feed: <description>, <itunes:summary> and <content:encoded>
     *
     * guid: string
     * The unique identifier for the episode
     *
     * datePublished: integer
     * The date and time the episode was published
     *
     * dateCrawled: integer
     * The time this episode was found in the feed
     *
     * enclosureUrl: URL
     * URL/link to the episode file
     *
     * enclosureType: string
     * The Content-Type for the item specified by the enclosureUrl
     *
     * enclosureLength: integer
     * The length of the item specified by the enclosureUrl in bytes
     *
     * duration: integer┃null
     * The estimated length of the item specified by the enclosureUrl in seconds. Will be null for liveItem.
     *
     * explicit: integer
     * Is feed or episode marked as explicit
     *
     * 0: not marked explicit
     * 1: marked explicit
     * Allowed: 0┃1
     * episode: integer┃null
     * Episode number
     *
     * episodeType: enum┃null
     * The type of episode. May be null for liveItem.
     *
     * Allowed: full┃trailer┃bonus
     * season: integer┃null
     * Season number. May be null for liveItem.
     *
     * image: URL
     * The item-level image for the episode
     *
     * feedItunesId: integer┃null
     * The iTunes ID of this feed if there is one, and we know what it is.
     *
     * feedImage: URL
     * The channel-level image element.
     *
     * feedId: integer
     * The internal PodcastIndex.org Feed ID.
     *
     * feedUrl: URL
     * Current feed URL
     *
     * feedAuthor: string
     * The channel-level author element.
     *
     * Usually iTunes specific, but could be from another namespace if not present.
     *
     * feedTitle: string
     * Name of the feed
     *
     * feedLanguage: string
     * The channel-level language specification of the feed.
     *
     * Languages accord with the RSS Language Spec.
     *
     * chaptersUrl: URL┃null
     * Link to the JSON file containing the episode chapters
     *
     * transcriptUrl: URL┃null
     * Link to the file containing the episode transcript
     *
     * Note: in most use cases, the transcripts value should be used instead
     *
     * transcripts: [{...}]
     * List of transcripts for the episode. May not be reported.
     * ⮕ [ This tag is used to link to a transcript or closed captions file. Multiple tags can be present for multiple transcript formats.
     *
     * Detailed file format information and example files are here. ]
     *
     * }]
     * count: integer
     * Number of items returned in request
     *
     * query: string
     * Search terms passed to request
     *
     * description: string
     * Description of the response
     *
     * }
     * ```
     * **/
    @GET("search/byperson")
    fun searchPodcastsByPerson(
        @Query("q") query: String,
        @Query("max") max: Int, //min 1 max 100
        @Query("fulltext") fulltext: Boolean? = true,
        @Query("pretty") pretty: Boolean = false
    ): TSDataState<SearchByPersonRes>


    /**
     * This call returns all of the feeds that match the search terms in the title, author or owner of the where the medium is music.
     * ```
     * Example: https://api.podcastindex.org/api/1.0/search/music/byterm?q=able+kirby&pretty
     * ```
     * @param query Terms to search for
     * ```
     * Examples: batman university
     * ```
     * @param type (val) Allowed: any ┃ lightning ┃ hive ┃ webmonetization
     * Only returns feeds with a value block of the specified type. Use any to return feeds with any value block.
     * ```
     * Examples: ∅
     * ```
     * @param max [Min 1┃Max 1000](max://) Maximum number of results to return.
     * ```
     * Examples: 10
     * ```
     * @param clean If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
     * Parameter shall not have a value
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * Parameter shall not have a value
     * @param similar If present, include similar matches in search response. For search/byterm, prioritizes title matches.
     * @return [SearchResponse]
     * ```
     * SCHEME
     * {
     * status: enum
     * Indicates API request status
     *
     * Allowed: true┃false
     * feeds: [{
     * List of feeds matching request
     *
     * id: integer
     * The internal PodcastIndex.org Feed ID.
     *
     * podcastGuid: string
     * The GUID from the podcast:guid tag in the feed. This value is a unique, global identifier for the podcast.
     *
     * See the namespace spec for guid for details.
     *
     * title: string
     * Name of the feed
     *
     * url: URL
     * Current feed URL
     *
     * originalUrl: URL
     * The URL of the feed, before it changed to the current url value.
     *
     * link: URL
     * The channel-level link in the feed
     *
     * description: string
     * The channel-level description
     *
     * Uses the longer of the possible fields in the feed: <description>, <itunes:summary> and <content:encoded>
     *
     * author: string
     * The channel-level author element.
     *
     * Usually iTunes specific, but could be from another namespace if not present.
     *
     * ownerName: string
     * The channel-level owner:name element.
     *
     * Usually iTunes specific, but could be from another namespace if not present.
     *
     * image: URL
     * The channel-level image element.
     *
     * artwork: URL
     * The seemingly best artwork we can find for the feed.
     *
     * Might be the same as image in most instances.
     *
     * lastUpdateTime: integer
     * The channel-level pubDate for the feed, if it’s sane.
     *
     * If not, this is a heuristic value, arrived at by analyzing other parts of the feed, like item-level pubDates.
     *
     * lastCrawlTime: integer
     * The last time we attempted to pull this feed from its url.
     *
     * lastParseTime: integer
     * The last time we tried to parse the downloaded feed content.
     *
     * lastGoodHttpStatusTime: integer
     * Timestamp of the last time we got a "good", meaning non-4xx/non-5xx, status code when pulling this feed from its url.
     *
     * lastHttpStatus: integer
     * The last http status code we got when pulling this feed from its url.
     *
     * You will see some made up status codes sometimes. These are what we use to track state within the feed puller. These all start with 9xx.
     *
     * contentType: string
     * The Content-Type header from the last time we pulled this feed from its url.
     *
     * itunesId: integer┃null
     * The iTunes ID of this feed if there is one, and we know what it is.
     *
     * generator: string
     * The channel-level generator element if there is one.
     *
     * language: string
     * The channel-level language specification of the feed.
     *
     * Languages accord with the RSS Language Spec.
     *
     * explicit: boolean
     * Is feed marked as explicit
     *
     * type: integer
     * Type of source feed where:
     *
     * 0: RSS
     * 1: Atom
     * Allowed: 0┃1
     * medium: string
     * The value of the podcast:medium attribute for the feed.
     *
     * See the medium description in the Podcast Namespace for more information.
     *
     * dead: integer
     * At some point, we give up trying to process a feed and mark it as dead. This is usually after 1000 errors without a successful pull/parse cycle. Once the feed is marked dead, we only check it once per month.
     *
     * episodeCount: integer
     * Number of episodes for this feed known to the index.
     *
     * crawlErrors: integer
     * The number of errors we’ve encountered trying to pull a copy of the feed. Errors are things like a 500 or 404 response, a server timeout, bad encoding, etc.
     *
     * parseErrors: integer
     * The number of errors we’ve encountered trying to parse the feed content. Errors here are things like not well-formed xml, bad character encoding, etc.
     *
     * We fix many of these types of issues on the fly when parsing. We only increment the errors count when we can’t fix it.
     *
     * categories: {...}
     * An array of categories, where the index is the Category ID and the value is the Category Name.
     *
     * All Category numbers and names are returned by the categories/list endpoint.
     *
     * locked: integer
     * Tell other podcast platforms whether they are allowed to import this feed. A value of 1 means that any attempt to import this feed into a new platform should be rejected.
     *
     * Contains the value of the feed's channel-level podcast:locked tag where:
     *
     * 0: 'no'
     * 1: 'yes'
     * Allowed: 0┃1
     * imageUrlHash: integer
     * A CRC32 hash of the image URL with the protocol (http://, https://) removed.
     *
     * newestItemPubdate: integer
     * The time the most recent episode in the feed was published.
     *
     * Note: some endpoints use newestItemPubdate while others use newestItemPublishTime. They return the same information. See https://github.com/Podcastindex-org/api/issues/3 to track when the property name is updated.
     *
     * }]
     * count: integer
     * Number of items returned in request
     *
     * query: string
     * Search terms passed to request
     *
     * description: string
     * Description of the response
     *
     * }
     * ```
     * **/
    @GET("search/music/byterm")
    fun searchMusicPodcasts(
        @Query("q") query: String,
        @Query("val") type: String,
        @Query("max") max: Int, //min 1 max 100
        @Query("aponly") aponly: Boolean = false, //Only returns feeds with an itunesId.
        @Query("clean") clean: Boolean? = null, //If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
        @Query("similar") similar: Boolean? = true,
        @Query("fulltext") fulltext: Boolean? = true,
        @Query("pretty") pretty: Boolean = false
    ): TSDataState<SearchResponse>

    /**
     * Return all the possible categories supported by the index.
     * ```
     * Example: https://api.podcastindex.org/api/1.0/categories/list?pretty
     * ```
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * Parameter shall not have a value
     *
     * Authentication
     * Required (None Applied)
     * @return [CategoryRes]
     * */
    @GET("categories/list")
    fun getCategory(@Query("pretty") pretty: Boolean?): TSDataState<CategoryRes>

    @GET("stats/current")
    fun getCurrent(@Query("pretty") pretty: Boolean?): TSDataState<StatCurrent>

    @GET("podcasts/byfeedid")
    fun getPodcastByFeedId(
        @Query("id") id: String,
        @Query("pretty") pretty: Boolean?
    ): TSDataState<PodcastByFeedIdRes>

    /**
     * @param max Maximum number of results to return. Min 1┃Max 1000
     * @param since Return items since the specified epoch timestamp. Ex: 1612125785
     * @param lang Specifying a language code (like "en") will return only episodes having that specific language.
     * You can specify multiple languages by separating them with commas.
     * If you also want to return episodes that have no language given, use the token "unknown". (ex. en,es,ja,unknown).
     * Values are not case sensitive.
     * ```
     * Examples:
     * en
     * Single ID
     * en,es
     * Multiple IDs.
     * ```
     *@param cat Use this argument to specify that you ONLY want episodes with these categories in the results.
     *
     * Separate multiple categories with commas.
     *
     * You may specify either the Category ID and/or the Category Name.
     *
     * Values are not case sensitive.
     *
     * The cat and notcat filters can be used together to fine tune a very specific result set.
     *
     * Category numbers and names can be found in the [Podcast Namespace documentation](https://github.com/Podcastindex-org/podcast-namespace/blob/main/categories.json)
     *```
     * Examples:
     * News
     ** -Single Category Name
     *
     ** -65
     * Single Category ID
     *
     * News,Religion
     * Multiple Category Names
     *
     * 55,65
     * Multiple Category IDs
     *
     * News,65
     * Multiple Categories Mixed Format.
     * ```
     * @param notcat Use this argument to specify categories of episodes to NOT show in the results.
     *
     * Separate multiple categories with commas.
     *
     * You may specify either the Category ID and/or the Category Name.
     *
     * Values are not case sensitive.
     *
     * The cat and notcat filters can be used together to fine tune a very specific result set.
     *
     * Category numbers and names can be found in the [Podcast Namespace documentation](https://github.com/Podcastindex-org/podcast-namespace/blob/main/categories.json)
     *```
     * Examples:
     * News
     * Single Category Name
     *
     * 65
     * Single Category ID
     *
     * News,Religion
     * Multiple Category Names
     *
     * 55,65
     * Multiple Category IDs
     *
     * News,65
     * Multiple Categories Mixed Format.
     *```
     * @param pretty If present, makes the output “pretty” to help with debugging. Parameter shall not have a value.
     * @return [TrendingPodcastRes]
     * ```
     * Scheme:
     * {
     * status: enum
     * Indicates API request status
     *
     * Allowed: true┃false
     * feeds: [{
     * List of feeds matching request
     *
     * id: integer
     * The internal PodcastIndex.org Feed ID.
     *
     * url: URL
     * Current feed URL
     *
     * title: string
     * Name of the feed
     *
     * description: string
     * The channel-level description
     *
     * Uses the longer of the possible fields in the feed: <description>, <itunes:summary> and <content:encoded>
     *
     * author: string
     * The channel-level author element.
     *
     * Usually iTunes specific, but could be from another namespace if not present.
     *
     * image: URL
     * The channel-level image element.
     *
     * artwork: URL
     * The seemingly best artwork we can find for the feed.
     *
     * Might be the same as image in most instances.
     *
     * newestItemPublishTime: integer
     * The time the most recent episode in the feed was published.
     *
     * Note: some endpoints use newestItemPubdate while others use newestItemPublishTime. They return the same information. See https://github.com/Podcastindex-org/api/issues/3 to track when the property name is updated.
     *
     * itunesId: integer┃null
     * The iTunes ID of this feed if there is one, and we know what it is.
     *
     * trendScore: integer
     * The ranking for how the podcast is trending in the index
     *
     * language: string
     * The channel-level language specification of the feed.
     *
     * Languages accord with the RSS Language Spec.
     *
     * categories: {
     * An array of categories, where the index is the Category ID and the value is the Category Name.
     *
     * All Category numbers and names are returned by the categories/list endpoint.
     *
     * }}]
     * count: integer
     * Number of items returned in request
     *
     * max: integer┃null
     * Value of max parameter passed to request.
     *
     * since: integer┃null
     * Value of since parameter passed to request.
     *
     * description: string
     * Description of the response
     *
     * }
     *
     * ```
     * @see <a href="https://github.com/Podcastindex-org/podcast-namespace/blob/main/categories.json">Categories</a>
     * */
    @GET("podcasts/trending")
    fun getTrending(
        @Query("max") max: Int = 100,
        @Query("since") since: Int = (System.currentTimeMillis() - Constants.A_DAY).toInt(),
        @Query("lang") lang: String = "vi,en",
        @Query("cat") cat: String? = null,
        @Query("notcat") notcat: String? = null,
        @Query("pretty") pretty: Boolean? = null
    ): TSDataState<TrendingPodcastRes>

    /**
     * Get all episodes that have been found in the [podcast:liveitem](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/1.0.md#live-item) from the feeds.
     * ```
     * Examples: https://api.podcastindex.org/api/1.0/episodes/live?pretty
     *```
     * @param max Maximum number of results to return. Min 1┃Max 1000
     * @param pretty If present, makes the output “pretty” to help with debugging.
     *
     * Parameter shall not have a value
     * @return [LiveResponse]
     * ```
     *Scheme
     * Single line description
     * {
     * status: enum
     * Indicates API request status
     *
     * Allowed: true┃false
     * items: [{
     * List of episodes matching request
     *
     * id: integer
     * The internal PodcastIndex.org episode ID.
     *
     * title: string
     * Name of the feed
     *
     * link: URL
     * The channel-level link in the feed
     *
     * guid: string
     * The unique identifier for the episode
     *
     * datePublished: integer
     * The date and time the episode was published
     *
     * datePublishedPretty: string
     * The date and time the episode was published formatted as a human readable string.
     *
     * Note: uses the PodcastIndex server local time to do conversion.
     *
     * dateCrawled: integer
     * The time this episode was found in the feed
     *
     * enclosureUrl: URL
     * URL/link to the episode file
     *
     * enclosureType: string
     * The Content-Type for the item specified by the enclosureUrl
     *
     * enclosureLength: integer
     * The length of the item specified by the enclosureUrl in bytes
     *
     * explicit: integer
     * Is feed or episode marked as explicit
     *
     * 0: not marked explicit
     * 1: marked explicit
     * Allowed: 0┃1
     * image: URL
     * The item-level image for the episode
     *
     * feedItunesId: integer┃null
     * The iTunes ID of this feed if there is one, and we know what it is.
     *
     * feedImage: URL
     * The channel-level image element.
     *
     * feedId: integer
     * The internal PodcastIndex.org Feed ID.
     *
     * feedTitle: string
     * Name of the feed
     *
     * feedLanguage: string
     * The channel-level language specification of the feed.
     *
     * Languages accord with the RSS Language Spec.
     *
     * categories: {...}
     * An array of categories, where the index is the Category ID and the value is the Category Name.
     *
     * All Category numbers and names are returned by the categories/list endpoint.
     *
     * }]
     * count: integer
     * Number of items returned in request
     *
     * max: integer┃null
     * Value of max parameter passed to request.
     *
     * description: string
     * Description of the response
     *
     * }
     * ```
     * @see [LiveResponse]
     *
     * */
    @GET("episodes/live")
    fun getLiveEpisodes(
        @Query("max") max: Int,
        @Query("pretty") pretty: Boolean? = null
    ): TSDataState<LiveResponse>

    /**
     * This call returns a random batch of episodes, in no specific order.
     * @param max Maximum number of results to return. [Min 1┃Max 1000](max://)
     * ```
     * Examples: 10
     * ```
     * @param lang Specifying a language code (like "en") will return only episodes having that specific language.
     * You can specify multiple languages by separating them with commas.
     * If you also want to return episodes that have no language given, use the token "unknown". (ex. en,es,ja,unknown).
     * Values are not case sensitive.
     * ```
     * Examples:
     * en
     * Single ID
     * en,es
     * Multiple IDs.
     * ```
     *@param cat Use this argument to specify that you ONLY want episodes with these categories in the results.
     *
     * Separate multiple categories with commas.
     *
     * You may specify either the Category ID and/or the Category Name.
     *
     * Values are not case sensitive.
     *
     * The cat and notcat filters can be used together to fine tune a very specific result set.
     *
     * Category numbers and names can be found in the [Podcast Namespace documentation](https://github.com/Podcastindex-org/podcast-namespace/blob/main/categories.json)
     *```
     * Examples:
     * News
     ** -Single Category Name
     *
     ** -65
     * Single Category ID
     *
     * News,Religion
     * Multiple Category Names
     *
     * 55,65
     * Multiple Category IDs
     *
     * News,65
     * Multiple Categories Mixed Format.
     * ```
     * @param notcat Use this argument to specify categories of episodes to NOT show in the results.
     *
     * Separate multiple categories with commas.
     *
     * You may specify either the Category ID and/or the Category Name.
     *
     * Values are not case sensitive.
     *
     * The cat and notcat filters can be used together to fine tune a very specific result set.
     *
     * Category numbers and names can be found in the [Podcast Namespace documentation](https://github.com/Podcastindex-org/podcast-namespace/blob/main/categories.json)
     *```
     * Examples:
     * News
     * Single Category Name
     *
     * 65
     * Single Category ID
     *
     * News,Religion
     * Multiple Category Names
     *
     * 55,65
     * Multiple Category IDs
     *
     * News,65
     * Multiple Categories Mixed Format.
     *```
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * Parameter shall not have a value

     * @see <a href="https://api.podcastindex.org/api/1.0/search/byterm?q=batman+university&pretty">Examples</a>
     * */
    @GET("episodes/random")
    fun getRandomEpisodes(
        @IntRange(from = 1, to = 1000)
        @Query("max") max: Int = 100,
        @Query("lang") lang: String = "vi,en",
        @Query("cat") cat: String? = null,
        @Query("notcat") notcat: String? = null,
        @Query("pretty") pretty: Boolean? = null
    ): TSDataState<EpisodeResponse>

    /**
     * This call returns all the episodes we know about for this feed from the PodcastIndex ID. Episodes are in reverse chronological order.
     * When using the enclosure parameter, only the episode matching the URL is returned.
     * ```
     * Examples:
     *
     * https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=75075&pretty
     * https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=41504,920666&pretty
     * Includes persons: https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=169991&pretty
     * Includes value: https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=4058673&pretty
     * Using enclosure: https://api.podcastindex.org/api/1.0/episodes/byfeedid?id=41504&enclosure=https://op3.dev/e/mp3s.nashownotes.com/NA-1551-2023-04-30-Final.mp3&pretty
     * ```
     * @param id The PodcastIndex Feed ID or IDs to search for.
     * ```
     * Examples:
     * 75075
     * Single ID
     *
     * 41504,920666
     * Multiple IDs
     * ```
     * @param since Return items since the specified epoch timestamp.
     * ```
     * Examples: 1612125785
     * ```
     * @param max Maximum number of results to return. Min 1┃Max 1000
     * ```
     * Examples: 10
     * ```
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * @param pretty If present, makes the output “pretty” to help with debugging.
     *
     * */
    @GET("episodes/byfeedid")
    fun getEpisodeByFeedId(
        @Query("id") id: String,
        @Query("max")
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        @Query("since")
        since: Long? = null,
        @Query("enclosure") enclosure: String? = null,
        @Query("fulltext") fulltext: String? = null,
        @Query("pretty") pretty: Boolean? = null
    ): TSDataState<EpisodeResponse>

    /**
     * This call returns the most recent max number of episodes globally across the whole index, in reverse chronological order.
     * ```
     * Example: https://api.podcastindex.org/api/1.0/recent/episodes?max=7&pretty
     * ```
     * @param max Maximum number of results to return. Min 1┃Max 1000
     * ```
     * Examples: 10
     * ```
     * @param excludeString Any item containing this string will be discarded from the result set.
     *
     * This may, in certain cases, reduce your set size below your max value.
     *
     * Matches against the title and URL properties.
     * ```
     * Examples: religion
     * ```
     * @param since Return items since the specified epoch timestamp.
     * ```
     * Examples: 1612125785
     * ```
     * @param fulltext If present, return the full text value of any text fields (ex: description). If not provided, field value is truncated to 100 words.
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * */
    @GET("recent/episodes")
    fun getRecentEpisodes(
        @Query("max")
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        @Query("excludeString")
        excludeString: String? = null,
        @Query("before")
        since: Long? = null,
        @Query("fulltext")
        fulltext: String? = null,
        @Query("pretty")
        pretty: Boolean? = null
    ): TSDataState<EpisodeResponse>

    /**
     * This call returns every new feed added to the index over the past 24 hours in reverse chronological order.
     * ```
     * Examples:
     *
     * https://api.podcastindex.org/api/1.0/recent/newfeeds?pretty
     * https://api.podcastindex.org/api/1.0/recent/newfeeds?pretty&since=1613805000
     * https://api.podcastindex.org/api/1.0/recent/newfeeds?feedid=2653471&pretty
     * https://api.podcastindex.org/api/1.0/recent/newfeeds?feedid=2653471&desc&pretty
     * ```
     * @param max Maximum number of results to return. Min 1┃Max 1000
     * ```
     * Examples: 10
     * ```
     * @param excludeString Any item containing this string will be discarded from the result set.
     *
     * This may, in certain cases, reduce your set size below your max value.
     *
     * Matches against the title and URL properties.
     * ```
     * Examples: religion
     * ```
     * @param since Return items since the specified epoch timestamp.
     * ```
     * Examples: 1612125785
     * ```
     * @param feedid The PodcastIndex Feed ID to start from (or go to if desc specified).
     *
     * If since parameter also specified, value of since is ignored.
     * ```
     * Examples: 2653471
     * ```
     * @param desc If present, display feeds in descending order.
     *
     * Only applicable when using feedid parameter.
     *
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * */
    @GET("recent/newfeeds")
    fun getRecentNewFeed(
        @Query("max")
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        @Query("since")
        since: Long? = null,
        @Query("feedid")
        feedId: String? = null,
        @Query("desc")
        desc: String? = null,
        @Query("pretty")
        pretty: Boolean? = null
    ) : TSDataState<TrendingPodcastRes>

    /**
     * This call returns every new feed added to the index over the past 24 hours in reverse chronological order.
     * ```
     * Examples:
     *
     * - https://api.podcastindex.org/api/1.0/recent/feeds?pretty
     * - https://api.podcastindex.org/api/1.0/recent/feeds?max=20&cat=102,health&lang=de,ja&pretty
     * ```
     * @param max Maximum number of results to return. Min 1┃Max 1000
     * ```
     * Examples: 10
     * ```
     * @param excludeString Any item containing this string will be discarded from the result set.
     *
     * This may, in certain cases, reduce your set size below your max value.
     *
     * Matches against the title and URL properties.
     * ```
     * Examples: religion
     * ```
     * @param since Return items since the specified epoch timestamp.
     * ```
     * Examples: 1612125785
     * ```
     * @param lang Specifying a language code (like "en") will return only episodes having that specific language.
     *
     * You can specify multiple languages by separating them with commas.
     *
     * If you also want to return episodes that have no language given, use the token "unknown". (ex. en,es,ja,unknown).
     *
     * Values are not case sensitive.
     * ```
     * Examples:
     * en
     * Single ID
     *
     * en,es
     * Multiple IDs
     * ```
     * @param cat If present, display feeds in descending order.
     *
     * Only applicable when using feedid parameter.
     *
     * Parameter shall not have a value
     * @param pretty If present, makes the output “pretty” to help with debugging.
     * */
    @GET("recent/feeds")
    fun getRecentFeeds(
        @Query("max")
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        @Query("since")
        since: Long? = null,
        @Query("lang")
        lang: String? = "vi",
        @Query("cat")
        cat: String? = null,
        @Query("notcat")
        notcat: String? = null,
        @Query("pretty")
        pretty: Boolean? = null
    ) : TSDataState<TrendingPodcastRes>
}