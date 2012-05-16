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

#define LOG_TAG "LatinIME: jni"

#include "com_android_inputmethod_keyboard_ProximityInfo.h"
#include "com_android_inputmethod_latin_BinaryDictionary.h"
#include "defines.h"
#include "jni.h"
#include "proximity_info.h"

#include <assert.h>
#include <errno.h>
#include <stdio.h>

using namespace latinime;

/*
 * Returns the JNI version on success, -1 on failure.
 */
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = 0;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        AKLOGE("ERROR: GetEnv failed");
        goto bail;
    }
    assert(env != 0);

    if (!register_BinaryDictionary(env)) {
        AKLOGE("ERROR: BinaryDictionary native registration failed");
        goto bail;
    }

    if (!register_ProximityInfo(env)) {
        AKLOGE("ERROR: ProximityInfo native registration failed");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}

namespace latinime {

int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* methods,
        int numMethods) {
    jclass clazz = env->FindClass(className);
    if (clazz == 0) {
        AKLOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) < 0) {
        AKLOGE("RegisterNatives failed for '%s'", className);
        env->DeleteLocalRef(clazz);
        return JNI_FALSE;
    }
    env->DeleteLocalRef(clazz);
    return JNI_TRUE;
}

} // namespace latinime
