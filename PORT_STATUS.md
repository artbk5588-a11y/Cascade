# Android port status (26 October 2026)

## What exists in this branch
- A minimal Android Gradle application and Bluetooth permission handling.
- A paired-device picker and RFCOMM/SPP connection screen.
- A basic ELM327 command console (ATZ, adapter initialization, 0100).
- A GitHub Actions workflow that attempts to build a debug APK and publish it as an artifact.

## What does not exist yet
This is not yet a working Android port of CASCADE. The original C++/Qt emulator sources from the supplied archive have not been imported into this repository, and the Android app does not call the emulator CPU, ROM loader, or CASCADE interface layer. The first APK, if CI succeeds, is only an SPP/ELM327 transport prototype.

## Original source findings
The supplied archive contains the legacy cascade-core C++/Qt project. The original build file is hiscanemu.pro; the serial abstraction is declared in os.h, with Linux-specific TTY handling in os_serial_linux.cpp. The Linux transport relies on TTY file descriptors and Linux-specific ioctls (TIOCGSERIAL, TIOCSSERIAL, BREAK, RTS and DTR). These operations cannot simply be reused for a Bluetooth ELM327 socket.

The IfaceELM layer and emulator CPU need to be connected to an Android transport. This likely requires a JNI/native build and a transport abstraction that supports ELM327 command/response framing. Legacy serial initialization methods that depend on physical BREAK/RTS/DTR need explicit handling; ELM327 adapters do not expose those physical line controls through SPP.

## Next engineering steps
1. Import the source files and build metadata from cascade-core, excluding generated binaries and ROM images until their redistribution rights and size are confirmed.
2. Create an Android NDK/CMake target for the portable emulator sources; remove Qt/desktop UI dependencies from the Android target or implement a native Android UI.
3. Define a transport interface between Java/Kotlin Bluetooth SPP and the C++ IfaceELM layer.
4. Make ROM selection configurable from Android app storage. Do not bundle ROM images without confirming that distribution is permitted.
5. Build in CI, test the emulator core with the fake interface, then test ELM327 and real hardware separately.

## Build
Open the repository's Actions tab and inspect the latest Android debug APK run. A successful run should produce the cascade-android-debug artifact containing app-debug.apk.
