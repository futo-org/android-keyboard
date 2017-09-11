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

LOCAL_PATH := $(call my-dir)

######################################
include $(CLEAR_VARS)

LATIN_IME_CORE_PATH := $(LOCAL_PATH)/../jni

LATIN_IME_DICT_TOOLKIT_SRC_DIR := src
LATIN_IME_CORE_SRC_DIR := ../jni/src
LATIN_DICT_TOOLKIT_TEST_SRC_DIR := tests

include $(LOCAL_PATH)/NativeFileList.mk
include $(LATIN_IME_CORE_PATH)/NativeFileList.mk

LATIN_IME_SRC_DIR := src
LOCAL_ADDRESS_SANITIZER := true
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-function
LOCAL_CXX_STL := libc++
LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_DICT_TOOLKIT_SRC_DIR) \
    $(LATIN_IME_CORE_PATH)/$(LATIN_IME_CORE_SRC_DIR)
LOCAL_MODULE := liblatinime_dicttoolkit_host_static_for_unittests
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    $(addprefix $(LATIN_IME_DICT_TOOLKIT_SRC_DIR)/, $(LATIN_IME_DICT_TOOLKIT_SRC_FILES)) \
    $(addprefix $(LATIN_IME_CORE_SRC_DIR)/, $(LATIN_IME_CORE_SRC_FILES))
include $(BUILD_HOST_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_ADDRESS_SANITIZER := true
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-function
LOCAL_CXX_STL := libc++
LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_DICT_TOOLKIT_SRC_DIR) \
    $(LATIN_IME_CORE_PATH)/$(LATIN_IME_CORE_SRC_DIR)
LOCAL_MODULE := dicttoolkit_unittests
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := \
    $(addprefix $(LATIN_DICT_TOOLKIT_TEST_SRC_DIR)/, $(LATIN_IME_DICT_TOOLKIT_TEST_FILES))
LOCAL_STATIC_LIBRARIES += liblatinime_dicttoolkit_host_static_for_unittests
include $(BUILD_HOST_NATIVE_TEST)

include $(LOCAL_PATH)/CleanupNativeFileList.mk

#################### Clean up the tmp vars
LATINIME_HOST_OSNAME :=
LATIN_IME_SRC_DIR :=
LATIN_IME_TEST_SRC_DIR :=

endif # TARGET_BUILD_APPS
