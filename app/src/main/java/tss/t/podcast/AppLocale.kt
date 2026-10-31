package tss.t.podcast

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import tss.t.core.storage.LanguagePrefs
import java.util.Locale

/**
 * Applies the app language chosen in Settings (or detected on first launch, LanguagePrefs).
 * Android 13+: the system per-app language (LocaleManager via AppCompat), which also shows in
 * system settings. Older versions: the activity context is wrapped in [wrap].
 */
object AppLocale {
    fun apply(tag: String) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (!current.equals(tag, ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /** API < 33: a context in the saved language (read straight from the prefs file, before Hilt). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences("TSSharedPref", Context.MODE_PRIVATE)
            .getString(LanguagePrefs.KEY_APP_LANGUAGE, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
