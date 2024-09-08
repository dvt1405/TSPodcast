package tss.t.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import tss.t.coreapi.models.TSDataState

class TSDataStateCall<T : Any>(
    private val proxy: Call<T>,
    private val coroutineScope: CoroutineScope,
) : Call<TSDataState<T>> {

    override fun execute(): Response<TSDataState<T>> {
        val response = proxy.execute()
        return if (response.isSuccessful) {
            if (response.body() != null) {
                Response.success(TSDataState.Success(response.body()!!))
            } else {
                Response.success(ErrorHandler.handleError(response))
            }
        } else {
            Response.error(response.errorBody(), response.raw())
        }
    }

    override fun enqueue(callback: Callback<TSDataState<T>>) {
        coroutineScope.launch {
            proxy.enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    if (response.isSuccessful && response.body() != null) {
                        callback.onResponse(
                            this@TSDataStateCall,
                            Response.success(TSDataState.Success(response.body()!!))
                        )
                    } else {
                        callback.onResponse(
                            this@TSDataStateCall,
                            Response.success(ErrorHandler.handleError(response))
                        )
                    }

                }

                override fun onFailure(call: Call<T>, p1: Throwable) {
                    callback.onFailure(this@TSDataStateCall, p1)
                }
            })
        }
    }

    override fun clone(): Call<TSDataState<T>> {
        return TSDataStateCall(proxy.clone(), coroutineScope)
    }

    override fun request(): Request = proxy.request()
    override fun timeout(): Timeout = proxy.timeout()
    override fun isExecuted(): Boolean = proxy.isExecuted
    override fun isCanceled(): Boolean = proxy.isCanceled
    override fun cancel() = proxy.cancel()
}