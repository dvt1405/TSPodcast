package tss.t.coreapi.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Parcelize
@Keep
class Categories : HashMap<String, String>(), Parcelable {
}