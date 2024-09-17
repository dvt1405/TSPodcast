package tss.t.podcasts

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import tss.t.core.repository.IPodcastRepository
import tss.t.core.storage.SharedPref
import tss.t.coreapi.API
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.SearchByPersonRes
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.StatCurrent
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPodcastRepositoryImpl @Inject constructor(
    private val api: API,
    private val sharedPref: SharedPref,
    @ApplicationContext
    private val context: Context
) : IPodcastRepository {
    override suspend fun searchPodcasts(
        query: String,
        type: String,
        max: Int,
        aponly: Boolean,
        clean: Boolean?,
        similar: Boolean?,
        fulltext: Boolean?,
        pretty: Boolean
    ): TSDataState<SearchResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun searchPodcastsByTitle(
        query: String,
        type: String,
        max: Int,
        clean: Boolean?,
        similar: Boolean?,
        fulltext: Boolean?,
        pretty: Boolean
    ): TSDataState<SearchResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun searchPodcastsByPerson(
        query: String,
        max: Int,
        fulltext: Boolean?,
        pretty: Boolean
    ): TSDataState<SearchByPersonRes> {
        TODO("Not yet implemented")
    }

    override fun searchMusicPodcasts(
        query: String,
        type: String,
        max: Int,
        aponly: Boolean,
        clean: Boolean?,
        similar: Boolean?,
        fulltext: Boolean?,
        pretty: Boolean
    ): TSDataState<SearchResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getCategory(pretty: Boolean?): Flow<TSDataState<CategoryRes>> {
        TODO("Not yet implemented")
    }

    override fun getCurrent(pretty: Boolean?): TSDataState<StatCurrent> {
        TODO("Not yet implemented")
    }

    override suspend fun getPodcastByFeedId(
        id: String,
        pretty: Boolean?
    ): Flow<TSDataState<PodcastByFeedIdRes>> {
        TODO("Not yet implemented")
    }

    override fun getTrending(
        max: Int,
        since: Int,
        lang: String,
        cat: String?,
        notcat: String?,
        pretty: Boolean?
    ): TSDataState<TrendingPodcastRes> {
        val jsonStr = context.assets.open("Trending.json")
            .bufferedReader(Charset.defaultCharset())
            .readText()
        return TSDataState.Success(
            Gson().fromJson(jsonStr, TrendingPodcastRes::class.java)
        )
    }

    override suspend fun getEpisodeByFeedId(
        id: String,
        max: Int,
        since: Long?,
        enclosure: String?,
        fulltext: String?,
        pretty: Boolean?
    ): Flow<TSDataState<EpisodeResponse>> {
        TODO("Not yet implemented")
    }

}