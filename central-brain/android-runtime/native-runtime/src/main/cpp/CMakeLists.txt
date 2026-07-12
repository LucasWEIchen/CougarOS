cmake_minimum_required(VERSION 3.22.1)

project(central_brain_native_runtime VERSION 1.0.0 LANGUAGES C)

add_library(
    central_brain_native
    SHARED
    central_brain_native.c
    central_brain_jni.c
)

target_include_directories(
    central_brain_native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

set_target_properties(
    central_brain_native
    PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
    C_EXTENSIONS NO
    C_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)

target_compile_options(
    central_brain_native
    PRIVATE
    -Wall
    -Wextra
    -Werror
    -Wpedantic
    -Wconversion
    -Wshadow
    -Wformat=2
    -Wundef
    -fstack-protector-strong
)

target_compile_definitions(
    central_brain_native
    PRIVATE
    _FORTIFY_SOURCE=2
)

target_link_options(
    central_brain_native
    PRIVATE
    -Wl,--no-undefined
    -Wl,-z,relro
    -Wl,-z,now
)
