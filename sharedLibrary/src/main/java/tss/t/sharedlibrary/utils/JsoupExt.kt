package tss.t.sharedlibrary.utils

import android.app.Application
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupExt @Inject constructor(
    private val context: Application
) {
    private val gson by lazy { Gson() }
    private val _cookieStore by lazy {
        EncryptedSharedPreferences.create(
            TAG,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        instance = this
    }

    suspend fun connect(
        url: String,
        cookieReferer: String = url,
        headers: Map<String, String> = emptyMap(),
    ): Document = withContext(Dispatchers.IO) {
        val cookieKey = runCatching {
            Uri.parse(cookieReferer).host
        }.getOrDefault(url)!!
        val oldCookie = getCookie(cookieKey)
        val doc = Jsoup.connect(url)
            .headers(headers)
            .cookies(oldCookie)
            .followRedirects(true)
            .execute()
        saveCookie(cookieKey, doc.cookies())
        return@withContext doc.parse()
    }

    suspend fun safeConnect(
        url: String,
        cookieReferer: String = url,
        headers: Map<String, String> = emptyMap(),
    ) = runCatching {
        connect(url, cookieReferer, headers)
    }.getOrNull()

    private fun getCookie(key: String): Map<String, String> {
        val str = _cookieStore.getString(key, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, String>>(str, mapType)
        }.getOrDefault(emptyMap())
    }

    private fun saveCookie(key: String, value: Map<String, String>) {
        _cookieStore.edit()
            .putString(key, gson.toJson(value))
            .apply()
    }

    fun getCookieForUrl(url: String): Map<String, String> {
        val key = runCatching {
            Uri.parse(url).host
        }.getOrDefault(url) ?: return emptyMap()
        return getCookie(key)
    }

    private val mapType = object : TypeToken<Map<String, String>>() {
    }.type

    companion object {
        private const val TAG = "JsoupExt"
        lateinit var instance: JsoupExt
            private set

        fun initialize(context: Application) = JsoupExt(context)
    }
}