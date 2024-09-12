package tss.t.coreapi.models

sealed class TSDataState<T : Any> {
    data class Success<T : Any>(val data: T) : TSDataState<T>()
    data class Loading<T : Any>(val data: T? = null) : TSDataState<T>()
    data class Error<T : Any>(val exception: Throwable) : TSDataState<T>()
}