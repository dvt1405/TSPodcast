package tss.t.sharedfirebase

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.compositionLocalOf
import androidx.core.os.bundleOf
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tss.t.sharedlibrary.utils.getAndroidDeviceId
import javax.inject.Inject
import javax.inject.Singleton

val LocalAnalyticsScope = compositionLocalOf<TSAnalytics?> { null }

@Singleton
class TSAnalytics @Inject constructor(
    @ApplicationContext
    val context: Context
) {
    private val _analyticScope by lazy { CoroutineScope(Dispatchers.IO) }
    private val _defaultAttrs by lazy {
        mutableMapOf<String, Any>(
            "os" to Build.VERSION.RELEASE,
            "os_version" to Build.VERSION.SDK_INT
        )
    }
    private val _firebaseSharedPref by lazy {
        EncryptedSharedPreferences.create(
            "firebase_shared_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private var androidId: String? = _firebaseSharedPref.getString("DeviceId", null)
        get() {
            return if (field != null) field
            else _firebaseSharedPref.getString("DeviceId", null)
        }
        set(value) {
            _firebaseSharedPref.edit()
                .putString("DeviceId", field)
                .apply()
            field = value
        }

    var currentScreenName: String? = null

    init {
        instance = this
        initDefaultAttrs()
        (context as? Application)?.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                }

                override fun onActivityStarted(activity: Activity) {
                }

                override fun onActivityResumed(activity: Activity) {
                    currentScreenName = activity::class.java.name
                }

                override fun onActivityPaused(activity: Activity) {
                }

                override fun onActivityStopped(activity: Activity) {
                    if (currentScreenName == activity::class.java.name) {
                        currentScreenName = null
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                }

                override fun onActivityDestroyed(activity: Activity) {
                }
            })
    }

    private fun initDefaultAttrs() {
        if (androidId == null) {
            context.getAndroidDeviceId()
                .takeIf { !it.isNullOrEmpty() }
                ?.let {
                    androidId = it
                }
        }
        androidId?.let {
            _defaultAttrs["device_id"] = it
        }
        _analyticScope.launch {
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                _defaultAttrs["app_version"] = pInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    fun trackScreen(
        screenName: String,
        vararg prop: Pair<String, Any>
    ) {
        _analyticScope.launch {
            Firebase.analytics.logEvent(
                "${screenName.lowercase()}_screen",
                bundleOf(*_defaultAttrs.toList().toTypedArray()).apply {
                    putAll(bundleOf(*prop))
                    putLong("event_time_stamp", System.currentTimeMillis() / 1000)
                }
            )
        }
    }

    fun trackEvent(
        eventName: String,
        screenName: String? = currentScreenName,
        vararg prop: Pair<String, Any?>
    ) {
        _analyticScope.launch {
            Firebase.analytics.logEvent(
                eventName,
                bundleOf(*_defaultAttrs.toList().toTypedArray()).apply {
                    putAll(bundleOf(*prop.filter { it.second != null }.toTypedArray()))
                    putLong("event_time_stamp", System.currentTimeMillis() / 1000)
                    putString("screen", screenName)
                }
            )
        }
    }

    companion object {
        var instance: TSAnalytics? = null
            private set
    }
}