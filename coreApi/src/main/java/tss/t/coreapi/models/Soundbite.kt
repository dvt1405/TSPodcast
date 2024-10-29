package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class Soundbite(
    val duration: Int,
    val startTime: Int,
    val title: String
): Parcelable