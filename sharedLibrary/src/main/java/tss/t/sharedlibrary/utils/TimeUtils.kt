package tss.t.sharedlibrary.utils

import android.annotation.SuppressLint

@SuppressLint("DefaultLocale")
fun Long.formatDuration(): String {
    val seconds = this / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return String.format("%02d:%02d", hours, minutes)
}
