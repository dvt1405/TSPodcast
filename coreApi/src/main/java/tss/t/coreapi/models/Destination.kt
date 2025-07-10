package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Destination(
    //@SerializedName("address")
    val address: String,
    //@SerializedName("customKey")
    val customKey: String? = null,
    //@SerializedName("customValue")
    val customValue: String? = null,
    //@SerializedName("fee")
    val fee: Boolean? = null,
    //@SerializedName("name")
    val name: String,
    //@SerializedName("split")
    val split: Int,
    //@SerializedName("type")
    val type: String
) : Parcelable