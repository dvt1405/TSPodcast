package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

abstract class BaseResponse(
    open val description: String = "",
    open val status: String = "",
)