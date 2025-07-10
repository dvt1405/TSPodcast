package tss.t.coreapi.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SocialInteract(
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("accountUrl")
    val accountUrl: String,
    @SerializedName("priority")
    val priority: Int,
    @SerializedName("protocol")
    val protocol: String,
    @SerializedName("url")
    val url: String
) : Parcelable