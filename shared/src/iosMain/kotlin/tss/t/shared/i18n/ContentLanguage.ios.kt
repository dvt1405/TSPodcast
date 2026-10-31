package tss.t.shared.i18n

import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/** The localization iOS picked for this app (per-app language in Settings), else the system language. */
actual fun appUiLanguageTag(): String =
    (NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String)
        ?: NSLocale.currentLocale.languageCode

actual fun systemRegionCode(): String = NSLocale.currentLocale.countryCode.orEmpty()
