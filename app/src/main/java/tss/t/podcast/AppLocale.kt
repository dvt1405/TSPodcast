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
    fun apply(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The framework LocaleManager directly: AppCompatDelegate.setApplicationLocales is a
            // no-op here because MainActivity is a ComponentActivity (no AppCompat delegate to
            // find the LocaleManager through), so the language never changed on Android 13+.
            val manager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
            if (!manager.applicationLocales.toLanguageTags().equals(tag, ignoreCase = true)) {
                manager.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
            }
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /** Whether [config] already shows [tag] (language, and region when the tag has one). */
    fun matches(config: Configuration, tag: String): Boolean {
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.locales[0] else @Suppress("DEPRECATION") config.locale
        val wanted = Locale.forLanguageTag(tag)
        fun lang(l: Locale) = l.language.let { if (it == "in") "id" else it }
        return lang(current) == lang(wanted) && (wanted.country.isEmpty() || wanted.country == current.country)
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
