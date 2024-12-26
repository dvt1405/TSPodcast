package tss.t.sharedfirebase

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import tss.t.sharedlibrary.utils.ConfigAPI
import tss.t.sharedlibrary.utils.LocalRemoteConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

val LocalRemoteConfigScope = staticCompositionLocalOf<TSFirebaseRemoteConfig> {
    throw IllegalStateException("")
}

@Singleton
class TSFirebaseRemoteConfig @Inject constructor(
    @ApplicationContext
    context: Context,
    private val sharedPref: TSFirebaseSharedPref,
    @FirebaseScope(FirebaseDispatcher.IO)
    private val _coroutineScope: CoroutineScope
) : ConfigAPI {
    private val isActive by lazy { AtomicBoolean(false) }
    private val _remoteConfig by lazy {
        Firebase.remoteConfig
    }
    init {
        instance = this
        setup()
        LocalRemoteConfig provides this
    }

    private fun setup() {
        _coroutineScope.launch {
            _remoteConfig.fetchAndActivate()
            var failCount = 0
            while (!isActive.get() && failCount <= 20) {
                val active = suspendCancellableCoroutine<Boolean> { cont ->
                    _remoteConfig.fetchAndActivate()
                        .addOnCanceledListener {
                            failCount++
                            cont.resume(false)
                        }
                        .addOnFailureListener {
                            failCount++
                            cont.resume(false)
                        }
                        .addOnSuccessListener {
                            cont.resume(it)
                        }
                }
                isActive.set(active)
                if (!active) {
                    delay(failCount * 30_000L)
                }
            }
        }
    }

    override fun getJSONObject(key: String, defValue: JSONObject?): JSONObject? {
        return safeGet(defValue) {
            JSONObject(_remoteConfig.getString(key))
        }
    }

    override fun getJSONArray(key: String, defValue: JSONArray?): JSONArray? {
        return safeGet(defValue) {
            JSONArray(_remoteConfig.getString(key))
        }
    }


    override fun getString(key: String, defValue: String?): String? {
        return safeGet(defValue) {
            _remoteConfig.getString(key)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return safeGet(defValue) {
            _remoteConfig.getBoolean(key)
        } ?: defValue
    }

    override fun getLong(key: String, defValue: Long?): Long? {
        return safeGet(defValue) {
            _remoteConfig.getLong(key)
        }
    }

    override fun getDouble(key: String, defValue: Double?): Double? {
        return safeGet(defValue) {
            _remoteConfig.getDouble(key)
        }
    }

    private inline fun <T> safeGet(defValue: T? = null, block: () -> T) = runCatching {
        ensureActive()
        block()
    }.getOrDefault(defValue)

    private fun ensureActive() {
        _remoteConfig.ensureInitialized().addOnCanceledListener {
            setup()
        }.addOnFailureListener {
            setup()
        }
    }

    companion object {
        lateinit var instance: TSFirebaseRemoteConfig
            private set
    }
}