LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src

LOCAL_SRC_FILES := \
	jni/com_android_inputmethod_latin_BinaryDictionary.cpp \
	src/dictionary.cpp \
	src/char_utils.cpp

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE)

LOCAL_LDLIBS := -lm

LOCAL_PRELINK_MODULE := false

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    libcutils \
    libutils

LOCAL_MODULE := libjni_latinime2

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
