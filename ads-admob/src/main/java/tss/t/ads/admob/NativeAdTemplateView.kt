package tss.t.ads.admob

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import tss.t.ads.NativeTemplate

internal data class NativeAdColors(
    val text: Int,
    val secondaryText: Int,
    val background: Int,
    val accent: Int,
    val onAccent: Int,
)

/**
 * Native ad templates built in code (no XML), wrapped in the SDK's
 * [NativeAdView] so clicks and impressions are attributed correctly.
 *
 * Both templates carry a visible "Ad" label (AdMob native policy) and leave
 * AdChoices to the SDK, which overlays it in a corner when no AdChoicesView is
 * assigned.
 *
 * - Small: icon, headline, body/advertiser, call to action in one row.
 * - Medium: the same header plus a [MediaView] (image or muted video).
 *
 * Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class NativeAdTemplateView(
    context: Context,
    private val template: NativeTemplate,
) : FrameLayout(context) {

    private val adView = NativeAdView(context)
    private val icon = ImageView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val badge = TextView(context)
    private val cta = Button(context)
    private val media: MediaView? =
        if (template == NativeTemplate.Medium) MediaView(context) else null
    private var boundAd: NativeAd? = null

    init {
        val pad = dp(12)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        badge.apply {
            text = context.getString(R.string.tss_ads_label)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(1), dp(4), dp(1))
        }
        headline.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        body.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        cta.apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            minHeight = dp(36)
            minimumHeight = dp(36)
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(6) })
            addView(headline, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(body, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (template == NativeTemplate.Small) {
                addView(cta, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
            }
        }
        root.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        if (media != null) {
            media.imageScaleType = ImageView.ScaleType.CENTER_CROP
            root.addView(
                media,
                LinearLayout.LayoutParams(MATCH, dp(180)).apply { topMargin = dp(10) }
            )
            root.addView(cta, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        }

        adView.addView(root, LayoutParams(MATCH, WRAP))
        adView.headlineView = headline
        adView.bodyView = body
        adView.iconView = icon
        adView.callToActionView = cta
        addView(adView, LayoutParams(MATCH, WRAP))
    }

    fun bind(ad: NativeAd, colors: NativeAdColors) {
        setBackgroundColor(colors.background)
        headline.setTextColor(colors.text)
        body.setTextColor(colors.secondaryText)
        badge.setTextColor(colors.onAccent)
        badge.background = rounded(colors.accent, dp(3).toFloat())
        cta.setTextColor(colors.onAccent)
        cta.background = rounded(colors.accent, dp(18).toFloat())

        if (boundAd === ad) return
        boundAd = ad

        headline.text = ad.headline
        val secondary = ad.body ?: ad.advertiser
        body.text = secondary
        body.visibility = if (secondary.isNullOrBlank()) View.GONE else View.VISIBLE
        val iconDrawable = ad.icon?.drawable
        icon.setImageDrawable(iconDrawable)
        icon.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE
        cta.text = ad.callToAction
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
        // Must be called after the asset views are populated.
        adView.registerNativeAd(ad, media)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
