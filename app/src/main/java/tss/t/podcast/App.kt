package tss.t.podcast

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.messaging
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tss.t.ads.ApplovinSdkWrapper
import tss.t.core.CoreApp
import tss.t.core.storage.SharedPref
import tss.t.core.storage.getOrCreateInstallId
import tss.t.core.usecase.PruneApiCache
import tss.t.sharedlibrary.crash.Crash
import tss.t.sharedlibrary.crash.CrashReporter
import tss.t.sharedlibrary.crash.safeCall
import tss.t.sharedlibrary.utils.ConfigAPI
import tss.t.sharedplayer.controller.TSMediaController
import javax.inject.Inject

@HiltAndroidApp
class App : CoreApp() {

    @Inject
    lateinit var mediaController: TSMediaController
    @Inject
    lateinit var applovinSdkWrapper: ApplovinSdkWrapper
    @Inject
    lateinit var remoteConfig: ConfigAPI
    @Inject
    lateinit var crashReporter: CrashReporter
    @Inject
    lateinit var sharedPref: SharedPref
    @Inject
    lateinit var pruneApiCache: PruneApiCache

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
        // Installed before anything else so that failures during the rest of
        // startup are reported rather than lost to the logcat-only fallback.
        Crash.install(crashReporter)
        safeCall(TAG_STARTUP) {
            Crash.userId(sharedPref.getOrCreateInstallId())
        }
        Firebase.messaging
            .token
            .addOnSuccessListener {
            }
            .addOnFailureListener {

            }
            .addOnCanceledListener {

            }
        registerActivityLifecycleCallbacks(mediaController)
        applovinSdkWrapper.initSdk()
        // Expire stale API rows once per launch; see PruneApiCache for why.
        CoroutineScope(Dispatchers.IO).launch { pruneApiCache() }
    }

    companion object {
        lateinit var instance: App
        private const val TAG_STARTUP = "App.onCreate"
    }
}
