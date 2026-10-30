package tss.t.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.EpisodeByIdRes
import tss.t.shared.model.EpisodeResponse
import tss.t.shared.model.LiveResponse
import tss.t.shared.model.PodcastByFeedIdRes
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes

/**
 * PodcastIndex endpoints the app uses (features-spec §2): E1, E2, E5–E12. Same paths and
 * defaults as Android's Retrofit `API` and the use cases that call it.
 *
 * - `null` parameters are not sent (Retrofit behaves the same); blank `cat`/`notcat` neither.
 * - When [credentials] are incomplete every call returns
 *   `TSDataState.Error(MissingCredentialsException)` without touching the network.
 * - E3 (recent episodes) and E4 (recent new feeds) are not ported: Android fetches them but
 *   never shows the result.
 * - Android's Retrofit sends `pretty=false` on E7/E8; `pretty` is a presence flag, so that
 *   asks for pretty-printed JSON. It is not sent here; only E11 sends `pretty=true` like Android.
 */
class PodcastIndexApi(
    private val client: HttpClient,
    private val credentials: CredentialsProvider,
    private val json: Json = PodcastIndexJson,
) {
    /** E1 `podcasts/trending`. [since] is epoch seconds and has no default on purpose. */
    suspend fun getTrending(
        since: Long,
        max: Int = 100,
        lang: String = "vi",
        cat: String? = null,
        notcat: String? = null,
    ): TSDataState<TrendingPodcastRes> = call(TrendingPodcastRes.serializer()) {
        client.get("podcasts/trending") {
            parameter("max", max)
            parameter("since", since)
            parameter("lang", lang)
            parameter("cat", cat.takeUnless { it.isNullOrBlank() })
            parameter("notcat", notcat.takeUnless { it.isNullOrBlank() })
        }
    }

    /** E2 `episodes/live`. */
    suspend fun getLiveEpisodes(max: Int = 100): TSDataState<LiveResponse> =
        call(LiveResponse.serializer()) {
            client.get("episodes/live") {
                parameter("max", max)
            }
        }

    /** E5 `recent/feeds`. */
    suspend fun getRecentFeeds(
        max: Int = 100,
        since: Long? = null,
        lang: String? = "vi",
        cat: String? = null,
        notcat: String? = null,
    ): TSDataState<TrendingPodcastRes> = call(TrendingPodcastRes.serializer()) {
        client.get("recent/feeds") {
            parameter("max", max)
            parameter("since", since)
            parameter("lang", lang)
            parameter("cat", cat.takeUnless { it.isNullOrBlank() })
            parameter("notcat", notcat.takeUnless { it.isNullOrBlank() })
        }
    }

    /** E6 `podcasts/bymedium`. Feed-shaped: map items with `Podcast.fromFeed`. */
    suspend fun getPodcastsByMedium(
        medium: String = MEDIUM_MUSIC,
        max: Int = 40,
    ): TSDataState<SearchResponse> = call(SearchResponse.serializer()) {
        client.get("podcasts/bymedium") {
            parameter("medium", medium)
            parameter("max", max)
        }
    }

    /** E7 `search/byterm`. */
    suspend fun searchPodcasts(
        query: String,
        type: String? = null,
        max: Int = 100,
        aponly: Boolean = false,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true,
    ): TSDataState<SearchResponse> = call(SearchResponse.serializer()) {
        client.get("search/byterm") {
            parameter("q", query)
            parameter("max", max)
            parameter("val", type)
            parameter("aponly", aponly)
            parameter("clean", clean)
            parameter("similar", similar)
            parameter("fulltext", fulltext)
        }
    }

    /**
     * E8 `search/music/byterm` with `val=music`. A blank [query] returns an empty
     * `Success` without a request, like Android's `SearchMusicFeeds`.
     */
    suspend fun searchMusicPodcasts(
        query: String,
        max: Int = 60,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true,
    ): TSDataState<SearchResponse> {
        if (query.isBlank()) {
            return TSDataState.Success(SearchResponse(count = 0, feeds = emptyList(), query = query))
        }
        return call(SearchResponse.serializer()) {
            client.get("search/music/byterm") {
                parameter("q", query)
                parameter("val", MEDIUM_MUSIC)
                parameter("max", max)
                parameter("clean", clean)
                parameter("similar", similar)
                parameter("fulltext", fulltext)
            }
        }
    }

    /** E9 `categories/list`. */
    suspend fun getCategories(): TSDataState<CategoryRes> = call(CategoryRes.serializer()) {
        client.get("categories/list")
    }

    /** E10 `episodes/byfeedid`. */
    suspend fun getEpisodesByFeedId(
        id: String,
        max: Int = 100,
        since: Long? = null,
    ): TSDataState<EpisodeResponse> = call(EpisodeResponse.serializer()) {
        client.get("episodes/byfeedid") {
            parameter("id", id)
            parameter("max", max)
            parameter("since", since)
        }
    }

    /** E11 `podcasts/byfeedid`. Android always passes `pretty = true`. */
    suspend fun getPodcastByFeedId(
        id: String,
        pretty: Boolean? = true,
    ): TSDataState<PodcastByFeedIdRes> = call(PodcastByFeedIdRes.serializer()) {
        client.get("podcasts/byfeedid") {
            parameter("id", id)
            parameter("pretty", pretty)
        }
    }

    /**
     * E12 `episodes/byid`, for episode deep links (M4 §4.2). `fulltext=true` like the web's
     * episode page, so the description is not cut at ~100 words.
     */
    suspend fun getEpisodeById(
        id: Long,
        fulltext: Boolean? = true,
    ): TSDataState<EpisodeByIdRes> = call(EpisodeByIdRes.serializer()) {
        client.get("episodes/byid") {
            parameter("id", id)
            parameter("fulltext", fulltext)
        }
    }

    private suspend fun <T : Any> call(
        deserializer: DeserializationStrategy<T>,
        request: suspend () -> HttpResponse,
    ): TSDataState<T> {
        val missing = credentials.missingCredentials()
        if (missing.isNotEmpty()) return TSDataState.Error(MissingCredentialsException(missing))
        return safeApiCall(json, deserializer, request)
    }

    companion object {
        const val MEDIUM_MUSIC = "music"
    }
}
