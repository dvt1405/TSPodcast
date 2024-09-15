package tss.t.hazeandroid

import android.util.Log

internal const val LOG_ENABLED = false

internal fun log(tag: String, message: () -> String) {
    if (LOG_ENABLED && Log.isLoggable(tag, Log.DEBUG)) {
        Log.d(tag, message())
    }
}
