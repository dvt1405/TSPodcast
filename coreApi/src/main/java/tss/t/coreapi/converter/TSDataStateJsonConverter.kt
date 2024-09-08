package tss.t.coreapi.converter

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonToken
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import tss.t.coreapi.models.TSDataState
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class TSDataStateJsonConverter<T: Any>(gson: Gson, adapter: TypeAdapter<out T>) : Converter<ResponseBody, TSDataState<T>> {
    private var gson: Gson? = gson
    private var adapter: TypeAdapter<out T>? = adapter

    @Throws(IOException::class)
    override fun convert(value: ResponseBody): TSDataState<T> {
        val jsonReader = gson!!.newJsonReader(value.charStream())
        try {
            val result: T = adapter!!.read(jsonReader)
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw JsonIOException("JSON document was not fully consumed.")
            }
            return TSDataState.Success(result)
        } finally {
            value.close()
        }
    }

    class Factory(private val gson: Gson = Gson()) : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type?, annotations: Array<Annotation?>?, retrofit: Retrofit?
        ): Converter<ResponseBody, *> {
             val rawType = getParameterUpperBound(0, type as ParameterizedType)
            val adapter: TypeAdapter<out Any> = gson.getAdapter(TypeToken.get(rawType))
            return TSDataStateJsonConverter(gson, adapter)
        }

        override fun requestBodyConverter(
            type: Type?,
            parameterAnnotations: Array<Annotation?>?,
            methodAnnotations: Array<Annotation?>?,
            retrofit: Retrofit?
        ): Converter<*, RequestBody> {
            throw IllegalStateException("Test")
        }
    }
}
