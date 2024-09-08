package tss.t.coreapi.converter

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import tss.t.coreapi.models.Categories
import java.lang.reflect.Type


class MapStringStringJsonConverter : JsonDeserializer<Categories> {
    private val key = "categories"
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Categories {
        val jsObject = json!!.asJsonObject.getAsJsonObject(key)
        return Gson().fromJson(jsObject, Categories::class.java)
    }
}