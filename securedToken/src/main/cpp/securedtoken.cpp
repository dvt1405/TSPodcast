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
    std::string apiUrl = "https://api.podcastindex.org/api/1.0";
    return env->NewStringUTF(apiUrl.c_str());
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
