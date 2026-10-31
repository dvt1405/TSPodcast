package tss.t.shared.model

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * PodcastIndex categories of a feed: category id (as a string) to category name,
 * e.g. `{"1":"Arts","55":"News"}`.
 */
typealias Categories = Map<String, String>

/**
 * Reads the `categories` field of a feed or episode.
 *
 * The API sends an object, but a feed without categories comes back as an empty
 * JSON array (`"categories": []`, PHP's encoding of an empty map), which a plain map
 * serializer rejects. An array is read as index to name. Values that are not strings
 * (numbers) are kept as their text.
 *
 * Android's Gson `MapStringStringJsonConverter` is deliberately not ported: it calls
 * `getAsJsonObject("categories")` on the field value itself and so always returns null.
 */
object CategoriesSerializer : JsonTransformingSerializer<Categories>(
    MapSerializer(String.serializer(), String.serializer())
) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonObject(
            element.withIndex()
                .filter { (_, value) -> value is JsonPrimitive && value !is JsonNull }
                .associate { (index, value) -> index.toString() to value.asText() }
        )

        is JsonObject -> JsonObject(
            element
                .filterValues { it is JsonPrimitive && it !is JsonNull }
                .mapValues { (_, value) -> value.asText() }
        )

        else -> JsonObject(emptyMap())
    }

    private fun JsonElement.asText(): JsonPrimitive = JsonPrimitive((this as JsonPrimitive).content)
}
