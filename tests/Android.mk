LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := shared

LOCAL_JAVA_LIBRARIES := android.test.runner

# Do not compress dictionary files to mmap dict data runtime
LOCAL_AAPT_FLAGS += -0 .dict
# Do not compress test data file
LOCAL_AAPT_FLAGS += -0 .txt

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := LatinIMETests

LOCAL_INSTRUMENTATION_FOR := LatinIME

include $(BUILD_PACKAGE)
