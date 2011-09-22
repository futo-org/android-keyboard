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
#include "jni_common.h"
#include "proximity_info.h"

#include <assert.h>
#include <errno.h>
#include <stdio.h>

namespace latinime {

static jint latinime_Keyboard_setProximityInfo(JNIEnv *env, jobject object,
        jint maxProximityCharsSize, jint displayWidth, jint displayHeight, jint gridWidth,
        jint gridHeight, jintArray proximityCharsArray, jint keyCount,
        jintArray keyXCoordinateArray, jintArray keyYCoordinateArray, jintArray keyWidthArray,
        jintArray keyHeightArray, jintArray keyCharCodeArray) {
    jint* proximityChars = env->GetIntArrayElements(proximityCharsArray, NULL);
    jint* keyXCoordinates = env->GetIntArrayElements(keyXCoordinateArray, NULL);
    jint* keyYCoordinates = env->GetIntArrayElements(keyYCoordinateArray, NULL);
    jint* keyWidths = env->GetIntArrayElements(keyWidthArray, NULL);
    jint* keyHeights = env->GetIntArrayElements(keyHeightArray, NULL);
    jint* keyCharCodes = env->GetIntArrayElements(keyCharCodeArray, NULL);
    ProximityInfo *proximityInfo = new ProximityInfo(maxProximityCharsSize, displayWidth,
            displayHeight, gridWidth, gridHeight, (const uint32_t *)proximityChars,
            keyCount, (const int32_t *)keyXCoordinates, (const int32_t *)keyYCoordinates,
            (const int32_t *)keyWidths, (const int32_t *)keyHeights, (const int32_t *)keyCharCodes);
    env->ReleaseIntArrayElements(keyCharCodeArray, keyCharCodes, 0);
    env->ReleaseIntArrayElements(keyHeightArray, keyHeights, 0);
    env->ReleaseIntArrayElements(keyWidthArray, keyWidths, 0);
    env->ReleaseIntArrayElements(keyYCoordinateArray, keyYCoordinates, 0);
    env->ReleaseIntArrayElements(keyXCoordinateArray, keyXCoordinates, 0);
    env->ReleaseIntArrayElements(proximityCharsArray, proximityChars, 0);
    return (jint)proximityInfo;
}

static void latinime_Keyboard_release(JNIEnv *env, jobject object, jint proximityInfo) {
    ProximityInfo *pi = (ProximityInfo*)proximityInfo;
    if (!pi) return;
    delete pi;
}

static JNINativeMethod sKeyboardMethods[] = {
    {"setProximityInfoNative", "(IIIII[II[I[I[I[I[I)I", (void*)latinime_Keyboard_setProximityInfo},
    {"releaseProximityInfoNative", "(I)V", (void*)latinime_Keyboard_release}
};

int register_ProximityInfo(JNIEnv *env) {
    const char* const kClassPathName = "com/android/inputmethod/keyboard/ProximityInfo";
    return registerNativeMethods(env, kClassPathName, sKeyboardMethods,
            sizeof(sKeyboardMethods) / sizeof(sKeyboardMethods[0]));
}

} // namespace latinime
