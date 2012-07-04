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

#ifndef LATINIME_JNI_COMMON_H
#define LATINIME_JNI_COMMON_H

#include <stdlib.h>

#include "jni.h"

namespace latinime {

int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods,
        int numMethods);

inline jint *safeGetIntArrayElements(JNIEnv *env, jintArray jArray) {
    if (jArray) {
        return env->GetIntArrayElements(jArray, 0);
    } else {
        return 0;
    }
}

inline jfloat *safeGetFloatArrayElements(JNIEnv *env, jfloatArray jArray) {
    if (jArray) {
        return env->GetFloatArrayElements(jArray, 0);
    } else {
        return 0;
    }
}

inline void safeReleaseIntArrayElements(JNIEnv *env, jintArray jArray, jint *cArray) {
    if (jArray) {
        env->ReleaseIntArrayElements(jArray, cArray, 0);
    }
}

inline void safeReleaseFloatArrayElements(JNIEnv *env, jfloatArray jArray, jfloat *cArray) {
    if (jArray) {
        env->ReleaseFloatArrayElements(jArray, cArray, 0);
    }
}

} // namespace latinime

#endif // LATINIME_JNI_COMMON_H
