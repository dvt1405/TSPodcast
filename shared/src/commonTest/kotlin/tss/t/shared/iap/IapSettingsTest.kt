package tss.t.shared.iap

import tss.t.shared.ads.RemoteConfigTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IapSettingsTest {
    private val defaults = IapSettings.iosDefaults()

    private fun parse(values: Map<String, String>) = IapSettings.parse(defaults) { values[it] }

    @Test
    fun defaultsAreOffWithYearlyPreselectedAndThreeAdFreeProducts() {
        assertFalse(defaults.enabled)
        assertEquals("premium_yearly", defaults.defaultProductId)
        assertEquals(listOf("premium_monthly", "premium_yearly", "remove_ads_lifetime"), defaults.products.map { it.id })
        assertTrue(defaults.products.all { it.removesAds })
        assertEquals(
            setOf("tss.t.podcast.premium.monthly", "tss.t.podcast.premium.yearly", "tss.t.podcast.remove_ads.lifetime"),
            defaults.adFreeStoreProductIds,
        )
        val yearly = defaults.product("premium_yearly")!!
        assertEquals(IapProductType.Subscription, yearly.type)
        assertEquals(IapProduct.BADGE_BEST_VALUE, yearly.badge)
        assertEquals("premium", yearly.subscriptionGroup)
        assertEquals("P1Y", yearly.period)
        assertNull(yearly.basePlanId)
        assertEquals(IapProductType.OneTime, defaults.productForStoreId("tss.t.podcast.remove_ads.lifetime")!!.type)
    }

    @Test
    fun missingOrBlankKeysKeepDefaults() {
        assertEquals(defaults, IapSettings.parse(defaults) { null })
        assertEquals(defaults, IapSettings.parse(defaults) { " " })
    }

    @Test
    fun badJsonKeepsDefaultProducts() {
        assertEquals(defaults.products, parse(mapOf(IapKeys.PRODUCTS to "{oops")).products)
        assertEquals(defaults.products, parse(mapOf(IapKeys.PRODUCTS to "[1,2]")).products)
        assertEquals(defaults.products, parse(mapOf(IapKeys.PRODUCTS to """{"version":1}""")).products)
        assertEquals(defaults.enabled, parse(mapOf(IapKeys.ENABLED to "maybe")).enabled)
    }

    @Test
    fun switchesAndDefaultProduct() {
        assertTrue(parse(mapOf(IapKeys.ENABLED to "true")).enabled)
        assertEquals("premium_monthly", parse(mapOf(IapKeys.DEFAULT_PRODUCT to "premium_monthly")).defaultProductId)
    }

    @Test
    fun productsAreSortedByOrderUnknownFieldsIgnoredBadEntriesSkipped() {
        val json = """
            {"version":2,"future":{"x":1},"products":[
              {"id":"b","type":"one_time","store_product_id":"store.b","benefits":["ad_free"],"order":2,"new_field":true},
              {"id":"a","type":"subscription","store_product_id":"store.a","period":"P1M","benefits":["ad_free","other"],"badge":"best_value","order":1},
              {"id":"no_store","type":"subscription","order":0},
              {"id":"weird","type":"consumable","store_product_id":"store.w","order":0},
              {"id":"a","type":"subscription","store_product_id":"store.dup","order":0},
              {"id":"c","type":"subscription","store_product_id":"store.c","benefits":["badge_only"]},
              "garbage"
            ]}
        """.trimIndent()
        val products = parse(mapOf(IapKeys.PRODUCTS to json)).products
        assertEquals(listOf("a", "b", "c"), products.map { it.id })
        assertEquals("store.a", products[0].storeProductId)
        assertEquals(listOf("ad_free", "other"), products[0].benefits)
        assertFalse(products[2].removesAds)
        assertEquals(Int.MAX_VALUE, products[2].order)
    }

    @Test
    fun emptyProductListIsRespected() {
        assertEquals(emptyList(), parse(mapOf(IapKeys.PRODUCTS to """{"version":1,"products":[]}""")).products)
    }

    /** Contract §1.1: in-app defaults = what iOS gets from `remoteconfig/ads-iap.params.json`. */
    @Test
    fun iosDefaultsEqualTheTemplate() {
        val template = RemoteConfigTemplate.iosValuesByGroup.getValue("IAP")
        assertEquals(IapKeys.ALL.toSet(), template.keys)
        assertEquals(defaults, IapSettings.parse(IapSettings(false, "x", emptyList())) { template[it] })
    }
}
