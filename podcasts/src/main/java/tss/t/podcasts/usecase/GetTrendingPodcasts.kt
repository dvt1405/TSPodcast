package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.core.storage.SharedPref
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import tss.t.podcasts.BlacklistRepositoryImpl
import javax.inject.Inject

class GetTrendingPodcasts @Inject constructor(
    private val repository: IPodcastRepository,
    private val sharedPref: SharedPref,
    private val blacklistRepositoryImpl: BlacklistRepositoryImpl
) {

    suspend operator fun invoke(
        max: Int,
        since: Int,
        lang: String = "vi",
        cat: String? = null,
        notcat: String? = null,
        pretty: Boolean? = null
    ): TSDataState<TrendingPodcastRes> {
        if (cachedData == null) {
            runCatching {
                cachedData = sharedPref.get("cachedTrending")
                lastUpdate = sharedPref.get("lastUpdateTrending") ?: 0L
            }
        }
        if (cachedDataFav == null) {
            runCatching {
                cachedDataFav = sharedPref.get("cachedTrending_$cat")
                lastUpdateFav = sharedPref.get("lastUpdateTrending_$cat") ?: 0L
            }
        }
        // Read the mutable companion fields once each; the previous null check
        // plus `!!` were separate reads reachable from several coroutines.
        if (cat.isNullOrEmpty()) {
            val cached = cachedData
            if (cached != null && System.currentTimeMillis() - lastUpdate < 15 * A_MINUTES) {
                return TSDataState.Success(cached)
            }
        } else {
            val cached = cachedDataFav
            if (cached != null && System.currentTimeMillis() - lastUpdateFav < 15 * A_MINUTES) {
                return TSDataState.Success(cached)
            }
        }

        return repository.getTrending(
            max = max,
            since = since,
            lang = lang,
            cat = cat,
            notcat = notcat,
            pretty = pretty
        ).also { it ->
            synchronized(lock) {
                if (it is TSDataState.Success) {
                    val result = it.data.items.filter {
                        !(blacklistRepositoryImpl.isInBlacklist(it.id.toString())
                                || blacklistRepositoryImpl.isInBlacklist(it.feedId.toString())
                                || blacklistRepositoryImpl.isContainKeywordsBlacklist(it.title))
                    }
                    if (cat.isNullOrEmpty()) {
                        cachedData = it.data.copy(
                            items = result,
                            count = result.size
                        )
                        lastUpdate = System.currentTimeMillis()
                        sharedPref.save("lastUpdateTrending", lastUpdate)
                        sharedPref.save("cachedTrending", cachedData)
                    } else {
                        cachedDataFav = it.data.copy(
                            items = result,
                            count = result.size
                        )
                        lastUpdateFav = System.currentTimeMillis()
                        // These two were transposed: the DTO was written under
                        // the timestamp key and vice versa, so the per-category
                        // disk cache never restored (the read at the top of this
                        // function expects a TrendingPodcastRes, not a Long).
                        sharedPref.save("lastUpdateTrending_$cat", lastUpdateFav)
                        sharedPref.save("cachedTrending_$cat", cachedDataFav)
                    }
                }
            }
        }
    }

    companion object {
        private const val A_MINUTES = 60 * 1000
        @Volatile
        var lastUpdate = 0L

        @Volatile
        var cachedData: TrendingPodcastRes? = null

        @Volatile
        var lastUpdateFav = 0L

        @Volatile
        var cachedDataFav: TrendingPodcastRes? = null
        val lock = Any()
    }

}