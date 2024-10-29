package tss.t.podcasts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tss.t.core.repository.IPodcastRepository
import tss.t.core.storage.SharedPref
import tss.t.core.storage.getListPodcastCategory
import tss.t.core.storage.saveListPodcastCategory
import tss.t.coreapi.API
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveResponse
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.SearchByPersonRes
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.StatCurrent
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import java.lang.IllegalStateException
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
        return withContext(Dispatchers.IO) {
            api.searchPodcasts(
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
        return withContext(Dispatchers.IO) {
            api.searchPodcastsByTitle(
                query = query,
                type = type,
                max = max,
                clean = clean,
                similar = similar,
                fulltext = fulltext,
                pretty = pretty
            )
        }
    }

    override suspend fun searchPodcastsByPerson(
        query: String,
        max: Int,
        fulltext: Boolean?,
        pretty: Boolean
    ): TSDataState<SearchByPersonRes> {
        return withContext(Dispatchers.IO) {
            api.searchPodcastsByPerson(
                query = query,
                max = max,
                fulltext = fulltext,
                pretty = pretty
            )
        }
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

    override suspend fun getPodcastByFeedId(
        id: String,
        pretty: Boolean?
    ): Flow<TSDataState<PodcastByFeedIdRes>> {
        return flow {
            val resul = api.getPodcastByFeedId(id, pretty)
            if (resul is TSDataState.Success) {
                emit(api.getPodcastByFeedId(id, pretty))
            } else {
                throw (resul as? TSDataState.Error)?.exception
                    ?: IllegalStateException("Get podcast failed")
            }
        }.retryWhen { cause, attempt ->
            cause !is IllegalStateException && attempt < 3
        }.catch {
            emit(TSDataState.Error(it))
        }
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

    override suspend fun getEpisodeByFeedId(
        id: String,
        max: Int,
        since: Long?,
        enclosure: String?,
        fulltext: String?,
        pretty: Boolean?
    ): Flow<TSDataState<EpisodeResponse>> {
        return flow {
            val result = api.getEpisodeByFeedId(
                id = id,
                max = max,
                since = since,
                enclosure = enclosure,
                fulltext = fulltext,
                pretty = pretty
            )
            if (result.isSuccess()) {
                emit(result)
            } else {
                throw result.exception()
            }
        }.retryWhen { cause, attempt ->
            attempt < 3
        }.catch {
            emit(TSDataState.Error(it))
        }

    }

    override suspend fun getRandomEpisodes(
        max: Int,
        lang: String,
        cat: String?,
        notcat: String?,
        pretty: Boolean?
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun getLiveEpisodes(
        max: Int,
        pretty: Boolean?
    ): Flow<TSDataState<LiveResponse>> {
        return withContext(Dispatchers.IO) {
            flowOf(
                api.getLiveEpisodes(
                    max = max,
                    pretty = pretty
                )
            )
        }.retryWhen { cause, attempt ->
            cause !is IllegalStateException && attempt < 3
        }.catch {
            emit(TSDataState.Error(it))
        }
    }

    override suspend fun getRecentEpisodes(
        max: Int,
        excludeString: String?,
        since: Long?,
        fulltext: String?,
        pretty: Boolean?
    ): Flow<TSDataState<EpisodeResponse>> {
        return withContext(Dispatchers.IO) {
            flowOf(
                api.getRecentEpisodes(
                    max = max,
                    excludeString = excludeString,
                    since = since,
                    fulltext = fulltext,
                    pretty = pretty
                )
            ).retryWhen { cause, attempt ->
                cause !is IllegalStateException && attempt < 3
            }.catch {
                emit(TSDataState.Error(it))
            }
        }
    }

    override suspend fun getRecentNewFeed(
        max: Int,
        since: Long?,
        feedId: String?,
        desc: String?,
        pretty: Boolean?
    ): Flow<TSDataState<TrendingPodcastRes>> {
        return withContext(Dispatchers.IO) {
            flowOf(
                api.getRecentNewFeed(
                    max = max,
                    since = since,
                    feedId = feedId,
                    desc = desc,
                    pretty = pretty
                )
            ).retryWhen { cause, attempt ->
                cause !is IllegalStateException && attempt < 3
            }.catch {
                emit(TSDataState.Error(it))
            }
        }
    }

    override suspend fun getRecentFeeds(
        max: Int,
        since: Long?,
        lang: String,
        cat: String?,
        notcat: String?,
        pretty: Boolean?
    ): Flow<TSDataState<TrendingPodcastRes>> {
        return withContext(Dispatchers.IO) {
            flowOf(
                api.getRecentFeeds(
                    max = max,
                    since = since,
                    lang = lang,
                    cat = cat,
                    notcat = notcat,
                    pretty = pretty
                )
            ).retryWhen { cause, attempt ->
                cause !is IllegalStateException && attempt < 3
            }.catch {
                emit(TSDataState.Error(it))
            }
        }
    }


}