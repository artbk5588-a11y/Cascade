cmake_minimum_required(VERSION 3.22.1)
project(cascade_native LANGUAGES CXX)

add_library(cascade_native SHARED
    native-lib.cpp
    os_android.cpp
    obd_response.cpp
    cascade-core/cpu.cpp
    cascade-core/cpu_emu.cpp
    cascade-core/cpu_io.cpp
    cascade-core/serial.cpp
    cascade-core/eeprom.cpp
    cascade-core/hsio.cpp
    cascade-core/keypad.cpp
    cascade-core/lcd.cpp
    cascade-core/hints.cpp
    cascade-core/iface_fake.cpp
)

target_compile_features(cascade_native PRIVATE cxx_std_17)
target_compile_options(cascade_native PRIVATE -Wall -Wextra)
target_link_libraries(cascade_native log)
