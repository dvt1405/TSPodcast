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
    private val uiPrefs: tss.t.core.storage.UiPrefs,
    private val languagePrefs: tss.t.core.storage.LanguagePrefs,
    private val locationPrefs: tss.t.core.storage.PodcastLocationPrefs,
) : ViewModel() {

    /** Podcast locations (priority order); Home reloads by itself when they change. */
    val locations: StateFlow<List<tss.t.core.storage.PodcastLocation>> = locationPrefs.locations

    fun setLocations(regions: List<String>) = locationPrefs.setLocations(regions)

    val language: StateFlow<tss.t.core.storage.AppLanguage> = languagePrefs.language
    val languages: List<tss.t.core.storage.AppLanguage> = tss.t.core.storage.LanguagePrefs.SUPPORTED

    /** App (UI) language; the caller then applies the UI locale. Content follows [locations]. */
    fun setLanguage(tag: String) = languagePrefs.setLanguage(tag)

    val liquidGlass: StateFlow<Boolean> = uiPrefs.liquidGlass

    fun setLiquidGlass(enabled: Boolean) = uiPrefs.setLiquidGlass(enabled)

    val allowExplicit: StateFlow<Boolean> = contentPrefs.allowExplicit
    val hiddenShows: StateFlow<List<HiddenShow>> = contentPrefs.hiddenShows
    val privacy: StateFlow<PrivacyChoices> = privacyConsent.choices

    fun setAllowExplicit(allow: Boolean) = contentPrefs.setAllowExplicit(allow)

    fun unhide(feedId: Long) = contentPrefs.unhide(feedId)

    fun setAnalytics(enabled: Boolean) = privacyConsent.setAnalytics(enabled)

    fun setCrashReports(enabled: Boolean) = privacyConsent.setCrashReports(enabled)
}
