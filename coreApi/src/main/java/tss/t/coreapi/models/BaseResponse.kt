package tss.t.coreapi.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
abstract class BaseResponse(
    @SerializedName("description")
    open val description: String = "",
    @SerializedName("status")
    open val status: String = "",
)