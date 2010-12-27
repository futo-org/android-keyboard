LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src $(JNI_H_INCLUDE)

LOCAL_SRC_FILES := \
    jni/com_android_inputmethod_latin_BinaryDictionary.cpp \
    src/bigram_dictionary.cpp \
    src/char_utils.cpp \
    src/dictionary.cpp \
    src/unigram_dictionary.cpp

#FLAG_DBG := true

ifneq ($(FLAG_DBG), true)
    LOCAL_NDK_VERSION := 4
endif

LOCAL_SDK_VERSION := 8

LOCAL_PRELINK_MODULE := false

LOCAL_MODULE := libjni_latinime2

LOCAL_MODULE_TAGS := optional

ifeq ($(FLAG_DBG), true)
    $(warning "Making debug build.")
    LOCAL_CFLAGS += -DFLAG_DBG
    LOCAL_SHARED_LIBRARIES := libcutils libutils
endif

include $(BUILD_SHARED_LIBRARY)
