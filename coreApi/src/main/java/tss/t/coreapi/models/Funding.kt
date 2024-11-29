package tss.t.coreapi.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Funding(
    @SerializedName("message")
    val message: String,
    @SerializedName("url")
    val url: String? = null
) : Parcelable