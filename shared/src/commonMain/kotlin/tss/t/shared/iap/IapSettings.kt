package tss.t.shared.iap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * In-app purchase configuration from Remote Config (contract:
 * docs/in-progress/plans/monetization-ads-iap.md §2 "IAP"). Prices, periods and trials come from
 * the store, never from here.
 */
object IapKeys {
    const val ENABLED = "iap_enabled"
    const val DEFAULT_PRODUCT = "iap_default_product"
    const val PRODUCTS = "iap_products"

    val ALL: List<String> = listOf(ENABLED, DEFAULT_PRODUCT, PRODUCTS)
}

enum class IapProductType(val key: String) {
    Subscription("subscription"),
    OneTime("one_time");

    companion object {
        fun fromKey(key: String?): IapProductType? = entries.firstOrNull { it.key == key }
    }
}

/** One product of `iap_products`. [id] is the internal id (analytics, default product). */
data class IapProduct(
    val id: String,
    val type: IapProductType,
    /** iOS product id (App Store Connect); Android productId. */
    val storeProductId: String,
    /** Android subscription base plan; null on iOS. */
    val basePlanId: String?,
    /** iOS subscription group, informational. */
    val subscriptionGroup: String?,
    /** ISO 8601 period (`P1M`), informational: the UI shows the store's period. */
    val period: String?,
    val benefits: List<String>,
    /** null or [BADGE_BEST_VALUE]. */
    val badge: String?,
    val order: Int,
) {
    /** The product turns every ad off (contract §1.3). */
    val removesAds: Boolean get() = BENEFIT_AD_FREE in benefits

    companion object {
        const val BENEFIT_AD_FREE = "ad_free"
        const val BADGE_BEST_VALUE = "best_value"
    }
}

data class IapSettings(
    val enabled: Boolean,
    val defaultProductId: String,
    /** Sorted by [IapProduct.order]. */
    val products: List<IapProduct>,
) {
    /** The product with internal [id], or null. */
    fun product(id: String): IapProduct? = products.firstOrNull { it.id == id }

    /** The product whose store id is [storeProductId], or null. */
    fun productForStoreId(storeProductId: String): IapProduct? =
        products.firstOrNull { it.storeProductId == storeProductId }

    /** Store ids of the products that remove ads (entitlement check). */
    val adFreeStoreProductIds: Set<String>
        get() = products.filter { it.removesAds }.map { it.storeProductId }.toSet()

    companion object {
        const val DEFAULT_PRODUCT_ID = "premium_yearly"

        /**
         * `iap_products` as iOS gets it from the template (`conditionalValues.platform_ios`).
         * [IapSettingsTemplateTest] keeps this equal to `remoteconfig/ads-iap.params.json`.
         */
        const val IOS_DEFAULT_PRODUCTS_JSON = "{\"version\":1,\"products\":[" +
            "{\"id\":\"premium_monthly\",\"type\":\"subscription\",\"store_product_id\":\"tss.t.podcast.premium.monthly\",\"subscription_group\":\"premium\",\"period\":\"P1M\",\"benefits\":[\"ad_free\"],\"badge\":null,\"order\":1}," +
            "{\"id\":\"premium_yearly\",\"type\":\"subscription\",\"store_product_id\":\"tss.t.podcast.premium.yearly\",\"subscription_group\":\"premium\",\"period\":\"P1Y\",\"benefits\":[\"ad_free\"],\"badge\":\"best_value\",\"order\":2}," +
            "{\"id\":\"remove_ads_lifetime\",\"type\":\"one_time\",\"store_product_id\":\"tss.t.podcast.remove_ads.lifetime\",\"benefits\":[\"ad_free\"],\"badge\":null,\"order\":3}" +
            "]}"

        /**
         * In-app defaults for iOS = the template: off this phase (owner 2026-11-13, Paid Apps
         * Agreement pending; Remote Config `iap_enabled` turns it on), `premium_yearly`, the three
         * iOS products.
         */
        fun iosDefaults(): IapSettings = IapSettings(
            enabled = false,
            defaultProductId = DEFAULT_PRODUCT_ID,
            products = parseProducts(IOS_DEFAULT_PRODUCTS_JSON).orEmpty(),
        )

        /**
         * Remote Config over [defaults]. A missing/blank key keeps the default; bad JSON or a
         * value without a `products` array keeps the default products; unknown fields are ignored.
         */
        fun parse(defaults: IapSettings, read: (String) -> String?): IapSettings {
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
         * The `products` of an `iap_products` value, sorted by `order`, or null when [text] is
         * not an object with a `products` array. An entry without `id` / `store_product_id` or
         * with an unknown `type` is skipped; a repeated `id` keeps the first.
         */
        fun parseProducts(text: String): List<IapProduct>? {
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val array = root["products"] as? JsonArray ?: return null
            val seen = mutableSetOf<String>()
            return array.mapIndexedNotNull { index, element ->
                val obj = element as? JsonObject ?: return@mapIndexedNotNull null
                val id = obj.string("id") ?: return@mapIndexedNotNull null
                val type = IapProductType.fromKey(obj.string("type")) ?: return@mapIndexedNotNull null
                val storeId = obj.string("store_product_id") ?: return@mapIndexedNotNull null
                if (!seen.add(id)) return@mapIndexedNotNull null
                val benefits = (obj["benefits"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    .orEmpty()
                val order = (obj["order"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: Int.MAX_VALUE
                index to IapProduct(
                    id = id,
                    type = type,
                    storeProductId = storeId,
                    basePlanId = obj.string("base_plan_id"),
                    subscriptionGroup = obj.string("subscription_group"),
                    period = obj.string("period"),
                    benefits = benefits,
                    badge = obj.string("badge"),
                    order = order,
                )
            }.sortedWith(compareBy({ it.second.order }, { it.first })).map { it.second }
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
