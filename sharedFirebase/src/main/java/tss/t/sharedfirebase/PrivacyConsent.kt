package tss.t.sharedfirebase

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the user agreed to: usage analytics and crash reports, each on its own. */
data class PrivacyChoices(
    /** False until the user answered the first-launch question for [PrivacyConsent.CURRENT_VERSION]. */
    val decided: Boolean = false,
    val analytics: Boolean = false,
    val crashReports: Boolean = false,
)

/**
 * Privacy consent (parity with iOS M6 N11 lite, Vietnam PDPL 91/2025): Firebase Analytics and
 * Crashlytics are off in the manifest and switched on only after the user agrees, either on the
 * first-launch sheet or later in Settings. Remote Config (content block list) and the install id
 * are necessary for the app and do not depend on it.
 *
 * Bump [CURRENT_VERSION] when the consent text changes to ask again.
 */
@Singleton
class PrivacyConsent @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("privacy_consent", Context.MODE_PRIVATE)

    private val _choices = MutableStateFlow(read())
    val choices: StateFlow<PrivacyChoices> = _choices.asStateFlow()

    /** Applies the saved choices to Firebase; called once from `Application.onCreate`. */
    fun applySaved() = apply(_choices.value)

    fun acceptAll() = save(PrivacyChoices(decided = true, analytics = true, crashReports = true))

    fun necessaryOnly() = save(PrivacyChoices(decided = true, analytics = false, crashReports = false))

    fun setAnalytics(enabled: Boolean) = save(_choices.value.copy(decided = true, analytics = enabled))

    fun setCrashReports(enabled: Boolean) = save(_choices.value.copy(decided = true, crashReports = enabled))

    private fun save(choices: PrivacyChoices) {
        prefs.edit {
            putInt(KEY_VERSION, if (choices.decided) CURRENT_VERSION else 0)
            putBoolean(KEY_ANALYTICS, choices.analytics)
            putBoolean(KEY_CRASH, choices.crashReports)
        }
        _choices.value = choices
        apply(choices)
    }

    private fun read() = PrivacyChoices(
        decided = prefs.getInt(KEY_VERSION, 0) >= CURRENT_VERSION,
        analytics = prefs.getBoolean(KEY_ANALYTICS, false),
        crashReports = prefs.getBoolean(KEY_CRASH, false),
    )

    private fun apply(choices: PrivacyChoices) {
        runCatching { Firebase.analytics.setAnalyticsCollectionEnabled(choices.analytics) }
        runCatching { FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = choices.crashReports }
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_ANALYTICS = "analytics"
        private const val KEY_CRASH = "crash_reports"
    }
}
