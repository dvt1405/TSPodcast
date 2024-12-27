package tss.t.sharedlibrary.utils

import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONArray
import org.json.JSONObject

val LocalRemoteConfig = staticCompositionLocalOf<ConfigAPI> {
    throw IllegalStateException("No provider for ConfigAPI")
}

interface ConfigAPI {
    fun getString(key: String, defValue: String? = null): String?
    fun getBoolean(key: String, defValue: Boolean = false): Boolean
    fun getLong(key: String, defValue: Long? = null): Long?
    fun getDouble(key: String, defValue: Double? = null): Double?
    fun getJSONArray(key: String, defValue: JSONArray? = null): JSONArray?
    fun getJSONObject(key: String, defValue: JSONObject? = null): JSONObject?
}