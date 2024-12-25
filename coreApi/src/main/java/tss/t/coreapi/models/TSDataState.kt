package tss.t.coreapi.models

import androidx.annotation.Keep

@Keep
sealed class TSDataState<T : Any>(
    open val data: T?
) {
    data class Success<T : Any>(override val data: T) : TSDataState<T>(data)
    data class Loading<T : Any>(override val data: T? = null) : TSDataState<T>(data)
    data class Error<T : Any>(
        val exception: Throwable,
        override val data: T? = null
    ) : TSDataState<T>(data)

    fun isSuccess(): Boolean {
        return this is Success
    }

    fun exception(): Throwable {
        return (this as Error).exception
    }
}