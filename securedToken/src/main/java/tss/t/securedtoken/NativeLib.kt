package tss.t.securedtoken

import androidx.annotation.Keep
import java.security.MessageDigest
import java.util.Locale

@Keep
object NativeLib {
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

    init {
        System.loadLibrary("securedtoken")
    }
}