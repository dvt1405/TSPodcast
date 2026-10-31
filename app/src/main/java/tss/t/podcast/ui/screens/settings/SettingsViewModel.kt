package tss.t.podcast.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import tss.t.core.storage.ContentPrefs
import tss.t.core.storage.HiddenShow
import tss.t.sharedfirebase.PrivacyChoices
import tss.t.sharedfirebase.PrivacyConsent
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val contentPrefs: ContentPrefs,
    private val privacyConsent: PrivacyConsent,
) : ViewModel() {

    val allowExplicit: StateFlow<Boolean> = contentPrefs.allowExplicit
    val hiddenShows: StateFlow<List<HiddenShow>> = contentPrefs.hiddenShows
    val privacy: StateFlow<PrivacyChoices> = privacyConsent.choices

    fun setAllowExplicit(allow: Boolean) = contentPrefs.setAllowExplicit(allow)

    fun unhide(feedId: Long) = contentPrefs.unhide(feedId)

    fun setAnalytics(enabled: Boolean) = privacyConsent.setAnalytics(enabled)

    fun setCrashReports(enabled: Boolean) = privacyConsent.setCrashReports(enabled)
}
