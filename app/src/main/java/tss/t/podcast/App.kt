package tss.t.podcast

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
        registerActivityLifecycleCallbacks(mediaController)
    }
}