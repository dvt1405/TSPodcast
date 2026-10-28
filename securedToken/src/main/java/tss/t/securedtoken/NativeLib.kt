package tss.t.securedtoken

import androidx.annotation.Keep
import java.security.MessageDigest
import java.util.Locale

@Keep
object NativeLib {
    /**
     * Why `libsecuredtoken.so` could not be loaded, or null when it loaded.
     *
     * The app ships as an AAB with ABI splits, so the library lives only in the
     * `config.<abi>` split APK. An install that has the base APK without that
     * split (a sideloaded base.apk, an APK-extractor or device-transfer copy)
     * has no library at all. This used to be an unconditional loadLibrary in
     * `init`, which threw from `Application.onCreate` on every launch, before
     * any UI could tell the user what was wrong.
     *
     * Callers that run without a screen to fall back on check [isAvailable]
     * before touching the `external` functions below.
     */
    val loadError: UnsatisfiedLinkError? = try {
        System.loadLibrary("securedtoken")
        null
    } catch (error: UnsatisfiedLinkError) {
        error
    }

    val isAvailable: Boolean
        get() = loadError == null

    /**
     * A native method that is implemented by the 'securedtoken' native library,
     * which is packaged with this application.
     */
    external fun getApiUrl(): String
    external fun getApiKey(): String
    external fun getTime(): String
    external fun getAuthHeader(): String
    external fun getUserAgent(): String
    external fun getApplovinKey(): String
    external fun getAdAppOpenId(): String
    external fun getAdBannerId(): String
    external fun getAdInterstitialId(): String
    external fun getNativeId(): String
    external fun getNativeSmallId(): String
    external fun getNativeMediumId(): String

    private fun byteArrayToString(bytes: ByteArray): String {
        val buffer = StringBuilder()
        for (b in bytes) {
            buffer.append(String.format(Locale.getDefault(), "%02x", b))
        }
        return buffer.toString()
    }

    fun sha1Hash(clearString: String): String? {
        try {
            val messageDigest = MessageDigest.getInstance("SHA-1")
            messageDigest.update(clearString.toByteArray(charset("UTF-8")))
            return byteArrayToString(messageDigest.digest())
        } catch (ignored: Exception) {
            ignored.printStackTrace()
            return null
        }
    }
}