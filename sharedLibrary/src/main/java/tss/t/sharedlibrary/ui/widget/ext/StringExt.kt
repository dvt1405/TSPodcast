package tss.t.sharedlibrary.ui.widget.ext

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString

@Composable
fun CharSequence.linkify(
    linkStyle: SpanStyle,
) = linkify(
    LocalContext.current,
    linkStyle
)

fun CharSequence.linkify(
    context: Context,
    linkStyle: SpanStyle
) = buildAnnotatedString {
    append(this@linkify)
    val spannable = SpannableString(this@linkify)
    Linkify.addLinks(
        spannable,
        Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS,
    )
    val spans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
    for (span in spans) {
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        addStyle(
            start = start,
            end = end,
            style = linkStyle,
        )
        addStringAnnotation(
            tag = "URL",
            annotation = span.url,
            start = start,
            end = end
        )
        addLink(
            LinkAnnotation.Clickable(
                "URL",
                styles = TextLinkStyles(style = linkStyle)
            ) {
                val uri = Uri.parse(span.url)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.w("URLSpan", "Actvity was not found for intent, $intent")
                }
            },
            start,
            end
        )
    }
}
