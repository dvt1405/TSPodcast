package tss.t.core.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tss.t.core.BuildConfig
import tss.t.core.network.TSDataStateCallAdapterFactory
import tss.t.coreapi.API
import tss.t.coreapi.converter.MapStringStringJsonConverter
import tss.t.coreapi.converter.TSDataStateJsonConverter
import tss.t.coreapi.models.Categories
import tss.t.securedtoken.NativeLib
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(
                Categories::class.java,
                MapStringStringJsonConverter()
            )
            .create()
    }

    @Provides
    fun provideClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                val request = it.request().newBuilder()
                request.header("X-Auth-Date", NativeLib.getTime())
                request.header("X-Auth-Key", NativeLib.getApiKey())
                request.header("Authorization", NativeLib.getAuthHeader())
                request.header("User-Agent", NativeLib.getUserAgent())
                return@Interceptor it.proceed(request.build())
            })
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        this.level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
    }

    @Provides
    @Named("NetworkScope")
    fun provideNetworkCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Provides
    fun provideRetrofit(
        gson: Gson,
        client: OkHttpClient,
        @Named("NetworkScope")
        scope: CoroutineScope
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .addConverterFactory(
                GsonConverterFactory
                    .create(gson)
            )
            .addConverterFactory(TSDataStateJsonConverter.Factory(gson))
            .addCallAdapterFactory(TSDataStateCallAdapterFactory(scope))
            .client(client)
    }

    @Provides
    fun provideAPI(retrofit: Retrofit.Builder): API {
        return retrofit.baseUrl(NativeLib.getApiUrl())
            .build()
            .create(API::class.java)
    }
}