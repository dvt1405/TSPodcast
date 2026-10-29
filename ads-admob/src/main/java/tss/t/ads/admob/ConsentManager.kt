package tss.t.ads.admob

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google UMP (User Messaging Platform) wrapper.
 *
 * Consent info is refreshed on every app start, as Google requires. The form
 * only appears where a message is configured in AdMob → Privacy & messaging
 * (EEA/UK/CH GDPR, regulated US states). Debug builds force a debug geography
 * (EEA by default, `UMP_DEBUG_GEOGRAPHY` Gradle property) on any device so the
 * form can be checked without a VPN.
 *
 * UMP calls its listeners on the main thread.
 */
@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val info: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    val canRequestAds: Boolean
        get() = info.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    val consentStatus: String
        get() = when (info.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> "not_required"
            ConsentInformation.ConsentStatus.REQUIRED -> "required"
            ConsentInformation.ConsentStatus.OBTAINED -> "obtained"
            else -> "unknown"
        }

    /**
     * Requests a consent info update and shows the form if one is required.
     * [onDone] is called once, on the main thread, with the error if any. On a
     * network failure UMP keeps the consent state from the previous session.
     */
    fun gather(activity: Activity, onDone: (FormError?) -> Unit) {
        val params = ConsentRequestParameters.Builder().apply {
            if (BuildConfig.DEBUG) debugSettings(activity)?.let(::setConsentDebugSettings)
        }.build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    onDone(error)
                }
            },
            { error -> onDone(error) },
        )
    }

    fun showPrivacyOptions(activity: Activity, onDone: (FormError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> onDone(error) }
    }

    private fun debugSettings(activity: Activity): ConsentDebugSettings? {
        val geography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY.uppercase()) {
            "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            "US" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
            "OTHER" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
            else -> return null
        }
        return ConsentDebugSettings.Builder(activity)
            .setDebugGeography(geography)
            // Apply the debug geography on any dev device, not only on devices
            // whose hashed id is listed below (debug builds only).
            .setForceTesting(true)
            .apply {
                BuildConfig.UMP_TEST_DEVICE_HASHED_IDS.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach(::addTestDeviceHashedId)
            }
            .build()
    }
}
