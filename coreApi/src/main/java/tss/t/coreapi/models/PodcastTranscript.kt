package tss.t.coreapi.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize


@Parcelize
data class PodcastTranscript(
    @SerializedName("type")
    val type: String,
    @SerializedName("url")
    val url: String
) : Parcelable