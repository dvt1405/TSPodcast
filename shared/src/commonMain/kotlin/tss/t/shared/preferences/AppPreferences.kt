package tss.t.shared.preferences

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.TrendingPodcastRes

/**
 * Typed view of the app's [KeyValueStore], with the key names of Android's `SharedPref`
 * (`core/.../storage/SharedPref.kt`, `podcasts/.../usecase/GetTrendingPodcasts.kt`) plus
 * [KEY_ALLOW_EXPLICIT_CONTENT] for D6.
 *
 * Objects are stored as JSON strings like Android stores Gson strings. A value that does not
 * parse reads as null (Android removes it; here the next write replaces it).
 */
class AppPreferences(
    private val store: KeyValueStore,
    private val json: Json,
) {
    suspend fun isOnboardingFinished(): Boolean = store.getBoolean(KEY_ONBOARDING_FINISHED) ?: false

    suspend fun saveOnboardingFinished(isFinished: Boolean) {
        store.putBoolean(KEY_ONBOARDING_FINISHED, isFinished)
    }

    suspend fun hasSelectFavouriteCategory(): Boolean =
        store.getBoolean(KEY_HAS_SELECT_FAVOURITE_CATEGORY) ?: false

    /** Favourite categories as saved by [FavouriteCategories.save], or null when never saved. */
    suspend fun favouriteCategories(): List<CategoryRes.Category>? =
        decode(store.getString(KEY_LIST_FAVOURITE_CATEGORY), categoryListSerializer)

    /** Use [FavouriteCategories.save], which enforces the minimum of 3. */
    internal suspend fun saveFavouriteCategories(categories: List<CategoryRes.Category>) {
        // List first, flag last: a crash in between leaves the user on the selection screen.
        store.putString(KEY_LIST_FAVOURITE_CATEGORY, json.encodeToString(categoryListSerializer, categories))
        store.putBoolean(KEY_HAS_SELECT_FAVOURITE_CATEGORY, true)
    }

    /** Last E9 list fetched from the network (`CategoryRes`), or null. */
    suspend fun categoryRes(): CategoryRes? =
        decode(store.getString(KEY_CATEGORY_RES), CategoryRes.serializer())

    suspend fun saveCategoryRes(categories: CategoryRes) {
        store.putString(KEY_CATEGORY_RES, json.encodeToString(CategoryRes.serializer(), categories))
    }

    /** Disk copy of E1 for [cat] (null or blank: no category) and when it was stored. */
    suspend fun cachedTrending(cat: String?): CachedTrending? {
        val savedAt = store.getLong(lastUpdateTrendingKey(cat)) ?: return null
        val value = decode(store.getString(cachedTrendingKey(cat)), TrendingPodcastRes.serializer())
            ?: return null
        return CachedTrending(value, savedAt)
    }

    suspend fun saveTrending(cat: String?, value: TrendingPodcastRes, savedAtMillis: Long) {
        store.putString(cachedTrendingKey(cat), json.encodeToString(TrendingPodcastRes.serializer(), value))
        store.putLong(lastUpdateTrendingKey(cat), savedAtMillis)
    }

    /** D6: explicit content is filtered unless this is true. Defaults to false. */
    suspend fun allowExplicitContent(): Boolean = store.getBoolean(KEY_ALLOW_EXPLICIT_CONTENT) ?: false

    suspend fun saveAllowExplicitContent(allow: Boolean) {
        store.putBoolean(KEY_ALLOW_EXPLICIT_CONTENT, allow)
    }

    /**
     * The user turned on new-podcast notifications in the app (M4 §4.4). Defaults to false:
     * nothing is asked or subscribed at launch. iOS only (Android has no opt-in).
     */
    suspend fun notificationsOptIn(): Boolean = store.getBoolean(KEY_NOTIFICATIONS_OPT_IN) ?: false

    suspend fun saveNotificationsOptIn(optIn: Boolean) {
        store.putBoolean(KEY_NOTIFICATIONS_OPT_IN, optIn)
    }

    /** The in-app notification prompt was shown once already. Defaults to false. */
    suspend fun notificationsPromptShown(): Boolean = store.getBoolean(KEY_NOTIFICATIONS_PROMPT_SHOWN) ?: false

    suspend fun saveNotificationsPromptShown(shown: Boolean) {
        store.putBoolean(KEY_NOTIFICATIONS_PROMPT_SHOWN, shown)
    }

    /** Removes every key (UI test reset). */
    suspend fun clear() {
        store.clear()
    }

    private fun <T> decode(text: String?, serializer: KSerializer<T>): T? {
        if (text == null) return null
        return runCatching { json.decodeFromString(serializer, text) }.getOrNull()
    }

    /** A disk copy of E1 and its timestamp (epoch millis). */
    data class CachedTrending(val value: TrendingPodcastRes, val savedAtMillis: Long)

    companion object {
        const val KEY_ONBOARDING_FINISHED = "OnboardingFinished"
        const val KEY_HAS_SELECT_FAVOURITE_CATEGORY = "HasSelectFavouriteCategory"
        const val KEY_LIST_FAVOURITE_CATEGORY = "ListFavouriteCategory"
        const val KEY_CATEGORY_RES = "CategoryRes"
        const val KEY_CACHED_TRENDING = "cachedTrending"
        const val KEY_LAST_UPDATE_TRENDING = "lastUpdateTrending"
        const val KEY_ALLOW_EXPLICIT_CONTENT = "AllowExplicitContent"
        const val KEY_NOTIFICATIONS_OPT_IN = "NotificationsOptIn"
        const val KEY_NOTIFICATIONS_PROMPT_SHOWN = "NotificationsPromptShown"

        /** `cachedTrending` without a category, `cachedTrending_<cat>` with one, like Android. */
        fun cachedTrendingKey(cat: String?): String = withCategory(KEY_CACHED_TRENDING, cat)

        fun lastUpdateTrendingKey(cat: String?): String = withCategory(KEY_LAST_UPDATE_TRENDING, cat)

        private fun withCategory(key: String, cat: String?): String {
            val normalized = cat?.trim().orEmpty()
            return if (normalized.isEmpty()) key else "${key}_$normalized"
        }

        private val categoryListSerializer = ListSerializer(CategoryRes.Category.serializer())
    }
}
