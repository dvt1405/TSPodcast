package tss.t.sharedfirebase

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import tss.t.sharedlibrary.utils.getAndroidDeviceId
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

    internal var androidId: String? = _firebaseSharedPref.getString("DeviceId", null)
        get() {
            return if (field != null) field
            else _firebaseSharedPref.getString("DeviceId", null)
        }
        private set(value) {
            _firebaseSharedPref.edit {
                putString("DeviceId", field)
            }
            field = value
        }

    init {
        initDefaultAttrs()
    }

    private fun initDefaultAttrs() {
        if (androidId == null) {
            context.getAndroidDeviceId()
                .takeIf { !it.isNullOrEmpty() }
                ?.let {
                    androidId = it
                }
        }
    }
}