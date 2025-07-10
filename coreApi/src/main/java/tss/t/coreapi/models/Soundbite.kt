package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class Soundbite(
    //@SerializedName("duration")
    val duration: Int,
    //@SerializedName("startTime")
    val startTime: Int,
    //@SerializedName("title")
    val title: String
): Parcelable