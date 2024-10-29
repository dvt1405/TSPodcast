package tss.t.coreapi.models


import android.os.Parcelable
import com.google.gson.annotations.Expose
import kotlinx.parcelize.Parcelize

@Parcelize
data class Person(
    @Expose
    val group: String,
    @Expose
    val href: String,
    @Expose
    val id: Int,
    @Expose
    val img: String,
    @Expose
    val name: String,
    @Expose
    val role: String
) : Parcelable