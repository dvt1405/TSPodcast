package tss.t.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplovinCoroutineScope @Inject constructor() {
    val mainDispatcher by lazy {
        CoroutineScope(Dispatchers.Main)
    }
    val ioDispatcher by lazy {
        CoroutineScope(Dispatchers.IO)
    }
}