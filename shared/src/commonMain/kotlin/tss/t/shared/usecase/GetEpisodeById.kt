package tss.t.shared.usecase

import tss.t.shared.model.Episode
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E12 `episodes/byid?id=&fulltext=true`: one episode, for `/episode/<id>-<slug>` deep links
 * (M4 §4.2; Android has no such call yet, the web does). One request, no retry and no cache,
 * like every endpoint except E10/E11. Not filtered here: the caller applies D6 and the
 * blacklist to what it shows, as it does for the podcast's episode list.
 *
 * An unknown id (`"episode": []`) is `Error(NoSuchElementException)`.
 */
class GetEpisodeById internal constructor(
    private val api: PodcastIndexApi,
) {
    suspend operator fun invoke(id: Long): TSDataState<Episode> =
        when (val result = api.getEpisodeById(id)) {
            is TSDataState.Success -> result.data.episode
                ?.let { TSDataState.Success(it) }
                ?: TSDataState.Error(NoSuchElementException("episodes/byid: no episode $id"))

            is TSDataState.Error -> TSDataState.Error(result.exception)
            is TSDataState.Loading -> TSDataState.Error(IllegalStateException("episodes/byid failed"))
        }
}
