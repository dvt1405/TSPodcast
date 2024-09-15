package tss.t.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import retrofit2.Call
import retrofit2.CallAdapter
import tss.t.coreapi.models.TSDataState
import java.lang.reflect.Type

class TSDataStateCallWrapperAdapter(
    private val resultType: Type,
    private val coroutineScope: CoroutineScope
) : CallAdapter<Type, Call<TSDataState<Type>>> {
    override fun responseType(): Type {
        return resultType
    }

    override fun adapt(p0: Call<Type>): Call<TSDataState<Type>> {
        return TSDataStateCall(p0, coroutineScope)
    }
}

class TSDataStateCallAdapter(
    private val resultType: Type,
    private val coroutineScope: CoroutineScope
) : CallAdapter<Type, TSDataState<Type>> {
    override fun responseType(): Type {
        return resultType
    }

    override fun adapt(call: Call<Type>): TSDataState<Type> {
        val response = try {
            call.execute()
        } catch (e: Exception) {
            return TSDataState.Error(e)
        }
        return if (response.isSuccessful) {
            if (response.body() != null) {
                TSDataState.Success(response.body()!!)
            } else {
                ErrorHandler.handleError(response)
            }
        } else {
            ErrorHandler.handleError(response)
        }
    }
}