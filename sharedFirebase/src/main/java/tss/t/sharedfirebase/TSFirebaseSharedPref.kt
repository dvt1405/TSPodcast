package tss.t.sharedfirebase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class TSFirebaseSharedPref @Inject constructor(
    @ApplicationContext
    val context: Context,
    @FirebaseScope(FirebaseDispatcher.IO)
    private val _coroutineScope: CoroutineScope,
) {
    private val _firebaseSharedPref by lazy {
        context.getSharedPreferences("firebase_shared_prefs", Context.MODE_PRIVATE)
    }

    init {
        // ANDROID_ID is no longer read or sent; drop the copy older versions stored.
        _firebaseSharedPref.edit { remove("DeviceId") }
    }
}