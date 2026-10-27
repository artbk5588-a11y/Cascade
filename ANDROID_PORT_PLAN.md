# Android port: native CASCADE core integration

## Current verified baseline

- The Android app can enumerate paired Bluetooth devices and open an SPP/RFCOMM socket.
- The ELM327 responds to `ATZ`; the test screen showed `ELM327 v2.3`.
- The vehicle returned a positive OBD response to `0100` (`41 00 98 3B 80 11`, split across receive chunks).
- This proves the Android transport path only. It does not yet run the CASCADE emulator.

## Original core layout

The supplied upstream source is an older Qt/C++ application. The emulator logic is in `cpu.cpp`, `cpu_emu.cpp`, `cpu_io.cpp`, `eeprom.cpp`, `hsio.cpp`, `lcd.cpp`, `keypad.cpp`, and related files. `main.cpp` constructs a Qt `UI`, selects an interface, loads a ROM, starts the CPU thread, and runs the GUI event loop. The qmake project also pulls in Qt UI and platform-specific serial implementations.

That entry point cannot simply be compiled into an Android APK: Android has no Qt Widgets event loop in this project, and the existing `os_serial_*.cpp` implementations expect a host serial device. The Android app instead owns a BluetoothSocket.

## Porting sequence

1. **Keep the tested SPP transport.** It is the Android-side byte-stream transport and must remain independent of the emulator.
2. **Create an NDK/JNI boundary.** Android Java/Kotlin owns permissions, BluetoothSocket lifecycle, and the UI. Native code exposes explicit create/start/stop/input/output operations; it must not access Android UI APIs directly.
3. **Separate the emulator from the desktop UI.** Replace the Qt `UI` dependency with a narrow platform-neutral callback interface for LCD frames, LEDs, key input, and error reporting. Keep the desktop Qt frontend out of the Android target.
4. **Add an Android transport backend.** Route byte exchange through callbacks/queues rather than opening `/dev/tty*` from the native core. Do not send ELM AT commands blindly into the emulator protocol; establish which original CASCADE interface and vehicle protocol are actually supported first.
5. **Build core-only tests before loading a ROM.** Compile the selected core with Android NDK, then test reset, memory/CPU stepping and interface behavior using the project's fake interface. Only then wire UI rendering and ROM selection.
6. **Audit ROM distribution separately.** Do not bundle ROM images into the APK until their redistribution rights and expected licensing path have been checked.

## Important compatibility risk

An ELM327 is an OBD adapter, not a generic transparent serial interface to every diagnostic protocol. The original core's ELM interface (`IfaceELM`) and its assumptions about timing, slow init, echoes, and byte framing must be compared with the actual Android transport before treating the ELM327 connection as a compatible replacement for the original hardware interface.

## Definition of done

The native library is not considered integrated until the Android build compiles actual CASCADE emulator source files and the app can start/stop the emulator, surface native errors, and demonstrate a meaningful core test. A successful APK build or an SPP connection alone does not satisfy this.
