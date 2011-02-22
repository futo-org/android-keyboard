/*
**
** Copyright 2011, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "LatinIME: jni: ProximityInfo"

#include "com_android_inputmethod_keyboard_ProximityInfo.h"
#include "jni.h"
#include "proximity_info.h"

#include <assert.h>
#include <errno.h>
#include <stdio.h>

// ----------------------------------------------------------------------------

namespace latinime {

//
// helper function to throw an exception
//
static void throwException(JNIEnv *env, const char* ex, const char* fmt, int data) {
    if (jclass cls = env->FindClass(ex)) {
        char msg[1000];
        snprintf(msg, sizeof(msg), fmt, data);
        env->ThrowNew(cls, msg);
        env->DeleteLocalRef(cls);
    }
}

static jint latinime_Keyboard_setProximityInfo(JNIEnv *env, jobject object,
        jint maxProximityCharsSize, jint displayWidth, jint displayHeight, jint gridWidth,
        jint gridHeight, jintArray proximityCharsArray) {
    jint* proximityChars = env->GetIntArrayElements(proximityCharsArray, NULL);
    ProximityInfo *proximityInfo = new ProximityInfo(maxProximityCharsSize, displayWidth,
            displayHeight, gridWidth, gridHeight, (const uint32_t *)proximityChars);
    env->ReleaseIntArrayElements(proximityCharsArray, proximityChars, 0);
    return (jint)proximityInfo;
}

static void latinime_Keyboard_release(JNIEnv *env, jobject object, jint proximityInfo) {
    ProximityInfo *pi = (ProximityInfo*)proximityInfo;
    if (!pi) return;
    delete pi;
}

// ----------------------------------------------------------------------------

static JNINativeMethod sKeyboardMethods[] = {
    {"setProximityInfoNative", "(IIIII[I)I", (void*)latinime_Keyboard_setProximityInfo},
    {"releaseProximityInfoNative", "(I)V", (void*)latinime_Keyboard_release}
};

static int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* gMethods,
        int numMethods) {
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

int register_ProximityInfo(JNIEnv *env) {
    const char* const kClassPathName = "com/android/inputmethod/keyboard/ProximityInfo";
    return registerNativeMethods(env, kClassPathName, sKeyboardMethods,
            sizeof(sKeyboardMethods) / sizeof(sKeyboardMethods[0]));
}

}; // namespace latinime
