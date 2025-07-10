package tss.t.sharedkmp.models

/**
 * A generic class that holds a value with its loading status.
 * @param T The type of data being loaded.
 * @param data The data being loaded.
 */
sealed class TSDataState<T : Any>(
    open val data: T?
) {
    /**
     * Represents a successful data loading operation.
     * @param data The loaded data.
     */
    data class Success<T : Any>(override val data: T) : TSDataState<T>(data)
    
    /**
     * Represents a data loading operation in progress.
     * @param data Optional data that might be available while loading.
     */
    data class Loading<T : Any>(override val data: T? = null) : TSDataState<T>(data)
    
    /**
     * Represents a failed data loading operation.
     * @param exception The exception that caused the failure.
     * @param data Optional data that might be available despite the error.
     */
    data class Error<T : Any>(
        val exception: Throwable,
        override val data: T? = null
    ) : TSDataState<T>(data)

    /**
     * Checks if the current state is [Success].
     * @return True if the state is [Success], false otherwise.
     */
    fun isSuccess(): Boolean {
        return this is Success
    }

    /**
     * Gets the exception from an [Error] state.
     * @return The exception that caused the error.
     * @throws ClassCastException if the state is not [Error].
     */
    fun exception(): Throwable {
        return (this as Error).exception
    }
}