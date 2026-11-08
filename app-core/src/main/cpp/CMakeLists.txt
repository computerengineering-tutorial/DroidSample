cmake_minimum_required(VERSION 3.22.1)
project("camera2ndk_sample" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(camera2ndk_sample SHARED
    native_camera_bridge.cpp
    camera_engine.cpp
)

target_link_libraries(camera2ndk_sample
    camera2ndk
    mediandk
    android
    log
)
