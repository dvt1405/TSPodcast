package tss.t.sharedlibrary.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tss.t.sharedlibrary.crash.safeCallOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupExt @Inject constructor(
    private val context: Application
) {
    private val gson by lazy { Gson() }
    /**
     * Plain SharedPreferences, not EncryptedSharedPreferences.
     *
     * The encrypted store was backed up by auto-backup and then restored onto a
     * device whose keystore could not decrypt it, throwing AEADBadTagException on
     * first access. Commit a42046b removed the same API from core/SharedPref.kt
     * for exactly that reason; this store was missed. Cookies are not secrets,
     * and the store is excluded from backup in res/xml/backup_rules.xml.
     */
    private val _cookieStore: SharedPreferences? by lazy {
        safeCallOrNull(TAG) {
            context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        }
    }

    init {
        instance = this
    }

    suspend fun connect(
        url: String,
        cookieReferer: String = url,
        headers: Map<String, String> = emptyMap(),
    ): Document = withContext(Dispatchers.IO) {
        val cookieKey = safeCallOrNull(TAG) { Uri.parse(cookieReferer).host } ?: url
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
        val store = _cookieStore ?: return emptyMap()
        val str = safeCallOrNull(TAG) { store.getString(key, null) } ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, String>>(str, mapType)
        }.getOrElse {
            // Unreadable entry (e.g. left over from the encrypted store).
            // Drop it so it cannot fail again.
            safeCallOrNull(TAG) { store.edit().remove(key).apply() }
            emptyMap()
        }
    }

    private fun saveCookie(key: String, value: Map<String, String>) {
        val store = _cookieStore ?: return
        safeCallOrNull(TAG) {
            store.edit()
                .putString(key, gson.toJson(value))
                .apply()
        }
    }

    fun getCookieForUrl(url: String): Map<String, String> {
        val key = safeCallOrNull(TAG) { Uri.parse(url).host } ?: return emptyMap()
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