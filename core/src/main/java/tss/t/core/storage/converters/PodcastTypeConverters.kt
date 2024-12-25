package tss.t.core.storage.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tss.t.core.repository.MediaType
import tss.t.coreapi.models.Categories
import tss.t.coreapi.models.Funding
import tss.t.coreapi.models.Person
import tss.t.coreapi.models.PodcastTranscript
import tss.t.coreapi.models.SocialInteract
import tss.t.coreapi.models.Soundbite
import tss.t.coreapi.models.Value
import tss.t.coreradio.models.RadioChannel

class PodcastTypeConverters {
    private val gson by lazy { Gson() }

    @TypeConverter
    fun categoriesToString(categories: Categories?): String? {
        return runCatching {
            gson.toJson(categories)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToCategories(string: String?): Categories? {
        return kotlin.runCatching {
            gson.fromJson(string, Categories::class.java)
        }.getOrNull()
    }

    @TypeConverter
    fun fundingToString(funding: Funding?): String? {
        return runCatching {
            gson.toJson(funding)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToFunding(string: String?): Funding? {
        return runCatching {
            gson.fromJson(string, Funding::class.java)
        }.getOrNull()
    }

    @TypeConverter
    fun valueToString(funding: Value?): String? {
        return runCatching {
            gson.toJson(funding)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToValue(string: String?): Value? {
        return runCatching {
            gson.fromJson(string, Value::class.java)
        }.getOrNull()
    }

    @TypeConverter
    fun podcastTranscriptToString(list: List<PodcastTranscript>?): String? {
        return runCatching {
            gson.toJson(list)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToPodcastTranscript(string: String?): List<PodcastTranscript>? {
        return runCatching {
            gson.fromJson(
                string,
                object : TypeToken<List<PodcastTranscript>?>() {}
            )
        }.getOrNull()
    }

    @TypeConverter
    fun personToString(list: List<Person>?): String? {
        return runCatching {
            gson.toJson(list)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToPerson(string: String?): List<Person>? {
        return runCatching {
            gson.fromJson(
                string,
                object : TypeToken<List<Person>?>() {}
            )
        }.getOrNull()
    }

    @TypeConverter
    fun soundbitesToString(list: List<Soundbite>?): String? {
        return runCatching {
            gson.toJson(list)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToSoundbites(string: String?): List<Soundbite>? {
        return runCatching {
            gson.fromJson(
                string,
                object : TypeToken<List<Soundbite>?>() {}
            )
        }.getOrNull()
    }


    @TypeConverter
    fun soundbiteToString(list: Soundbite?): String? {
        return runCatching {
            gson.toJson(list)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToSoundbite(string: String?): Soundbite? {
        return runCatching {
            gson.fromJson(
                string,
                Soundbite::class.java
            )
        }.getOrNull()
    }

    @TypeConverter
    fun socialInteractToString(list: List<SocialInteract>?): String? {
        return runCatching {
            gson.toJson(list)
        }.getOrNull()
    }

    @TypeConverter
    fun stringToSocialInteract(string: String?): List<SocialInteract>? {
        return runCatching {
            gson.fromJson(
                string,
                object : TypeToken<List<SocialInteract>?>() {}
            )
        }.getOrNull()
    }

    @TypeConverter
    fun mediaTypeToString(mediaType: MediaType): String {
        return runCatching {
            mediaType.name
        }.getOrDefault("")
    }

    @TypeConverter
    fun stringToRadioLink(string: String): List<RadioChannel.Link> {
        return runCatching {
            gson.fromJson(
                string,
                radoLinkListType
            )
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun radioLinkToString(links: List<RadioChannel.Link>): String {
        return runCatching {
            gson.toJson(links)
        }.getOrDefault("")
    }

    @TypeConverter
    fun stringToListString(string: String): List<String> {
        return runCatching {
            gson.fromJson(string, listStringType)
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun listStringToString(list: List<String>): String {
        return runCatching {
            gson.toJson(list)
        }.getOrDefault("")
    }

    companion object {
        private val radoLinkListType by lazy {
            object : TypeToken<List<RadioChannel.Link>>() {}
        }

        private val listStringType by lazy {
            object : TypeToken<List<String>>() {}
        }
    }
}