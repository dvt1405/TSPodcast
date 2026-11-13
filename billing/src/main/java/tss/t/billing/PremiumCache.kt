package tss.t.billing

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last known Premium state, on a private SharedPreferences file (like the ads counters). Read
 * synchronously at start-up so a Premium user never sees an ad before the store answers; the
 * store result always overwrites it. Not a security boundary: there is no receipt server yet
 * (contract §1.6), and the store is re-queried on every foreground.
 */
@Singleton
class PremiumCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    val premium: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM, false)

    val subscriptionStoreProductId: String?
        get() = prefs.getString(KEY_SUBSCRIPTION, null)

    fun save(entitlement: Entitlement) {
        prefs.edit {
            putBoolean(KEY_PREMIUM, entitlement.premium)
            putString(KEY_SUBSCRIPTION, entitlement.subscriptionStoreProductId)
        }
    }

    /** [Entitlement] as cached, not verified with the store yet. */
    fun entitlement(): Entitlement = Entitlement(
        premium = premium,
        subscriptionStoreProductId = subscriptionStoreProductId,
        verified = false,
    )

    private companion object {
        const val FILE = "billing_premium"
        const val KEY_PREMIUM = "premium"
        const val KEY_SUBSCRIPTION = "subscription_product"
    }
}
