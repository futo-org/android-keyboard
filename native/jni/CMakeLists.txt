cmake_minimum_required(VERSION 3.22)
project(latinime)

# ------------------------------------------------------------------
# Build options
# ------------------------------------------------------------------
option(EXT_BUILD        "External build" OFF)
option(EXT_BUILD_ASAN   "External build with ASAN" OFF)
option(FLAG_DBG         "Enable debug build" OFF)
option(FLAG_DO_PROFILE  "Enable profiling build" OFF)


# ------------------------------------------------------------------
# Global build settings
# ------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_ANDROID_STL_TYPE c++_static)

# We always use optimization because GGML would otherwise run at an unusable speed
set(CMAKE_C_FLAGS_RELEASE   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

if(EXT_BUILD)
    set(CMAKE_C_FLAGS_DEBUG   "-O0 -ggdb")
    set(CMAKE_CXX_FLAGS_DEBUG "-O0 -ggdb")
    find_package(JNI REQUIRED)
else()
    set(CMAKE_C_FLAGS_DEBUG   "-O1 -ggdb")
    set(CMAKE_CXX_FLAGS_DEBUG "-O1 -ggdb")
endif()

add_compile_options(
        -Wall -Wextra -Weffc++ -Wformat=2 -Wcast-qual -Wcast-align
        -Wwrite-strings -Wfloat-equal -Wpointer-arith -Winit-self
        -Wredundant-decls -Woverloaded-virtual -Wsign-promo
        -Wno-system-headers -Wno-deprecated-copy
        -Wno-unused-parameter -Wno-unused-function
        -Wno-vla-cxx-extension
        -fexceptions
        -DHAVE_PTHREAD
)

if(EXT_BUILD_ASAN)
    add_compile_options(-fsanitize=address)
    add_link_options(-fsanitize=address)
endif()

# Enable NEON unconditionally (same as LOCAL_ARM_NEON := true)
if(${ANDROID_ABI} MATCHES "armeabi-v7a")
    add_compile_options(-mfpu=neon)
endif()

# ------------------------------------------------------------------
# Source files
# ------------------------------------------------------------------
include(${CMAKE_CURRENT_SOURCE_DIR}/NativeFileList.cmake)

# ------------------------------------------------------------------
# Static library: libjni_latinime_common_static
# ------------------------------------------------------------------
add_library(jni_latinime_common_static STATIC ${LATIN_IME_CORE_SRC_FILES})

target_include_directories(jni_latinime_common_static PRIVATE ${LATIN_IME_CORE_INCLUDE})
if(EXT_BUILD)
    target_include_directories(jni_latinime_common_static PRIVATE ${JNI_INCLUDE_DIRS})
endif()

if(FLAG_DO_PROFILE)
    message(STATUS "Making profiling version of native library")
    target_compile_definitions(jni_latinime_common_static PRIVATE FLAG_DO_PROFILE)
    target_compile_options(jni_latinime_common_static PRIVATE -funwind-tables)
elseif(FLAG_DBG)
    message(STATUS "Making debug version of native library")
    target_compile_definitions(jni_latinime_common_static PRIVATE FLAG_DBG)
endif()

# ------------------------------------------------------------------
# Shared library: libjni_latinime.so
# ------------------------------------------------------------------
if(NOT EXT_BUILD)
    add_library(jni_latinime SHARED ${LATIN_IME_JNI_SRC_FILES})

    target_include_directories(jni_latinime PRIVATE ${LATIN_IME_JNI_INCLUDE})

    target_link_libraries(jni_latinime
            jni_latinime_common_static
            log
            dl)
endif()