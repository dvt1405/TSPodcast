package tss.t.billing

import org.json.JSONArray
import org.json.JSONObject
import tss.t.sharedlibrary.utils.ConfigAPI
import javax.inject.Inject
import javax.inject.Singleton

/** Remote Config keys. Contract: docs/in-progress/plans/monetization-ads-iap.md §2. */
object IapKeys {
    /** Shows the paywall entry. false only hides it: an owned Premium keeps working. */
    const val ENABLED = "iap_enabled"

    /** Internal [IapProduct.id] preselected on the paywall. */
    const val DEFAULT_PRODUCT = "iap_default_product"

    /** JSON `{ "version": 1, "products": [ ... ] }`, see [IapSettings.parseProducts]. */
    const val PRODUCTS = "iap_products"
}

enum class IapProductType(val key: String) {
    /** Play subscription (product + base plan). */
    Subscription("subscription"),

    /** Play one-time product, never consumed (non-consumable). */
    OneTime("one_time");

    companion object {
        fun fromKey(key: String?): IapProductType? = entries.firstOrNull { it.key == key }
    }
}

/** Benefits a product grants. Unknown benefits from Remote Config are kept but ignored. */
object IapBenefits {
    /** No ads of any format (Premium, `tss.t.ads.AdFreeEntitlement`). */
    const val AD_FREE = "ad_free"
}

/**
 * One sellable package. Price, period and trial come from the store, never from here:
 * [period] is informational only.
 */
data class IapProduct(
    /** Internal id, the same on both platforms (analytics, [IapSettings.defaultProductId]). */
    val id: String,
    val type: IapProductType,
    /** Play product id. A subscription's base plans share it. */
    val storeProductId: String,
    /** Play base plan of a [IapProductType.Subscription]; null for one-time products. */
    val basePlanId: String?,
    /** ISO 8601 (`P1M`, `P1Y`), informational. */
    val period: String?,
    val benefits: List<String>,
    /** null or `best_value`. */
    val badge: String?,
    val order: Int,
) {
    val isAdFree: Boolean get() = IapBenefits.AD_FREE in benefits

    companion object {
        const val BADGE_BEST_VALUE = "best_value"
    }
}

/**
 * An immutable snapshot of the `iap_*` settings. Built from Remote Config on top of
 * [IapSettings.defaults] (equal to the template's `defaultValue`s), so a missing key, an
 * unparsable value or a device that has never fetched all behave like the published template.
 */
data class IapSettings(
    val enabled: Boolean,
    val defaultProductId: String,
    /** Sorted by [IapProduct.order]. */
    val products: List<IapProduct>,
) {
    /** The product to preselect: [defaultProductId] when listed, else the first one. */
    fun defaultProduct(): IapProduct? =
        products.firstOrNull { it.id == defaultProductId } ?: products.firstOrNull()

    companion object {
        val DEFAULT_PRODUCTS: List<IapProduct> = listOf(
            IapProduct(
                id = "premium_monthly",
                type = IapProductType.Subscription,
                storeProductId = "premium",
                basePlanId = "monthly",
                period = "P1M",
                benefits = listOf(IapBenefits.AD_FREE),
                badge = null,
                order = 1,
            ),
            IapProduct(
                id = "premium_yearly",
                type = IapProductType.Subscription,
                storeProductId = "premium",
                basePlanId = "yearly",
                period = "P1Y",
                benefits = listOf(IapBenefits.AD_FREE),
                badge = IapProduct.BADGE_BEST_VALUE,
                order = 2,
            ),
            IapProduct(
                id = "remove_ads_lifetime",
                type = IapProductType.OneTime,
                storeProductId = "remove_ads_lifetime",
                basePlanId = null,
                period = null,
                benefits = listOf(IapBenefits.AD_FREE),
                badge = null,
                order = 3,
            ),
        )

        /**
         * Owner, 2026-11-13: IAP hidden this phase (App Store Paid Apps Agreement pending), turned
         * on later through Remote Config `iap_enabled`; yearly preselected.
         */
        val DEFAULTS = IapSettings(
            enabled = false,
            defaultProductId = "premium_yearly",
            products = DEFAULT_PRODUCTS,
        )

        /**
         * Applies Remote Config values over [defaults]. [read] returns the raw string for a
         * key, or null/blank when the key is not set (Firebase returns "" for unknown keys).
         */
        fun parse(defaults: IapSettings = DEFAULTS, read: (String) -> String?): IapSettings {
            fun raw(key: String) = read(key)?.trim()?.takeIf { it.isNotEmpty() }
            val enabled = when (raw(IapKeys.ENABLED)?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> defaults.enabled
            }
            return IapSettings(
                enabled = enabled,
                defaultProductId = raw(IapKeys.DEFAULT_PRODUCT) ?: defaults.defaultProductId,
                products = raw(IapKeys.PRODUCTS)?.let(::parseProducts) ?: defaults.products,
            )
        }

        /**
         * `iap_products`. Returns null (= keep the defaults) when the JSON is broken or has no
         * usable product. Products with an unknown `type`, no `id` / `store_product_id`, a
         * subscription without `base_plan_id` or a duplicate `id` are skipped; unknown fields
         * are ignored.
         */
        fun parseProducts(json: String): List<IapProduct>? = runCatching {
            val array = JSONObject(json).optJSONArray("products") ?: return null
            val seen = mutableSetOf<String>()
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                parseProduct(obj)?.takeIf { seen.add(it.id) }
            }.sortedBy { it.order }.takeIf { it.isNotEmpty() }
        }.getOrNull()

        private fun parseProduct(obj: JSONObject): IapProduct? {
            val id = obj.string("id") ?: return null
            val type = IapProductType.fromKey(obj.string("type")) ?: return null
            val storeId = obj.string("store_product_id") ?: return null
            val basePlan = obj.string("base_plan_id")
            if (type == IapProductType.Subscription && basePlan == null) return null
            return IapProduct(
                id = id,
                type = type,
                storeProductId = storeId,
                basePlanId = basePlan.takeIf { type == IapProductType.Subscription },
                period = obj.string("period"),
                benefits = obj.optJSONArray("benefits").strings(),
                badge = obj.string("badge"),
                order = obj.opt("order").let { (it as? Number)?.toInt() ?: (it as? String)?.toIntOrNull() }
                    ?: Int.MAX_VALUE,
            )
        }

        /** A non-blank string field; JSON null, numbers and blanks are null. */
        private fun JSONObject.string(key: String): String? =
            (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

        private fun JSONArray?.strings(): List<String> = this?.let { array ->
            (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.trim()?.takeIf(String::isNotEmpty) }
        }.orEmpty()
    }
}

/** Read access to the current [IapSettings]. */
interface IapConfig {
    fun current(): IapSettings
}

/**
 * [IapConfig] backed by Firebase Remote Config through [ConfigAPI]. Re-read at most every
 * [CACHE_MS]; the snapshot is immutable and safe to share across threads.
 */
@Singleton
class RemoteIapConfig @Inject constructor(
    private val configAPI: ConfigAPI,
) : IapConfig {
    @Volatile
    private var cached: Pair<Long, IapSettings>? = null

    override fun current(): IapSettings {
        val now = System.currentTimeMillis()
        cached?.let { (at, settings) -> if (now - at < CACHE_MS) return settings }
        val settings = runCatching {
            IapSettings.parse { key -> configAPI.getString(key, null) }
        }.getOrDefault(IapSettings.DEFAULTS)
        cached = now to settings
        return settings
    }

    private companion object {
        const val CACHE_MS = 30_000L
    }
}
