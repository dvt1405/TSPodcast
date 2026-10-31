package tss.t.podcast

import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import tss.t.sharedfirebase.PrivacyConsent
import com.google.firebase.messaging.messaging
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tss.t.core.CoreApp
import tss.t.core.storage.SharedPref
import tss.t.core.storage.getOrCreateInstallId
import tss.t.core.usecase.PruneApiCache
import tss.t.sharedlibrary.crash.Crash
import tss.t.sharedlibrary.crash.CrashReporter
import tss.t.securedtoken.NativeLib
import tss.t.sharedlibrary.crash.safeCall
import tss.t.sharedlibrary.crash.safeCallOrNull
import tss.t.sharedlibrary.utils.ConfigAPI
import tss.t.sharedplayer.controller.TSMediaController
import javax.inject.Inject

@HiltAndroidApp
class App : CoreApp() {

    @Inject
    lateinit var mediaController: TSMediaController
    @Inject
    lateinit var remoteConfig: ConfigAPI
    @Inject
    lateinit var crashReporter: CrashReporter
    @Inject
    lateinit var sharedPref: SharedPref
    @Inject
    lateinit var pruneApiCache: PruneApiCache
    @Inject
    lateinit var privacyConsent: PrivacyConsent

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
        // Analytics and Crashlytics are off in the manifest; turn them on only if the user agreed.
        privacyConsent.applySaved()
        // Installed before anything else so that failures during the rest of
        // startup are reported rather than lost to the logcat-only fallback.
        Crash.install(crashReporter)
        safeCall(TAG_STARTUP) {
            Crash.userId(sharedPref.getOrCreateInstallId())
        }
        NativeLib.loadError?.let(::reportMissingNativeLib)
        Firebase.messaging
            .token
            .addOnSuccessListener {
            }
            .addOnFailureListener {

            }
            .addOnCanceledListener {

            }
        registerActivityLifecycleCallbacks(mediaController)
        // Ads start from MainActivity (AdsController.start): UMP consent needs an
        // Activity, and the SDK must not initialise before consent is known.
        // Expire stale API rows once per launch; see PruneApiCache for why.
        CoroutineScope(Dispatchers.IO).launch { pruneApiCache() }
    }

    /**
     * libsecuredtoken.so is missing, which happens when the base APK was
     * installed without its ABI split. Recorded as a non-fatal with what the
     * install actually contains, so the console can tell a sideload apart from
     * a packaging regression. MainActivity shows a reinstall prompt instead of
     * the app, since every API call needs the library.
     */
    private fun reportMissingNativeLib(error: UnsatisfiedLinkError) {
        val splitNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationInfo.splitNames?.joinToString().orEmpty()
        } else {
            "unknown"
        }
        val installer = safeCallOrNull(TAG_NATIVE_LIB) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        }
        Crash.record(
            error,
            TAG_NATIVE_LIB,
            mapOf(
                "split_names" to splitNames,
                "installer" to (installer ?: "none"),
                "supported_abis" to Build.SUPPORTED_ABIS.joinToString()
            )
        )
    }

    companion object {
        lateinit var instance: App
        private const val TAG_STARTUP = "App.onCreate"
        private const val TAG_NATIVE_LIB = "App.missingNativeLib"
    }
}
