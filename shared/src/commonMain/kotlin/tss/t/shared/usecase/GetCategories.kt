package tss.t.shared.usecase

import kotlinx.serialization.json.Json
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.generated.BundledAssets
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi
import tss.t.shared.preferences.AppPreferences

/**
 * E9 `categories/list`, same fallbacks as Android's `PodcastsRepositoryImpl.getCategory`.
 * Always returns `Success`:
 * 1. with [useCache] and a list already fetched in this process, that list, no request;
 * 2. otherwise the network list, which is then kept in memory and on disk (`CategoryRes`);
 * 3. when the network fails (including missing credentials, which sends nothing): the last
 *    list fetched in this process, else the disk copy, else the bundled
 *    `coreApi/src/main/assets/categories.json` (112 categories).
 *
 * Only a list that came from the network is kept, so the asset fallback never hides a later
 * successful request. `CancellationException` propagates.
 */
class GetCategories internal constructor(
    private val api: PodcastIndexApi,
    private val cache: InMemoryTtlCache<Unit, CategoryRes>,
    private val preferences: AppPreferences,
    private val json: Json,
) {
    suspend operator fun invoke(useCache: Boolean = false): TSDataState<CategoryRes> {
        if (useCache) cache.get(Unit)?.let { return TSDataState.Success(it) }

        val network = api.getCategories()
        if (network is TSDataState.Success) {
            cache.put(Unit, network.data)
            preferences.saveCategoryRes(network.data)
            return network
        }
        val fallback = cache.get(Unit)
            ?: preferences.categoryRes()
            ?: CategoryRes.fromAssetJson(json, BundledAssets.categories)
        return TSDataState.Success(fallback)
    }
}
