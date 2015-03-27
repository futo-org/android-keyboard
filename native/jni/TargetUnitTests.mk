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

LOCAL_PATH := $(call my-dir)

######################################
include $(CLEAR_VARS)

include $(LOCAL_PATH)/NativeFileList.mk

#################### Target library for unit test
LATIN_IME_SRC_DIR := src
LOCAL_CFLAGS += -std=c++11 -Wno-unused-parameter -Wno-unused-function
LOCAL_CLANG := true
LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_SRC_DIR)
LOCAL_MODULE := liblatinime_target_static_for_unittests
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(addprefix $(LATIN_IME_SRC_DIR)/, $(LATIN_IME_CORE_SRC_FILES))
# Here intentionally use libc++_shared rather than libc++_static because
# $(BUILD_NATIVE_TEST) has not yet supported libc++_static.
LOCAL_SDK_VERSION := 14
LOCAL_NDK_STL_VARIANT := c++_shared
include $(BUILD_STATIC_LIBRARY)

#################### Target native tests
include $(CLEAR_VARS)
LATIN_IME_TEST_SRC_DIR := tests
LOCAL_CFLAGS += -std=c++11 -Wno-unused-parameter -Wno-unused-function
LOCAL_CLANG := true
LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_SRC_DIR)
LOCAL_MODULE := liblatinime_target_unittests
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES :=  \
    $(addprefix $(LATIN_IME_TEST_SRC_DIR)/, $(LATIN_IME_CORE_TEST_FILES))
LOCAL_STATIC_LIBRARIES += liblatinime_target_static_for_unittests
include $(BUILD_NATIVE_TEST)

#################### Clean up the tmp vars
LATIN_IME_SRC_DIR :=
LATIN_IME_TEST_SRC_DIR :=
include $(LOCAL_PATH)/CleanupNativeFileList.mk
