package tss.t.shared.privacy

import tss.t.shared.preferences.KeyValueStore
import tss.t.shared.util.EpochClock

/**
 * What the user chose in the privacy sheet (M6 §3.6, N11 lite).
 *
 * @property version the consent text version the choice was made for; 0 when never asked.
 * @property decidedAtMillis when the choice was saved (epoch ms), null when never asked.
 * @property analytics usage analytics allowed; always false while [needsPrompt].
 * @property crash crash reports allowed; always false while [needsPrompt].
 * @property needsPrompt never asked, or asked for an older text: show the sheet again.
 */
data class ConsentState(
    val version: Int,
    val decidedAtMillis: Long?,
    val analytics: Boolean,
    val crash: Boolean,
    val needsPrompt: Boolean,
)

/**
 * Consent to optional data collection, in the app's key-value store. "Cần thiết" (running the
 * app, Remote Config for the spam block list, the random install id) is not a choice and is not
 * stored here; usage analytics and crash reports are, both off until the user turns them on.
 *
 * Raising [CURRENT_VERSION] (a new consent text) asks again: until then a choice made for an
 * older version counts as "both off". Every decision stores the current version and the time
 * from [clock], so the app can show when the user agreed.
 */
class ConsentRepository(
    private val store: KeyValueStore,
    private val clock: EpochClock,
) {
    suspend fun state(): ConsentState {
        val version = store.getLong(KEY_VERSION)?.toInt() ?: 0
        val decidedAt = store.getLong(KEY_DECIDED_AT)
        val needsPrompt = decidedAt == null || version < CURRENT_VERSION
        return ConsentState(
            version = version,
            decidedAtMillis = decidedAt,
            analytics = !needsPrompt && store.getBoolean(KEY_ANALYTICS) == true,
            crash = !needsPrompt && store.getBoolean(KEY_CRASH) == true,
            needsPrompt = needsPrompt,
        )
    }

    suspend fun needsPrompt(): Boolean = state().needsPrompt

    /** "Đồng ý tất cả". */
    suspend fun acceptAll(): ConsentState = update(analytics = true, crash = true)

    /** "Chỉ cần thiết". */
    suspend fun necessaryOnly(): ConsentState = update(analytics = false, crash = false)

    /** The sheet with its toggles, or the Settings toggles. */
    suspend fun update(analytics: Boolean, crash: Boolean): ConsentState {
        // Choices first, version and time last: a crash in between asks again on the next launch.
        store.putBoolean(KEY_ANALYTICS, analytics)
        store.putBoolean(KEY_CRASH, crash)
        store.putLong(KEY_DECIDED_AT, clock.nowMillis())
        store.putLong(KEY_VERSION, CURRENT_VERSION.toLong())
        return state()
    }

    companion object {
        /** Version of the consent text; raise it to ask everyone again. */
        const val CURRENT_VERSION = 1

        const val KEY_VERSION = "consent.version"
        const val KEY_DECIDED_AT = "consent.decidedAt"
        const val KEY_ANALYTICS = "consent.analytics"
        const val KEY_CRASH = "consent.crash"
    }
}
