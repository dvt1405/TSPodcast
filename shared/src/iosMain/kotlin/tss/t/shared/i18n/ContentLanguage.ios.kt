package tss.t.shared.i18n

import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages

/** The localization iOS picked for this app (per-app language in Settings), else the system language. */
actual fun appUiLanguageTag(): String =
    (NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String)
        ?: NSLocale.currentLocale.languageCode

actual fun systemRegionCode(): String = NSLocale.currentLocale.countryCode.orEmpty()

/** `NSLocale.preferredLanguages` (the iPhone's language list), not the app's localization. */
actual fun systemLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: NSLocale.currentLocale.languageCode
