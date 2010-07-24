LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := LatinIME

LOCAL_CERTIFICATE := shared

LOCAL_JNI_SHARED_LIBRARIES := libjni_latinime

LOCAL_STATIC_JAVA_LIBRARIES := android-common

#LOCAL_AAPT_FLAGS := -0 .dict
# The following flag is required because we use a different package name
# com.google.android.inputmethod.latin than Java package name
# com.android.inputmethod.latin
LOCAL_AAPT_FLAGS := --custom-package com.android.inputmethod.latin

LOCAL_SDK_VERSION := 8

LOCAL_PROGUARD_FLAGS := -include $(LOCAL_PATH)/proguard.flags

# Define LOCAL_DICTIONARY_RESOURCE_DIR in order to overlay dictionaries.
# The overlay dictionary resource directory should have dictionary files such
# as raw-en/main.dict, raw-es/main.dict per locale.
ifneq ($(strip $(LOCAL_DICTIONARY_RESOURCE_DIR)),)
LOCAL_RESOURCE_DIR := $(LOCAL_DICTIONARY_RESOURCE_DIR) $(LOCAL_PATH)/res
endif

include $(BUILD_PACKAGE)
