# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ifeq (,$(TARGET_BUILD_APPS))

# Only build if it's explicitly requested, or running mm/mmm.
ifneq ($(ONE_SHOT_MAKEFILE)$(filter $(MAKECMDGOALS),dicttoolkit),)

# HACK: Temporarily disable host tool build on Mac until the build system is ready for C++11.
LATINIME_HOST_OSNAME := $(shell uname -s)
ifneq ($(LATINIME_HOST_OSNAME), Darwin) # TODO: Remove this

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LATIN_IME_CORE_PATH := $(LOCAL_PATH)/../jni

LATIN_IME_DICT_TOOLKIT_SRC_DIR := src
LATIN_IME_CORE_SRC_DIR := ../jni/src

LOCAL_CFLAGS += -Werror -Wall -Wextra -Weffc++ -Wformat=2 -Wcast-qual -Wcast-align \
    -Wwrite-strings -Wfloat-equal -Wpointer-arith -Winit-self -Wredundant-decls \
    -Woverloaded-virtual -Wsign-promo -Wno-system-headers

# To suppress compiler warnings for unused variables/functions used for debug features etc.
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-function
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-function

include $(LOCAL_PATH)/NativeFileList.mk
include $(LATIN_IME_CORE_PATH)/NativeFileList.mk

LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_DICT_TOOLKIT_SRC_DIR) \
    $(LATIN_IME_CORE_PATH)/$(LATIN_IME_CORE_SRC_DIR)

LOCAL_SRC_FILES := $(LATIN_IME_DICT_TOOLKIT_MAIN_SRC_FILES) \
    $(addprefix $(LATIN_IME_DICT_TOOLKIT_SRC_DIR)/, $(LATIN_IME_DICT_TOOLKIT_SRC_FILES)) \
    $(addprefix $(LATIN_IME_CORE_SRC_DIR)/, $(LATIN_IME_CORE_SRC_FILES))

LOCAL_MODULE := dicttoolkit
LOCAL_MODULE_TAGS := optional

LOCAL_CLANG := true
LOCAL_CXX_STL := libc++

include $(BUILD_HOST_EXECUTABLE)
#################### Clean up the tmp vars
include $(LOCAL_PATH)/CleanupNativeFileList.mk
#################### Unit test
include $(LOCAL_PATH)/UnitTests.mk

endif # Darwin - TODO: Remove this

endif

endif # TARGET_BUILD_APPS
