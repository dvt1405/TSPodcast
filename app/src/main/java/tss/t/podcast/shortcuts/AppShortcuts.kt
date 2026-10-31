package tss.t.podcast.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import tss.t.podcast.MainActivity
import tss.t.podcast.R

/**
 * Launcher shortcuts (long-press the app icon), parity with iOS App Shortcuts (M6 T5):
 * continue listening, pause, and a 30-minute sleep timer. Published as dynamic shortcuts so the
 * intents always target this build's package.
 */
object AppShortcuts {
    const val ACTION_CONTINUE = "tss.t.podcast.action.CONTINUE_LISTENING"
    const val ACTION_PAUSE = "tss.t.podcast.action.PAUSE"
    const val ACTION_SLEEP_30 = "tss.t.podcast.action.SLEEP_TIMER_30"

    fun publish(context: Context) {
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut(context, "continue", ACTION_CONTINUE, R.string.shortcut_continue_short, R.string.shortcut_continue_long, android.R.drawable.ic_media_play),
                    shortcut(context, "pause", ACTION_PAUSE, R.string.shortcut_pause_short, R.string.shortcut_pause_long, android.R.drawable.ic_media_pause),
                    shortcut(context, "sleep30", ACTION_SLEEP_30, R.string.shortcut_sleep_short, R.string.shortcut_sleep_long, android.R.drawable.ic_lock_idle_alarm),
                )
            )
        }
    }

    private fun shortcut(
        context: Context,
        id: String,
        action: String,
        shortLabel: Int,
        longLabel: Int,
        icon: Int,
    ) = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(context.getString(shortLabel))
        .setLongLabel(context.getString(longLabel))
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        .build()
}
