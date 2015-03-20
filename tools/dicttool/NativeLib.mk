#
# Copyright (C) 2013 The Android Open Source Project
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

# HACK: Temporarily disable host tool build on Mac until the build system is ready for C++11.
LATINIME_HOST_OSNAME := $(shell uname -s)
ifneq ($(LATINIME_HOST_OSNAME), Darwin) # TODO: Remove this

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Need to define the name of the library in the caller in LATINIME_HOST_NATIVE_LIBNAME

LATINIME_DIR_RELATIVE_TO_DICTTOOL := ../..

ifeq ($(FLAG_DBG), true)
    $(warning Making debug version of native library)
    LOCAL_CFLAGS += -DFLAG_DBG -funwind-tables -fno-inline
endif #FLAG_DBG

LOCAL_CFLAGS += -DHOST_TOOL -fPIC -Wno-deprecated -Wno-unused-parameter -Wno-unused-function
ifneq ($(strip $(HOST_JDK_IS_64BIT_VERSION)),)
LOCAL_MULTILIB := 64
endif #HOST_JDK_IS_64BIT_VERSION

# For C++11
LOCAL_CFLAGS += -std=c++11

LATINIME_NATIVE_JNI_DIR := $(LATINIME_DIR_RELATIVE_TO_DICTTOOL)/native/jni
LATINIME_NATIVE_SRC_DIR := $(LATINIME_DIR_RELATIVE_TO_DICTTOOL)/native/jni/src
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(LATINIME_NATIVE_SRC_DIR)

include $(LOCAL_PATH)/$(LATINIME_NATIVE_JNI_DIR)/NativeFileList.mk

LOCAL_SRC_FILES := \
    $(addprefix $(LATINIME_NATIVE_JNI_DIR)/, $(LATIN_IME_JNI_SRC_FILES)) \
    $(addprefix $(LATINIME_NATIVE_SRC_DIR)/, $(LATIN_IME_CORE_SRC_FILES))

LOCAL_MODULE := $(LATINIME_HOST_NATIVE_LIBNAME)

include $(BUILD_HOST_SHARED_LIBRARY)

endif # Darwin - TODO: Remove this

# Clear our private variables
include $(LOCAL_PATH)/$(LATINIME_NATIVE_JNI_DIR)/CleanupNativeFileList.mk
LATINIME_DIR_RELATIVE_TO_DICTTOOL := ../..
LATINIME_HOST_OSNAME :=
