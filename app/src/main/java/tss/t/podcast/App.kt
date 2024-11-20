package tss.t.podcast

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import dagger.hilt.android.HiltAndroidApp
import tss.t.core.CoreApp
import tss.t.sharedplayer.controller.TSMediaController
import javax.inject.Inject

@HiltAndroidApp
class App : CoreApp() {

    @Inject
    lateinit var mediaController: TSMediaController

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Firebase.messaging
            .token
            .addOnSuccessListener {
                Log.d("TuanDv", "onCreate: $it")
            }
        registerActivityLifecycleCallbacks(mediaController)
    }
}