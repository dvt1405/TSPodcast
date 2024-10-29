package tss.t.sharedlibrary.utils

import android.content.Context
import androidx.annotation.DrawableRes
import coil.request.ImageRequest
import tss.t.sharedresources.R
import tss.t.sharedresources.SharedConstants

fun imageRequestBuilder(
    context: Context,
    @DrawableRes
    placeHolder: Int = R.drawable.image_loader_place_holder,
    @DrawableRes
    error: Int = R.drawable.onboarding_slide_7,
): ImageRequest.Builder {
    return ImageRequest.Builder(context = context)
        .addHeader(
            SharedConstants.USER_AGENT_KEY,
            SharedConstants.USER_AGENT_WEB_VALUE
        )
        .placeholder(placeHolder)
        .error(error)
        .crossfade(250)
}