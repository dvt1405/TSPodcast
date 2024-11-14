package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Funding(
    val message: String,
    val url: String? = null
) : Parcelable