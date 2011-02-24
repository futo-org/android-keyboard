LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src

LOCAL_SRC_FILES := \
    jni/com_android_inputmethod_keyboard_ProximityInfo.cpp \
    jni/com_android_inputmethod_latin_BinaryDictionary.cpp \
    jni/onload.cpp \
    src/bigram_dictionary.cpp \
    src/char_utils.cpp \
    src/dictionary.cpp \
    src/proximity_info.cpp \
    src/unigram_dictionary.cpp

#FLAG_DBG := true

TARGETING_UNBUNDLED_FROYO := true

ifeq ($(TARGET_ARCH), x86)
    TARGETING_UNBUNDLED_FROYO := false
endif

ifeq ($(FLAG_DBG), true)
    TARGETING_UNBUNDLED_FROYO := false
endif

ifeq ($(TARGETING_UNBUNDLED_FROYO), true)
    LOCAL_NDK_VERSION := 4
    LOCAL_SDK_VERSION := 8
endif

LOCAL_MODULE := libjni_latinime

LOCAL_MODULE_TAGS := user

ifeq ($(FLAG_DBG), true)
    $(warning Making debug version of native library)
    LOCAL_CFLAGS += -DFLAG_DBG
    LOCAL_SHARED_LIBRARIES := libcutils libutils
endif

include $(BUILD_SHARED_LIBRARY)
