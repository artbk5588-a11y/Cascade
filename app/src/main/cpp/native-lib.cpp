#include <jni.h>
#include <string>
#include <sys/stat.h>
#include <thread>
#include <mutex>
#include "cascade-core/iface_fake.h"

#include "obd_response.h"
#include "cascade-core/cpu.h"
#include "cascade-core/ui.h"

namespace {
UI* g_cascadeUi = nullptr;
Cpu* g_cascadeCpu = nullptr;
IfaceFake* g_cascadeIface = nullptr;
std::thread g_emulationThread;
std::mutex g_coreMutex;
bool g_romLoaded = false;
bool g_emulationRunning = false;

void stopEmulationLocked() {
    if (g_cascadeCpu && g_emulationRunning) {
        g_cascadeCpu->sendCommand(CPU_CMD_EXIT);
    }
    if (g_emulationThread.joinable()) g_emulationThread.join();
    g_emulationRunning = false;
}

void releaseCascadeCore() {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    stopEmulationLocked();
    delete g_cascadeCpu;
    g_cascadeCpu = nullptr;
    delete g_cascadeIface;
    g_cascadeIface = nullptr;
    delete g_cascadeUi;
    g_cascadeUi = nullptr;
    g_romLoaded = false;
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

    std::lock_guard<std::mutex> lock(g_coreMutex);
    stopEmulationLocked();
    if (!g_cascadeUi) g_cascadeUi = new UI();
    if (!g_cascadeCpu) g_cascadeCpu = new Cpu(g_cascadeUi);
    const bool loaded = g_cascadeCpu->loadRom(path);
    g_romLoaded = loaded;
    env->ReleaseStringUTFChars(pathValue, path);
    return env->NewStringUTF(loaded
        ? "ROM_LOADED: CASCADE accepted the ROM image; tap Start CASCADE to run the emulation core"
        : "ROM_ERROR: CASCADE could not load this image");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeCoreStatus(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(
        "CASCADE CPU sources are linked. ROM loading is available; CPU execution, LCD rendering, and keypad integration remain unfinished.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeStartEmulation(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_romLoaded || !g_cascadeCpu || !g_cascadeUi)
        return env->NewStringUTF("CORE_ERROR: select and load a ROM first");
    if (g_emulationRunning)
        return env->NewStringUTF("CORE_RUNNING: emulation is already running");
    if (g_emulationThread.joinable()) g_emulationThread.join();
    if (!g_cascadeIface) {
        g_cascadeIface = new IfaceFake(g_cascadeCpu, g_cascadeUi);
        g_cascadeCpu->setSerial(g_cascadeIface, true);
    }
    g_emulationRunning = true;
    g_emulationThread = std::thread([] {
        g_cascadeCpu->emulate();
        std::lock_guard<std::mutex> doneLock(g_coreMutex);
        g_emulationRunning = false;
    });
    return env->NewStringUTF("CORE_STARTING: CASCADE CPU thread started with the built-in fake diagnostic interface");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_cascade_android_MainActivity_nativeStopEmulation(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    stopEmulationLocked();
    return env->NewStringUTF("CORE_STOPPED: CASCADE CPU thread has stopped");
}
