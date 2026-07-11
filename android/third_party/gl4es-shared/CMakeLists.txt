# Builds gl4es as a SHARED library (libgl4es.so) with visible gl* symbols so
# the Free Pascal engine can dlopen it and resolve the fixed-function OpenGL
# ES 1.1 entry points through GetProcAddress. gl4es re-implements them on top
# of OpenGL ES 2.0, which every modern Android GPU (and the emulator's
# SwiftShader) supports reliably — unlike native ES 1.1.
cmake_minimum_required(VERSION 3.22)
project(gl4es_shared C)

if(NOT DEFINED GL4ES_DIR)
    message(FATAL_ERROR "Pass -DGL4ES_DIR=<path to gl4es checkout>")
endif()

file(GLOB GL_SRC
    "${GL4ES_DIR}/src/gl/*.c"
    "${GL4ES_DIR}/src/gl/wrap/*.c"
    "${GL4ES_DIR}/src/gl/math/*.c"
    "${GL4ES_DIR}/src/glx/*.c")

add_library(gl4es SHARED ${GL_SRC})
target_include_directories(gl4es PRIVATE "${GL4ES_DIR}/src" "${GL4ES_DIR}/include")
target_compile_definitions(gl4es PRIVATE NOX11 NO_GBM DEFAULT_ES=2 ANDROID)
target_compile_options(gl4es PRIVATE
    -std=gnu99 -O2 -funwind-tables -fvisibility=default
    -Wno-typedef-redefinition -Wno-dangling-else -Wno-implicit-function-declaration
    -include "${GL4ES_DIR}/include/android_debug.h")
# 16 KB page alignment (Play requirement)
target_link_options(gl4es PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(gl4es EGL GLESv2 log dl m)
