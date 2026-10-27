cmake_minimum_required(VERSION 3.22.1)
project(cascade_native LANGUAGES CXX)

add_library(cascade_native SHARED
    native-lib.cpp
    obd_response.cpp
)

target_compile_features(cascade_native PRIVATE cxx_std_17)
target_compile_options(cascade_native PRIVATE -Wall -Wextra -Werror)
target_link_libraries(cascade_native log)
