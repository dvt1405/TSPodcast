package tss.t.shared.preferences

import tss.t.shared.model.CategoryRes

/**
 * Favourite categories picked after onboarding (features-spec §3.2): Android's
 * `SelectFavouriteCategoryScreen` enables "Lưu và tiếp tục" from [MIN_FAVOURITE_CATEGORIES]
 * and `SharedPref.saveFavouriteCategory` stores them.
 */
class FavouriteCategories(private val preferences: AppPreferences) {

    fun canSave(count: Int): Boolean = count >= MIN_FAVOURITE_CATEGORIES

    /**
     * Stores [categories] (one entry per id, `isFavourite = true`, sorted by id) and then sets
     * `HasSelectFavouriteCategory`. Returns false and writes nothing when fewer than
     * [MIN_FAVOURITE_CATEGORIES] distinct ids are given.
     */
    suspend fun save(categories: List<CategoryRes.Category>): Boolean {
        val distinct = categories.distinctBy { it.id }.sortedBy { it.id }
        if (!canSave(distinct.size)) return false
        preferences.saveFavouriteCategories(distinct.map { it.copy(isFavourite = true) })
        return true
    }

    /** Saved favourites, sorted by id; empty when none were saved. */
    suspend fun favourites(): List<CategoryRes.Category> =
        preferences.favouriteCategories().orEmpty().sortedBy { it.id }

    /**
     * Ids joined with `","` in ascending order (`"1,3,5"`), the `cat` value of E1/E5; null when
     * nothing is saved. Sorted, unlike Android's `Set` order, so the trending cache key is stable.
     */
    suspend fun favouriteCategoryIds(): String? =
        favourites().map { it.id }.distinct().takeIf { it.isNotEmpty() }?.joinToString(",")

    companion object {
        const val MIN_FAVOURITE_CATEGORIES = 3
    }
}
