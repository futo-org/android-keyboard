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
        jintArray keyHeightArray, jintArray keyCharCodeArray,
        jfloatArray sweetSpotCenterXArray, jfloatArray sweetSpotCenterYArray,
        jfloatArray sweetSpotRadiusArray) {
    jint *proximityChars = env->GetIntArrayElements(proximityCharsArray, NULL);
    jint *keyXCoordinates = safeGetIntArrayElements(env, keyXCoordinateArray);
    jint *keyYCoordinates = safeGetIntArrayElements(env, keyYCoordinateArray);
    jint *keyWidths = safeGetIntArrayElements(env, keyWidthArray);
    jint *keyHeights = safeGetIntArrayElements(env, keyHeightArray);
    jint *keyCharCodes = safeGetIntArrayElements(env, keyCharCodeArray);
    jfloat *sweetSpotCenterXs = safeGetFloatArrayElements(env, sweetSpotCenterXArray);
    jfloat *sweetSpotCenterYs = safeGetFloatArrayElements(env, sweetSpotCenterYArray);
    jfloat *sweetSpotRadii = safeGetFloatArrayElements(env, sweetSpotRadiusArray);
    ProximityInfo *proximityInfo = new ProximityInfo(maxProximityCharsSize, displayWidth,
            displayHeight, gridWidth, gridHeight, (const uint32_t*)proximityChars,
            keyCount, (const int32_t*)keyXCoordinates, (const int32_t*)keyYCoordinates,
            (const int32_t*)keyWidths, (const int32_t*)keyHeights, (const int32_t*)keyCharCodes,
            (const float*)sweetSpotCenterXs, (const float*)sweetSpotCenterYs,
            (const float*)sweetSpotRadii);
    safeReleaseFloatArrayElements(env, sweetSpotRadiusArray, sweetSpotRadii);
    safeReleaseFloatArrayElements(env, sweetSpotCenterYArray, sweetSpotCenterYs);
    safeReleaseFloatArrayElements(env, sweetSpotCenterXArray, sweetSpotCenterXs);
    safeReleaseIntArrayElements(env, keyCharCodeArray, keyCharCodes);
    safeReleaseIntArrayElements(env, keyHeightArray, keyHeights);
    safeReleaseIntArrayElements(env, keyWidthArray, keyWidths);
    safeReleaseIntArrayElements(env, keyYCoordinateArray, keyYCoordinates);
    safeReleaseIntArrayElements(env, keyXCoordinateArray, keyXCoordinates);
    env->ReleaseIntArrayElements(proximityCharsArray, proximityChars, 0);
    return (jint)proximityInfo;
}

static void latinime_Keyboard_release(JNIEnv *env, jobject object, jint proximityInfo) {
    ProximityInfo *pi = (ProximityInfo*)proximityInfo;
    if (!pi) return;
    delete pi;
}

static JNINativeMethod sKeyboardMethods[] = {
    {"setProximityInfoNative", "(IIIII[II[I[I[I[I[I[F[F[F)I",
            (void*)latinime_Keyboard_setProximityInfo},
    {"releaseProximityInfoNative", "(I)V", (void*)latinime_Keyboard_release}
};

int register_ProximityInfo(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/keyboard/ProximityInfo";
    return registerNativeMethods(env, kClassPathName, sKeyboardMethods,
            sizeof(sKeyboardMethods) / sizeof(sKeyboardMethods[0]));
}

} // namespace latinime
