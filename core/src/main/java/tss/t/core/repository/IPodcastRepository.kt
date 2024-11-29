package tss.t.core.repository

import androidx.annotation.IntRange
import kotlinx.coroutines.flow.Flow
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveResponse
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.StatCurrent
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes

interface IPodcastRepository {
    suspend fun searchPodcasts(
        query: String,
        type: String? = null,
        max: Int,
        aponly: Boolean = false,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true,
        pretty: Boolean = false
    ): TSDataState<SearchResponse>

    suspend fun searchPodcastsByTitle(
        query: String,
        type: String,
        max: Int, //min 1 max 100
        clean: Boolean? = null, //If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
        similar: Boolean? = true,
        fulltext: Boolean? = true,
        pretty: Boolean = false
    ): TSDataState<SearchResponse>

    suspend fun searchPodcastsByPerson(
        query: String,
        max: Int, //min 1 max 100
        fulltext: Boolean? = true,
        pretty: Boolean = false
    ): TSDataState<SearchResponse>

    fun searchMusicPodcasts(
        query: String,
        type: String,
        max: Int, //min 1 max 100
        aponly: Boolean = false, //Only returns feeds with an itunesId.
        clean: Boolean? = null, //If present, only non-explicit feeds will be returned. Meaning, feeds where the itunes:explicit flag is set to false.
        similar: Boolean? = true,
        fulltext: Boolean? = true,
        pretty: Boolean = false
    ): TSDataState<SearchResponse>

    suspend fun getCategory(pretty: Boolean? = null): Flow<TSDataState<CategoryRes>>

    fun getCurrent(pretty: Boolean? = null): TSDataState<StatCurrent>

    suspend fun getPodcastByFeedId(
        id: String,
        pretty: Boolean? = null
    ): Flow<TSDataState<PodcastByFeedIdRes>>

    fun getTrending(
        max: Int,
        since: Int,
        lang: String,
        cat: String? = null,
        notcat: String? = "sexuality",
        pretty: Boolean? = null
    ): TSDataState<TrendingPodcastRes>

    suspend fun getEpisodeByFeedId(
        id: String,
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        since: Long? = null,
        enclosure: String? = null,
        fulltext: String? = null,
        pretty: Boolean? = null
    ): Flow<TSDataState<EpisodeResponse>>

    suspend fun getRandomEpisodes(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        lang: String = "vi,en",
        cat: String? = null,
        notcat: String? = null,
        pretty: Boolean? = null
    )

    suspend fun getLiveEpisodes(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        pretty: Boolean? = null
    ): Flow<TSDataState<LiveResponse>>

    suspend fun getRecentEpisodes(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        excludeString: String? = null,
        since: Long? = null,
        fulltext: String? = null,
        pretty: Boolean? = null
    ): Flow<TSDataState<EpisodeResponse>>

    suspend fun getRecentNewFeed(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        since: Long? = null,
        feedId: String? = null,
        desc: String? = null,
        pretty: Boolean? = null
    ): Flow<TSDataState<TrendingPodcastRes>>

    suspend fun getRecentFeeds(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        since: Long? = null,
        lang: String = "vi",
        cat: String?,
        notcat: String?,
        pretty: Boolean?
    ): Flow<TSDataState<TrendingPodcastRes>>
}