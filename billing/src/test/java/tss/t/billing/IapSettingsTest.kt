package tss.t.billing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class IapSettingsTest {
    private val defaults = IapSettings.DEFAULTS

    private fun parse(values: Map<String, String>) = IapSettings.parse(defaults) { values[it] }

    @Test
    fun `defaults are off with yearly preselected`() {
        assertFalse(defaults.enabled)
        assertEquals("premium_yearly", defaults.defaultProduct()?.id)
        assertEquals(listOf("premium_monthly", "premium_yearly", "remove_ads_lifetime"), defaults.products.map { it.id })
        assertTrue(defaults.products.all { it.isAdFree })
    }

    @Test
    fun `unset keys keep the in-app defaults`() {
        // Firebase returns "" for a key that was never set.
        assertEquals(defaults, IapSettings.parse(defaults) { "" })
        assertEquals(defaults, IapSettings.parse(defaults) { null })
    }

    @Test
    fun `values override defaults`() {
        val s = parse(
            mapOf(
                IapKeys.ENABLED to "false",
                IapKeys.DEFAULT_PRODUCT to "remove_ads_lifetime",
                IapKeys.PRODUCTS to """{"version":2,"products":[
                    {"id":"b","type":"one_time","store_product_id":"b_store","benefits":["ad_free"],"order":2},
                    {"id":"a","type":"subscription","store_product_id":"sub","base_plan_id":"weekly",
                     "period":"P1W","benefits":["ad_free","future_thing"],"badge":"best_value","order":1,
                     "subscription_group":"ios_only","new_field":{"x":1}}
                ]}""",
            )
        )
        assertFalse(s.enabled)
        assertEquals("remove_ads_lifetime", s.defaultProductId)
        // Not in the list: falls back to the first product.
        assertEquals("a", s.defaultProduct()?.id)
        assertEquals(listOf("a", "b"), s.products.map { it.id })
        val a = s.products[0]
        assertEquals(IapProductType.Subscription, a.type)
        assertEquals("sub", a.storeProductId)
        assertEquals("weekly", a.basePlanId)
        assertEquals("P1W", a.period)
        assertEquals(IapProduct.BADGE_BEST_VALUE, a.badge)
        assertTrue(a.isAdFree)
        val b = s.products[1]
        assertEquals(IapProductType.OneTime, b.type)
        assertNull(b.basePlanId)
        assertNull(b.badge)
    }

    @Test
    fun `broken products json keeps the defaults`() {
        listOf("{not json", "[]", """{"products":[]}""", """{"products":"x"}""", """{"version":1}""").forEach {
            assertEquals(it, defaults.products, parse(mapOf(IapKeys.PRODUCTS to it)).products)
        }
    }

    @Test
    fun `invalid products are skipped, valid ones kept`() {
        val json = """{"products":[
            {"id":"unknown_type","type":"consumable","store_product_id":"x","order":1},
            {"id":"no_store","type":"one_time","order":2},
            {"id":"sub_without_plan","type":"subscription","store_product_id":"premium","order":3},
            {"type":"one_time","store_product_id":"no_id","order":4},
            "not an object",
            {"id":"ok","type":"one_time","store_product_id":"ok_store","badge":null},
            {"id":"ok","type":"one_time","store_product_id":"duplicate"}
        ]}"""
        val products = parse(mapOf(IapKeys.PRODUCTS to json)).products
        assertEquals(listOf("ok"), products.map { it.id })
        assertEquals("ok_store", products[0].storeProductId)
        assertNull(products[0].badge)
        assertFalse(products[0].isAdFree)
    }

    @Test
    fun `garbage booleans keep the default`() {
        assertFalse(parse(mapOf(IapKeys.ENABLED to "maybe")).enabled)
        assertTrue(parse(mapOf(IapKeys.ENABLED to "1")).enabled)
    }

    /** Contract §1.1: the in-app defaults equal the template's Android `defaultValue`s. */
    @Test
    fun `defaults equal the remote config template`() {
        val template = File("../remoteconfig/ads-iap.params.json")
        assumeTrue("template not in this checkout", template.isFile)
        val params = JSONObject(template.readText())
            .getJSONObject("parameterGroups").getJSONObject("IAP").getJSONObject("parameters")
        val values = params.keys().asSequence().associateWith { key ->
            params.getJSONObject(key).getJSONObject("defaultValue").getString("value")
        }
        assertEquals(setOf(IapKeys.ENABLED, IapKeys.DEFAULT_PRODUCT, IapKeys.PRODUCTS), values.keys)
        // Parse on top of something else, so a key that merely fell back would not pass.
        val other = IapSettings(enabled = false, defaultProductId = "x", products = emptyList())
        assertEquals(defaults, IapSettings.parse(other) { values[it] })
    }
}
