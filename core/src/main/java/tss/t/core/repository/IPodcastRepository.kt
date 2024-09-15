package tss.t.core.repository

import androidx.annotation.IntRange
import kotlinx.coroutines.flow.Flow
import retrofit2.http.Query
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.SearchByPersonRes
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.StatCurrent
import tss.t.coreapi.models.TrendingPodcastRes

interface IPodcastRepository {
    suspend fun searchPodcasts(
        query: String,
        type: String, max: Int,
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
    ): TSDataState<SearchByPersonRes>

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
}