package tss.t.shared.ads

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract §1.1: the in-app defaults are what iOS gets from `remoteconfig/ads-iap.params.json`,
 * so a device that never fetched Remote Config behaves like one that did.
 */
class AdsSettingsTemplateTest {
    private val template = RemoteConfigTemplate.iosValuesByGroup.getValue("Ads (v3.5+)")
    private val defaults = AdsSettings.defaults(debug = false)

    @Test
    fun everyAdsKeyOfTheTemplateIsKnownAndViceVersa() {
        assertEquals(AdsKeys.ALL.toSet(), template.keys)
    }

    @Test
    fun releaseDefaultsEqualTheTemplate() {
        val parsed = AdsSettings.parse(defaults, allowUnitIdOverrides = true) { template[it] }
        assertEquals(defaults, parsed.copy(unitIdOverrides = emptyMap()))
        assertEquals(AdPlacement.entries.toSet(), parsed.placementsEnabled.keys)
    }

    @Test
    fun bakedIosUnitIdsEqualTheTemplateForEveryPlacement() {
        val parsed = AdsSettings.parse(defaults, allowUnitIdOverrides = true) { template[it] }
        assertEquals(AdPlacement.entries.toSet(), parsed.unitIdOverrides.keys)
        for (placement in AdPlacement.entries) {
            assertEquals(
                parsed.unitIdOverrides.getValue(placement),
                IosAdUnitIds.forPlacement(placement, defaults, debug = false),
                placement.key,
            )
        }
        val raw = kotlinx.serialization.json.Json.parseToJsonElement(template.getValue(AdsKeys.UNIT_IDS))
        assertEquals(
            IosAdUnitIds.RELEASE_REWARDED_INTERSTITIAL,
            (raw as kotlinx.serialization.json.JsonObject).getValue("rewarded_interstitial")
                .let { (it as kotlinx.serialization.json.JsonPrimitive).content },
        )
    }
}
