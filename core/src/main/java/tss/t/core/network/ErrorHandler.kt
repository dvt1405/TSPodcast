package tss.t.core.network

import retrofit2.HttpException
import retrofit2.Response
import tss.t.core.exceptions.TSExceptions
import tss.t.coreapi.models.TSDataState
import java.nio.charset.Charset

object ErrorHandler {
    fun <T : Any> handleError(response: Response<T>): TSDataState.Error<T> {
        return TSDataState.Error(
            TSExceptions(
                errorCode = response.code(),
                message = response.message(),
                description = response.errorBody()
                    ?.source()
                    ?.readString(Charset.defaultCharset()) ?: response.message(),
                cause = HttpException(response)
            )
        )
    }
}