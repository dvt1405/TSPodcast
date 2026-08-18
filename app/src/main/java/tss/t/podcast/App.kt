package tss.t.podcast

import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import dagger.hilt.android.HiltAndroidApp
import tss.t.ads.ApplovinSdkWrapper
import tss.t.core.CoreApp
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
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
    }

    companion object {
        lateinit var instance: App
    }
}