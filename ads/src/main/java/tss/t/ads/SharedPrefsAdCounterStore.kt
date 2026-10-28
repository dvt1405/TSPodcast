package tss.t.ads

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AdCounterStore] on a private SharedPreferences file. Counters survive
 * process death (the AppLovin version kept its 10-minute throttle in memory,
 * so every cold start reset it). Session counts are per process.
 *
 * All access is synchronized: full-screen callbacks arrive on SDK background
 * threads while policy checks run on the main thread.
 */
@Singleton
class SharedPrefsAdCounterStore @Inject constructor(
    @ApplicationContext context: Context,
) : AdCounterStore {
    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }
    private val sessionShows = mutableMapOf<AdPlacement, Int>()
    private var sessionStarted = false

    @Synchronized
    override fun installAt(now: Long): Long {
        val stored = prefs.getLong(KEY_INSTALL_AT, 0L)
        if (stored > 0L) return stored
        prefs.edit { putLong(KEY_INSTALL_AT, now) }
        return now
    }

    override val sessionCount: Int
        @Synchronized get() = prefs.getInt(KEY_SESSIONS, 0)

    override val lastFullScreenAt: Long
        @Synchronized get() = prefs.getLong(KEY_LAST_FULL_SCREEN, 0L)

    override val adFreeUntil: Long
        @Synchronized get() = prefs.getLong(KEY_AD_FREE_UNTIL, 0L)

    @Synchronized
    override fun lastShownAt(placement: AdPlacement): Long =
        prefs.getLong("last_${placement.key}", 0L)

    @Synchronized
    override fun shownOnDay(placement: AdPlacement, day: Long): Int =
        if (prefs.getLong("day_${placement.key}", -1L) == day) {
            prefs.getInt("count_${placement.key}", 0)
        } else {
            0
        }

    @Synchronized
    override fun shownThisSession(placement: AdPlacement): Int = sessionShows[placement] ?: 0

    @Synchronized
    override fun recordShown(placement: AdPlacement, now: Long, day: Long) {
        val today = shownOnDay(placement, day)
        sessionShows[placement] = (sessionShows[placement] ?: 0) + 1
        prefs.edit {
            putLong("last_${placement.key}", now)
            putLong("day_${placement.key}", day)
            putInt("count_${placement.key}", today + 1)
            if (placement.isFullScreen) putLong(KEY_LAST_FULL_SCREEN, now)
        }
    }

    /** Counts one session per process, however often it is called. */
    @Synchronized
    override fun startSession() {
        if (sessionStarted) return
        sessionStarted = true
        prefs.edit { putInt(KEY_SESSIONS, prefs.getInt(KEY_SESSIONS, 0) + 1) }
    }

    @Synchronized
    override fun grantAdFreeUntil(until: Long) {
        prefs.edit { putLong(KEY_AD_FREE_UNTIL, until) }
    }

    private companion object {
        const val FILE = "tss_ads_counters"
        const val KEY_INSTALL_AT = "install_at"
        const val KEY_SESSIONS = "sessions"
        const val KEY_LAST_FULL_SCREEN = "last_full_screen"
        const val KEY_AD_FREE_UNTIL = "ad_free_until"
    }
}
