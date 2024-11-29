package tss.t.coreapi.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize


@Parcelize
data class Value(
    @SerializedName("destinations")
    val destinations: List<Destination>,
    @SerializedName("model")
    val model: Model
) : Parcelable