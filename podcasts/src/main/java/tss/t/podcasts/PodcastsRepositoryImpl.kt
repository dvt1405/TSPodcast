package tss.t.podcasts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import tss.t.core.repository.IPodcastRepository
import tss.t.core.storage.SharedPref
import tss.t.core.storage.getListPodcastCategory
import tss.t.core.storage.saveListPodcastCategory
import tss.t.coreapi.API
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.SearchByPersonRes
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.StatCurrent
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PodcastsRepositoryImpl @Inject constructor(
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
        return api.searchPodcasts(
            query = query,
            max = max,
            type = type,
            aponly = aponly,
            clean = clean,
            similar = similar,
            fulltext = fulltext,
            pretty = pretty
        )
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
        return api.searchPodcastsByTitle(
            query = query,
            type = type,
            max = max,
            clean = clean,
            similar = similar,
            fulltext = fulltext,
            pretty = pretty
        )
    }

    override fun searchPodcastsByPerson(
        query: String,
        max: Int,
        fulltext: Boolean?,
        pretty: Boolean
    ): TSDataState<SearchByPersonRes> {
        return api.searchPodcastsByPerson(
            query = query,
            max = max,
            fulltext = fulltext,
            pretty = pretty
        )
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
        return api.searchMusicPodcasts(
            query = query,
            type = type,
            max = max,
            aponly = aponly,
            clean = clean,
            similar = similar,
            fulltext = fulltext,
            pretty = pretty
        )
    }

    override suspend fun getCategory(
        pretty: Boolean?
    ): Flow<TSDataState<CategoryRes>> = flow<TSDataState<CategoryRes>> {
        val network = api.getCategory(pretty = pretty)
        var category: CategoryRes?
        if (network is TSDataState.Success) {
            category = network.data
            sharedPref.saveListPodcastCategory(category)
        } else {
            category = sharedPref.getListPodcastCategory()
        }
        if (category == null) {
            category = getCategoriesFromAsset()
        }
        emit(TSDataState.Success(category))
    }.flowOn(Dispatchers.IO)

    private fun getCategoriesFromAsset(): CategoryRes {
        val listFeed = mutableListOf<CategoryRes.Category>()
        val categories = context.assets.open("categories.json")
            .bufferedReader()
            .readText()
        val jsArr = JSONObject(categories).getJSONArray("Categories")
        for (i in 0 until jsArr.length()) {
            val js = jsArr.getJSONObject(i)
            listFeed.add(
                CategoryRes.Category(
                    js.optInt("id"),
                    js.optString("name")
                )
            )
        }
        return CategoryRes(listFeed.size, listFeed)
    }

    override fun getCurrent(pretty: Boolean?): TSDataState<StatCurrent> {
        return api.getCurrent(pretty)
    }

    override fun getPodcastByFeedId(id: String, pretty: Boolean?): TSDataState<PodcastByFeedIdRes> {
        return api.getPodcastByFeedId(id, pretty)
    }

    override fun getTrending(
        max: Int,
        since: Int,
        lang: String,
        cat: String?,
        notcat: String?,
        pretty: Boolean?
    ): TSDataState<TrendingPodcastRes> {
        return api.getTrending(
            max = max, since = since, lang = lang, cat = cat, notcat = notcat, pretty = pretty
        )
    }

}