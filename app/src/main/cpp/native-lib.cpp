#include <jni.h>
#include <string>

#include "obd_response.h"

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeValidatePid00(
        JNIEnv* env, jobject /* this */, jstring response) {
    if (response == nullptr) {
        return env->NewStringUTF("NO_RESPONSE");
    }

    const char* chars = env->GetStringUTFChars(response, nullptr);
    if (chars == nullptr) {
        return env->NewStringUTF("NATIVE_ERROR");
    }

    const std::string raw(chars);
    env->ReleaseStringUTFChars(response, chars);

    const std::string normalized = cascade::normalize_obd_hex(raw);
    const bool valid = cascade::is_mode01_pid00_response(raw);
    const std::string result = std::string(valid ? "VALID_PID00: " : "NOT_VALID_PID00: ")
                             + normalized;
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeCoreStatus(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(
        "CASCADE CPU emulator is compiled into libcascade_native; ROM loading and Android LCD/keypad integration are not implemented yet");
}
