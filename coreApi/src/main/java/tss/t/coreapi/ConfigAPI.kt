package tss.t.coreapi

import android.util.Log
import com.google.gson.Gson

interface ConfigAPI {
    val gson: Gson
    fun getString(key: String, defValue: String? = null): String?
    fun getInt(key: String, defValue: Int?): Int?
    fun getBoolean(key: String, defValue: Boolean?): Boolean?
    fun getLong(key: String, defValue: Long?): Long?
    fun getDouble(key: String, defValue: Double?): Double?
}

inline fun <reified T> ConfigAPI.getConfig(key: String, defValue: T?): T? {
    val jsonStr = getString(key, null)
    if (jsonStr.isNullOrEmpty()) return defValue
    return runCatching {
        gson.fromJson(jsonStr, T::class.java)
    }.onFailure {
        Log.d("TuanDv", "getConfig: {key: $key, error: ${it.message}}", it)
    }.getOrNull()
}