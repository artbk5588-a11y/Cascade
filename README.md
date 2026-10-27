# CASCADE for Android

Android prototype that builds the CASCADE CPU emulator sources into a native library and exposes ROM selection, CPU start/stop, LCD framebuffer rendering, and a touchscreen keypad.

## Get the APK

Open the [latest Android debug build](https://github.com/artbk5588-a11y/Cascade/actions/workflows/android-debug.yml). Select the newest successful run, open **Artifacts**, and download **cascade-android-debug**. Extract the ZIP to get `app-debug.apk`, then install it on the Android device.

The APK is a debug build, not a signed release.

## First run

1. Start the app.
2. Tap **Выбрать ROM CASCADE** and select a legally obtained ROM image from the device's file picker. The app copies the selected file into its private cache.
3. The native loader currently rejects files smaller than 48 KiB. A successful load is reported in the log.
4. Tap **Запустить CASCADE**. Use the on-screen keypad and watch the LCD area above it.
5. Tap **Остановить CASCADE** to stop the CPU thread.

No ROM image is included in this repository or APK. A successful build does not by itself prove that a particular ROM boots or that all input keys match the target machine.

## Current scope and limitations

- The CASCADE CPU sources are compiled into `libcascade_native.so`.
- The app polls a 480×360 monochrome framebuffer and renders it in the LCD view.
- Touchscreen key-down/key-up events are passed to the native UI event queue.
- The built-in `IfaceFake` diagnostic interface is attached to the CPU. The Bluetooth SPP/ELM327 controls are a separate transport test and are **not** wired into the CASCADE emulator's serial interface.
- Device testing with a real ROM is still required. Please report the ROM load result and any startup log/errors if it fails.

## Build locally

Use JDK 17, Android SDK platform 35, NDK 27.2.12479018, CMake 3.22.1, and Gradle 8.9, then run:

```sh
gradle --no-daemon assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## License

This repository incorporates the upstream CASCADE project. Preserve upstream copyright and license notices in the source files; consult the upstream repository and license files for the applicable terms.
