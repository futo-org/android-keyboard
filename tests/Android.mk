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

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := shared

# Do not compress dictionary files to mmap dict data runtime
LOCAL_AAPT_FLAGS += -0 .dict
# Do not compress test data file
LOCAL_AAPT_FLAGS += -0 .txt

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    mockito-target-minus-junit4

LOCAL_JAVA_LIBRARIES := android.test.mock.stubs legacy-android-test

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := LatinIMETests

LOCAL_INSTRUMENTATION_FOR := LatinIME

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
