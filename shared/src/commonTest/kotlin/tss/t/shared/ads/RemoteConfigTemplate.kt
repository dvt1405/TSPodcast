package tss.t.shared.ads

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tss.t.shared.fixtures.Fixtures

/**
 * `remoteconfig/ads-iap.params.json` as iOS reads it: every parameter's `defaultValue`, replaced
 * by `conditionalValues.platform_ios` where present (contract §1).
 */
object RemoteConfigTemplate {
    /** Group name → (key → iOS value). */
    val iosValuesByGroup: Map<String, Map<String, String>> by lazy {
        val root = Json.parseToJsonElement(Fixtures.adsIapTemplate).jsonObject
        root.getValue("parameterGroups").jsonObject.mapValues { (_, group) ->
            group.jsonObject.getValue("parameters").jsonObject.mapValues { (_, parameter) ->
                val p = parameter.jsonObject
                val ios = (p["conditionalValues"] as? JsonObject)?.get("platform_ios") as? JsonObject
                (ios ?: p.getValue("defaultValue").jsonObject).getValue("value").jsonPrimitive.content
            }
        }
    }

    val iosValues: Map<String, String> by lazy { iosValuesByGroup.values.fold(emptyMap()) { a, b -> a + b } }
}
