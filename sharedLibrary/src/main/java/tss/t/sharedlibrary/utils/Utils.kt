package tss.t.sharedlibrary.utils

import android.content.Context
import android.provider.Settings

fun Context.getAndroidDeviceId(): String? {
    return runCatching {
        Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }.getOrNull()
}