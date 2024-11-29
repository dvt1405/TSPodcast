#include <jni.h>
#include <string>
#include <ctime>

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getSecureToken(
        JNIEnv *env,
        jobject /* this */
) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getApiUrl(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiUrl = "https://api.podcastindex.org/api/1.0/";
    return env->NewStringUTF(apiUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getUserAgent(
        JNIEnv *env,
        jobject /* this */
) {
    std::string userAgent = "TSSPodcast/Android";
    return env->NewStringUTF(userAgent.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getApiKey(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiUrl = "DNCYKCFMPKCJMBY6UARC";
    return env->NewStringUTF(apiUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getApiSecret(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiUrl = "5pugGKXBHCmGjM6$FMNdkmW7^qfnDnLju6U5uWsa";
    return env->NewStringUTF(apiUrl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getTime(
        JNIEnv *env,
        jobject /* this */
) {
    auto time = std::chrono::system_clock::now();
    std::time_t t = std::chrono::system_clock::to_time_t(time);
    long timeInMilli = static_cast<long int>(t);
    return env->NewStringUTF(std::to_string(timeInMilli).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getAuthHeader(
        JNIEnv *env,
        jobject obj
) {
    jclass nativeLibClass = env->FindClass("tss/t/securedtoken/NativeLib");
    jmethodID hashMethod = env->GetMethodID(nativeLibClass, "sha1Hash",
                                            "(Ljava/lang/String;)Ljava/lang/String;");
    std::string apiKey = "DNCYKCFMPKCJMBY6UARC";
    std::string apiSecret = "5pugGKXBHCmGjM6$FMNdkmW7^qfnDnLju6U5uWsa";
    auto time = std::chrono::system_clock::now();
    std::time_t t = std::chrono::system_clock::to_time_t(time);
    long timeInMilli = static_cast<long int>(t);
    std::string input = apiKey + apiSecret + std::to_string(timeInMilli);
    std::printf("%s\n", input.c_str());
    jstring inputStr = env->NewStringUTF(input.c_str());
    jobject result = env->CallObjectMethod(obj, hashMethod, inputStr);
    return (jstring) result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getApplovinKey(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "XoUhFsdfrdHsitU1lkxGZJtdzJNUpccv4zCuBizPr-KxNnOwq8DkW9DMZOyBrqSWXRfPLQeD7vxF_TtdJM3vpG";
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getAdAppOpenId(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "faf620f74848eb9a";
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getAdBannerId(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "c48aa01dfd26d222";
    return env->NewStringUTF(apiKey.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getAdInterstitialId(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "72c81778bb62ed1f";
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getNativeId(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "f158449395289e71";
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getNativeSmallId(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "6849d51df7d98235";
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_tss_t_securedtoken_NativeLib_getNativeMediumId(
        JNIEnv *env,
        jobject /* this */
) {
    std::string apiKey = "0d7fd972b2451a71";
    return env->NewStringUTF(apiKey.c_str());
}