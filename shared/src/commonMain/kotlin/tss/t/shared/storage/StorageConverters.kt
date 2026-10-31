package tss.t.shared.storage

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tss.t.shared.model.Funding
import tss.t.shared.model.Person
import tss.t.shared.model.PodcastTranscript
import tss.t.shared.model.SocialInteract
import tss.t.shared.model.Soundbite
import tss.t.shared.model.Value

/**
 * JSON columns (Android `PodcastTypeConverters`, Gson there, kotlinx.serialization here). Like
 * Android, a value that cannot be written or read becomes `null` instead of failing the query.
 * The enum column ([tss.t.shared.favourite.FavouriteType]) uses Room's built-in enum converter.
 */
class StorageConverters {
    @TypeConverter
    fun categoriesToText(value: Map<String, String>?): String? = encode(categoriesSerializer, value)

    @TypeConverter
    fun textToCategories(text: String?): Map<String, String>? = decode(categoriesSerializer, text)

    @TypeConverter
    fun fundingToText(value: Funding?): String? = encode(Funding.serializer(), value)

    @TypeConverter
    fun textToFunding(text: String?): Funding? = decode(Funding.serializer(), text)

    @TypeConverter
    fun valueToText(value: Value?): String? = encode(Value.serializer(), value)

    @TypeConverter
    fun textToValue(text: String?): Value? = decode(Value.serializer(), text)

    @TypeConverter
    fun personsToText(value: List<Person>?): String? = encode(personsSerializer, value)

    @TypeConverter
    fun textToPersons(text: String?): List<Person>? = decode(personsSerializer, text)

    @TypeConverter
    fun transcriptsToText(value: List<PodcastTranscript>?): String? = encode(transcriptsSerializer, value)

    @TypeConverter
    fun textToTranscripts(text: String?): List<PodcastTranscript>? = decode(transcriptsSerializer, text)

    @TypeConverter
    fun soundbiteToText(value: Soundbite?): String? = encode(Soundbite.serializer(), value)

    @TypeConverter
    fun textToSoundbite(text: String?): Soundbite? = decode(Soundbite.serializer(), text)

    @TypeConverter
    fun soundbitesToText(value: List<Soundbite>?): String? = encode(soundbitesSerializer, value)

    @TypeConverter
    fun textToSoundbites(text: String?): List<Soundbite>? = decode(soundbitesSerializer, text)

    @TypeConverter
    fun socialInteractToText(value: List<SocialInteract>?): String? = encode(socialInteractSerializer, value)

    @TypeConverter
    fun textToSocialInteract(text: String?): List<SocialInteract>? = decode(socialInteractSerializer, text)

    private fun <T> encode(serializer: KSerializer<T>, value: T?): String? =
        value?.let { runCatching { json.encodeToString(serializer, it) }.getOrNull() }

    private fun <T> decode(serializer: KSerializer<T>, text: String?): T? =
        text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    // Explicit types: KSP (Room) reads this class and cannot see the serializers that the
    // serialization plugin generates, so an inferred type would be a "missing type" to it.
    private companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val categoriesSerializer: KSerializer<Map<String, String>> =
            MapSerializer(String.serializer(), String.serializer())
        val personsSerializer: KSerializer<List<Person>> = ListSerializer(Person.serializer())
        val transcriptsSerializer: KSerializer<List<PodcastTranscript>> =
            ListSerializer(PodcastTranscript.serializer())
        val soundbitesSerializer: KSerializer<List<Soundbite>> = ListSerializer(Soundbite.serializer())
        val socialInteractSerializer: KSerializer<List<SocialInteract>> =
            ListSerializer(SocialInteract.serializer())
    }
}
