cmake_minimum_required(VERSION 3.22)
project(phonk_drop_editor LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(phonknative SHARED
        phonk_jni.cpp
        analysis.cpp
        timeline_engine.cpp
        json.cpp
        crash_signal.cpp)

target_include_directories(phonknative PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_definitions(phonknative PRIVATE PHONK_ANDROID)