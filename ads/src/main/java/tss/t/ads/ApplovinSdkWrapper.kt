package tss.t.ads

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import tss.t.securedtoken.NativeLib
import tss.t.sharedfirebase.TSAnalytics
import javax.inject.Inject
import javax.inject.Singleton

val LocalBannerAdsManagerScope = compositionLocalOf<BannerAdsManager?> { null }
val LocalNativeAdsManagerScope = compositionLocalOf<NativeAdsManager?> { null }
val LocalAppOpenAdManagerScope = compositionLocalOf<AppOpenAdManager?> { null }

@Singleton
class ApplovinSdkWrapper @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val appOpenAdManager: AppOpenAdManager,
    private val adBannerAdsManager: BannerAdsManager,
    private val nativeAdsManager: NativeAdsManager,
    private val tsAnalytics: TSAnalytics,
    private val adsScope: ApplovinCoroutineScope
) {
    private var _state: SdkState = SdkState.None
    private var _isInitSdkInProcess: Boolean = false
    private val _pendingCallback: MutableList<ApplovinInitSdkCallback> by lazy {
        mutableListOf()
    }

    private val isInitializedSdk: Boolean
        get() = _state == SdkState.Ready

    private val isInitSdkInProcess: Boolean
        get() = _state == SdkState.InitInProcess

    fun initSdk(callback: ApplovinInitSdkCallback = ApplovinInitSdkCallback {}) {
        if (isInitializedSdk) {
            callback.onInitSuccess()
            return
        } else if (_isInitSdkInProcess) {
            _pendingCallback.add(callback)
            return
        }
        _state = SdkState.InitInProcess
        val initConfig = AppLovinSdkInitializationConfiguration
            .builder(NativeLib.getApplovinKey(), context)
            .setMediationProvider(AppLovinMediationProvider.MAX)
            .setTestDeviceAdvertisingIds(listOf(TEST_DEVICE_1))
            .setAdUnitIds(
                listOf(
                    NativeLib.getNativeId(),
                    NativeLib.getAdBannerId(),
                    NativeLib.getAdAppOpenId(),
                    NativeLib.getAdInterstitialId(),
                    NativeLib.getNativeMediumId(),
                    NativeLib.getNativeSmallId()
                )
            )
            .setExceptionHandlerEnabled(true)
            .build()

        AppLovinSdk.getInstance(context)
            .initialize(initConfig) { sdkConfig ->
                _state = SdkState.Ready
                synchronized(_pendingCallback) {
                    callback.onInitSuccess()
                    _pendingCallback.forEach {
                        it.onInitSuccess()
                    }
                    _pendingCallback.clear()
                }
            }
    }

    fun addListener(callback: ApplovinInitSdkCallback) {
        synchronized(_pendingCallback) {
            _pendingCallback.add(callback)
        }
    }

    fun loadOpenAds() {
        if (!appOpenAdManager.adEnabled) return
        when (_state) {

            SdkState.Ready -> {
                adsScope.mainDispatcher.launch {
                    appOpenAdManager.showAdIfReady()
                }
            }

            SdkState.InitInProcess -> addListener {
                loadOpenAds()
            }

            else -> {
                initSdk {
                    loadOpenAds()
                }
            }
        }
    }

    enum class SdkState {
        None,
        InitInProcess,
        Ready,
        Failed
    }

    companion object {
        const val TEST_DEVICE_1 = "913ed179-c92b-43ba-8954-10ca1ad1abe1"
    }
}