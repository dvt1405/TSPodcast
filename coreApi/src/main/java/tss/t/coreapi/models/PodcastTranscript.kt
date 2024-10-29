package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class PodcastTranscript(
    val type: String,
    val url: String
) : Parcelable