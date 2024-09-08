package tss.t.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import tss.t.coreapi.models.TSDataState
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class TSDataStateCallAdapterFactory(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        val rawReturnType = getRawType(returnType)
        when (rawReturnType) {
            Call::class.java -> {
                val callType = getParameterUpperBound(0, returnType as ParameterizedType)
                val rawCallType = getRawType(callType)
                if (rawCallType == TSDataState::class.java) {
                    val resultType = getParameterUpperBound(0, callType as ParameterizedType)
                    return TSDataStateCallWrapperAdapter(resultType, coroutineScope)
                }
            }

            TSDataState::class.java -> {
                val resultType = getParameterUpperBound(0, returnType as ParameterizedType)
                return TSDataStateCallAdapter(resultType, coroutineScope)
            }
        }
        return null
    }
}