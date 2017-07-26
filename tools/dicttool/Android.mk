#
# Copyright (C) 2012 The Android Open Source Project
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

# Only build if it's explicitly requested, or running mm/mmm.
ifneq ($(ONE_SHOT_MAKEFILE)$(filter $(MAKECMDGOALS),dicttool_aosp),)

LATINIME_DICTTOOL_AOSP_LOCAL_PATH := $(call my-dir)
LOCAL_PATH := $(LATINIME_DICTTOOL_AOSP_LOCAL_PATH)
LATINIME_HOST_NATIVE_LIBNAME := liblatinime-aosp-dicttool-host
include $(LOCAL_PATH)/NativeLib.mk

######################################
LOCAL_PATH := $(LATINIME_DICTTOOL_AOSP_LOCAL_PATH)
include $(CLEAR_VARS)

LATINIME_LOCAL_DIR := ../..
LATINIME_BASE_SRC_DIR := $(LATINIME_LOCAL_DIR)/java/src/com/android/inputmethod
LATINIME_BASE_OVERRIDABLE_SRC_DIR := \
        $(LATINIME_LOCAL_DIR)/java-overridable/src/com/android/inputmethod
MAKEDICT_CORE_SRC_DIR := $(LATINIME_BASE_SRC_DIR)/latin/makedict
LATINIME_TESTS_SRC_DIR := $(LATINIME_LOCAL_DIR)/tests/src/com/android/inputmethod/latin

# Dependencies for Dicttool. Most of these files are needed by BinaryDictionary.java. Note that
# a significant part of the dependencies are mocked in the compat/ directory, with empty or
# nearly-empty implementations, for parts that we don't use in Dicttool.
LATINIME_SRC_FILES_FOR_DICTTOOL := \
        latin/BinaryDictionary.java \
        latin/DicTraverseSession.java \
        latin/Dictionary.java \
        latin/NgramContext.java \
        latin/SuggestedWords.java \
        latin/settings/SettingsValuesForSuggestion.java \
        latin/utils/BinaryDictionaryUtils.java \
        latin/utils/CombinedFormatUtils.java \
        latin/utils/JniUtils.java

LATINIME_OVERRIDABLE_SRC_FILES_FOR_DICTTOOL := \
        latin/define/DebugFlags.java

LATINIME_TEST_SRC_FILES_FOR_DICTTOOL := \
        utils/ByteArrayDictBuffer.java

USED_TARGETED_SRC_FILES := \
        $(addprefix $(LATINIME_BASE_SRC_DIR)/, $(LATINIME_SRC_FILES_FOR_DICTTOOL)) \
        $(addprefix $(LATINIME_BASE_OVERRIDABLE_SRC_DIR)/, \
                $(LATINIME_OVERRIDABLE_SRC_FILES_FOR_DICTTOOL)) \
        $(addprefix $(LATINIME_TESTS_SRC_DIR)/, $(LATINIME_TEST_SRC_FILES_FOR_DICTTOOL))

DICTTOOL_ONDEVICE_TESTS_DIR := \
        $(LATINIME_LOCAL_DIR)/tests/src/com/android/inputmethod/latin/makedict/
DICTTOOL_COMPAT_TESTS_DIR := compat

LOCAL_MAIN_SRC_FILES := $(call all-java-files-under, $(MAKEDICT_CORE_SRC_DIR))
LOCAL_TOOL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SRC_FILES := $(LOCAL_TOOL_SRC_FILES) \
        $(filter-out $(addprefix %/, $(notdir $(LOCAL_TOOL_SRC_FILES))), $(LOCAL_MAIN_SRC_FILES)) \
        $(USED_TARGETED_SRC_FILES) \
        $(call all-java-files-under, \
                tests $(DICTTOOL_COMPAT_TESTS_DIR) $(DICTTOOL_ONDEVICE_TESTS_DIR))

LOCAL_JAVA_LIBRARIES := junit-host
LOCAL_STATIC_JAVA_LIBRARIES := jsr305lib latinime-common-host
LOCAL_REQUIRED_MODULES := $(LATINIME_HOST_NATIVE_LIBNAME)
LOCAL_JAR_MANIFEST := etc/manifest.txt
LOCAL_MODULE := dicttool_aosp

include $(BUILD_HOST_JAVA_LIBRARY)
include $(LOCAL_PATH)/etc/Android.mk

# Clear our private variables
LATINIME_DICTTOOL_AOSP_LOCAL_PATH :=
LATINIME_LOCAL_DIR :=

endif
