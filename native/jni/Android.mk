# Copyright (C) 2011 The Android Open Source Project
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
include $(CLEAR_VARS)

LATIN_IME_SRC_DIR := ../src

LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_SRC_DIR)

LOCAL_CFLAGS += -Werror -Wall

# To suppress compiler warnings for unused variables/functions used for debug features etc.
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-function

LATIN_IME_JNI_SRC_FILES := \
    com_android_inputmethod_keyboard_ProximityInfo.cpp \
    com_android_inputmethod_latin_BinaryDictionary.cpp \
    jni_common.cpp

LATIN_IME_CORE_SRC_FILES := \
    additional_proximity_chars.cpp \
    basechars.cpp \
    bigram_dictionary.cpp \
    char_utils.cpp \
    correction.cpp \
    dictionary.cpp \
    proximity_info.cpp \
    unigram_dictionary.cpp

LOCAL_SRC_FILES := \
    $(LATIN_IME_JNI_SRC_FILES) \
    $(addprefix $(LATIN_IME_SRC_DIR)/,$(LATIN_IME_CORE_SRC_FILES))

#FLAG_DBG := true
#FLAG_DO_PROFILE := true

TARGETING_UNBUNDLED_FROYO := true

ifeq ($(TARGET_ARCH), x86)
    TARGETING_UNBUNDLED_FROYO := false
endif

ifeq ($(TARGET_ARCH), mips)
    TARGETING_UNBUNDLED_FROYO := false
endif

ifeq ($(FLAG_DBG), true)
    TARGETING_UNBUNDLED_FROYO := false
endif

ifeq ($(FLAG_DO_PROFILE), true)
    TARGETING_UNBUNDLED_FROYO := false
endif

ifeq ($(TARGETING_UNBUNDLED_FROYO), true)
    LOCAL_NDK_VERSION := 4
    LOCAL_SDK_VERSION := 8
endif

LOCAL_MODULE := libjni_latinime

LOCAL_MODULE_TAGS := optional

# For STL
LOCAL_C_INCLUDES += external/stlport/stlport bionic
LOCAL_SHARED_LIBRARIES += libstlport

ifeq ($(FLAG_DO_PROFILE), true)
    $(warning Making profiling version of native library)
    LOCAL_CFLAGS += -DFLAG_DO_PROFILE
    LOCAL_SHARED_LIBRARIES += libcutils libutils
else # FLAG_DO_PROFILE
ifeq ($(FLAG_DBG), true)
    $(warning Making debug version of native library)
    LOCAL_CFLAGS += -DFLAG_DBG
    LOCAL_SHARED_LIBRARIES += libcutils libutils
endif # FLAG_DBG
endif # FLAG_DO_PROFILE

include $(BUILD_SHARED_LIBRARY)
