package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class Value(
    val destinations: List<Destination>,
    val model: Model
) : Parcelable