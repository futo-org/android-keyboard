LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src $(JNI_H_INCLUDE)

LOCAL_SRC_FILES := \
	jni/com_android_inputmethod_latin_BinaryDictionary.cpp \
	src/dictionary.cpp \
	src/char_utils.cpp

LOCAL_NDK_VERSION := 4
LOCAL_SDK_VERSION := 8

LOCAL_PRELINK_MODULE := false

LOCAL_MODULE := libjni_latinime2

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
