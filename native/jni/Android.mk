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

############ some local flags
# If you change any of those flags, you need to rebuild both libjni_latinime_common_static
# and the shared library that uses libjni_latinime_common_static.
FLAG_DBG ?= false
FLAG_DO_PROFILE ?= false

######################################
include $(CLEAR_VARS)

LATIN_IME_SRC_DIR := src

LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(LATIN_IME_SRC_DIR)

LOCAL_CFLAGS += -Werror -Wall -Wextra -Weffc++ -Wformat=2 -Wcast-qual -Wcast-align \
    -Wwrite-strings -Wfloat-equal -Wpointer-arith -Winit-self -Wredundant-decls -Wno-system-headers

ifeq ($(TARGET_ARCH), arm)
ifeq ($(TARGET_GCC_VERSION), 4.6)
LOCAL_CFLAGS += -Winline
endif # TARGET_GCC_VERSION
endif # TARGET_ARCH

# To suppress compiler warnings for unused variables/functions used for debug features etc.
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-unused-function

LATIN_IME_JNI_SRC_FILES := \
    com_android_inputmethod_keyboard_ProximityInfo.cpp \
    com_android_inputmethod_latin_BinaryDictionary.cpp \
    com_android_inputmethod_latin_DicTraverseSession.cpp \
    jni_common.cpp

LATIN_IME_CORE_SRC_FILES := \
    additional_proximity_chars.cpp \
    bigram_dictionary.cpp \
    char_utils.cpp \
    correction.cpp \
    dictionary.cpp \
    dic_traverse_wrapper.cpp \
    digraph_utils.cpp \
    proximity_info.cpp \
    proximity_info_params.cpp \
    proximity_info_state.cpp \
    proximity_info_state_utils.cpp \
    unigram_dictionary.cpp \
    words_priority_queue.cpp \
    suggest/core/suggest.cpp \
    $(addprefix suggest/core/dicnode/, \
        dic_node.cpp \
        dic_node_utils.cpp \
        dic_nodes_cache.cpp) \
    suggest/core/policy/weighting.cpp \
    suggest/core/session/dic_traverse_session.cpp \
    suggest/policyimpl/gesture/gesture_suggest_policy_factory.cpp \
    $(addprefix suggest/policyimpl/typing/, \
        scoring_params.cpp \
        typing_scoring.cpp \
        typing_suggest_policy.cpp \
        typing_traversal.cpp \
        typing_weighting.cpp)

LOCAL_SRC_FILES := \
    $(LATIN_IME_JNI_SRC_FILES) \
    $(addprefix $(LATIN_IME_SRC_DIR)/, $(LATIN_IME_CORE_SRC_FILES))

ifeq ($(FLAG_DO_PROFILE), true)
    $(warning Making profiling version of native library)
    LOCAL_CFLAGS += -DFLAG_DO_PROFILE -funwind-tables -fno-inline
else # FLAG_DO_PROFILE
ifeq ($(FLAG_DBG), true)
    $(warning Making debug version of native library)
    LOCAL_CFLAGS += -DFLAG_DBG -funwind-tables -fno-inline
ifeq ($(FLAG_FULL_DBG), true)
    $(warning Making full debug version of native library)
    LOCAL_CFLAGS += -DFLAG_FULL_DBG
endif # FLAG_FULL_DBG
endif # FLAG_DBG
endif # FLAG_DO_PROFILE

LOCAL_MODULE := libjni_latinime_common_static
LOCAL_MODULE_TAGS := optional

LOCAL_SDK_VERSION := 14
LOCAL_NDK_STL_VARIANT := stlport_static

include $(BUILD_STATIC_LIBRARY)
######################################
include $(CLEAR_VARS)

# All code in LOCAL_WHOLE_STATIC_LIBRARIES will be built into this shared library.
LOCAL_WHOLE_STATIC_LIBRARIES := libjni_latinime_common_static

ifeq ($(FLAG_DO_PROFILE), true)
    $(warning Making profiling version of native library)
    LOCAL_LDFLAGS += -llog
else # FLAG_DO_PROFILE
ifeq ($(FLAG_DBG), true)
    $(warning Making debug version of native library)
    LOCAL_LDFLAGS += -llog
endif # FLAG_DBG
endif # FLAG_DO_PROFILE

LOCAL_MODULE := libjni_latinime
LOCAL_MODULE_TAGS := optional

LOCAL_SDK_VERSION := 14
LOCAL_NDK_STL_VARIANT := stlport_static
LOCAL_LDFLAGS += -ldl

include $(BUILD_SHARED_LIBRARY)

#################### Clean up the tmp vars
LATIN_IME_CORE_SRC_FILES :=
LATIN_IME_JNI_SRC_FILES :=
LATIN_IME_SRC_DIR :=
