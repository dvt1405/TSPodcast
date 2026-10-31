package tss.t.shared.i18n

import java.util.Locale

actual fun appUiLanguageTag(): String = Locale.getDefault().toLanguageTag()

actual fun systemRegionCode(): String = Locale.getDefault().country.orEmpty()
