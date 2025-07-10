package tss.t.coreapi.models


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Person(
    //@SerializedName("group")
    val group: String,
    //@SerializedName("href")
    val href: String,
    //@SerializedName("id")
    val id: Int,
    //@SerializedName("img")
    val img: String,
    //@SerializedName("name")
    val name: String,
    //@SerializedName("role")
    val role: String
) : Parcelable