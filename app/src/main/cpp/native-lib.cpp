#include <jni.h>
#include <string>
#include <sys/stat.h>

#include "obd_response.h"
#include "cascade-core/cpu.h"
#include "cascade-core/ui.h"

namespace {
UI* g_cascadeUi = nullptr;
Cpu* g_cascadeCpu = nullptr;

void releaseCascadeCore() {
    delete g_cascadeCpu;
    g_cascadeCpu = nullptr;
    delete g_cascadeUi;
    g_cascadeUi = nullptr;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeValidatePid00(
        JNIEnv* env, jobject /* this */, jstring response) {
    if (response == nullptr) return env->NewStringUTF("NO_RESPONSE");
    const char* chars = env->GetStringUTFChars(response, nullptr);
    if (chars == nullptr) return env->NewStringUTF("NATIVE_ERROR");
    const std::string raw(chars);
    env->ReleaseStringUTFChars(response, chars);
    const std::string normalized = cascade::normalize_obd_hex(raw);
    const bool valid = cascade::is_mode01_pid00_response(raw);
    const std::string result = std::string(valid ? "VALID_PID00: " : "NOT_VALID_PID00: ") + normalized;
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeLoadRom(
        JNIEnv* env, jobject /* this */, jstring pathValue) {
    if (pathValue == nullptr) return env->NewStringUTF("ROM_ERROR: no path");
    const char* path = env->GetStringUTFChars(pathValue, nullptr);
    if (path == nullptr) return env->NewStringUTF("ROM_ERROR: JNI string unavailable");

    struct stat st;
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) {
        env->ReleaseStringUTFChars(pathValue, path);
        return env->NewStringUTF("ROM_ERROR: file cannot be opened");
    }
    // Cpu::loadRom copies the first 0xC000 bytes into RAM, so reject undersized
    // files before entering upstream code (which assumes a full ROM image).
    if (st.st_size < 0xC000) {
        env->ReleaseStringUTFChars(pathValue, path);
        return env->NewStringUTF("ROM_ERROR: file is smaller than 48 KiB");
    }

    if (!g_cascadeUi) g_cascadeUi = new UI();
    if (!g_cascadeCpu) g_cascadeCpu = new Cpu(g_cascadeUi);
    const bool loaded = g_cascadeCpu->loadRom(path);
    env->ReleaseStringUTFChars(pathValue, path);
    return env->NewStringUTF(loaded
        ? "ROM_LOADED: CASCADE accepted the ROM image; CPU execution and Android display are not started yet"
        : "ROM_ERROR: CASCADE could not load this image");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeCoreStatus(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(
        "CASCADE CPU sources are linked. ROM loading is available; CPU execution, LCD rendering, and keypad integration remain unfinished.");
}
