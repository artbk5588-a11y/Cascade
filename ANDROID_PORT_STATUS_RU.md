# Android port status — 28 October 2026

## Confirmed on the device

- Android APK can enumerate paired Bluetooth devices after the Android 12+ `BLUETOOTH_CONNECT` permission fix.
- Classic Bluetooth RFCOMM/SPP connects to `OBDII SNOWMAN`.
- ELM command round-trip works: `ATZ` returns the adapter banner `ELM327 v2.3`.
- Initialization commands return `OK`; request `0100` receives a positive-looking response from the vehicle.

This validates the Android transport prototype only. It does **not** mean the CASCADE emulator is running yet.

## Source audit

The uploaded public source is a legacy Qt/C++ application. The original `hiscanemu.pro` builds a Qt Widgets UI and includes Linux serial backends. `main.cpp` constructs `UI`, `Cpu`, and an `IfaceELM`; `IfaceELM` calls the POSIX-style `os_serial_*` API and expects an ELM command prompt (`>`). Android's Java `BluetoothSocket` is currently owned by `MainActivity`, so the native core cannot directly use it as a TTY file descriptor.

## Required integration seams

1. **Separate emulator core from desktop UI.** `ui.h` inherits `QWidget` and exposes Qt types. Android's current Gradle project has no Qt-for-Android setup. We must either port the UI to Android/JNI or provide a narrow native UI adapter and replace Qt-dependent code used by the core.
2. **Provide a transport abstraction.** The existing `IfaceELM` uses `os_serial_open/send/read/expect/flush` on a TTY and configures adapter commands in its constructor. Bluetooth SPP is a byte stream, not a TTY. Implement a tested bridge (Java Bluetooth streams to a native pipe/socketpair, or a new stream interface) and preserve prompt/timeout handling.
3. **Build with Android NDK/CMake.** Compile the minimum core sources for Android ABIs, remove desktop-only serial/Qt sources, and keep all native source and build scripts in the repository.
4. **Load a ROM only after distribution rights and runtime path are confirmed.** The source license is CasPL 1.0 and must remain with derivative works; ROM data should not be bundled blindly before its redistribution status is verified.
5. **End-to-end validation.** On-device test should show the native core starting, receiving bytes from SPP, and running a real CASCADE workflow—not just sending standalone AT commands.

## Next implementation target

First extract a Qt-independent core boundary and a stream-based ELM transport seam. Do not label a JNI placeholder or a successful native smoke test as a working CASCADE port. The current APK remains a transport prototype until the CPU/emulator and required UI path are actually built and launched.
