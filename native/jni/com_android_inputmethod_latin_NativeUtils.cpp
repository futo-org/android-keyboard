/*
**
** Copyright 2012, The Android Open Source Project
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

#include "com_android_inputmethod_latin_NativeUtils.h"
#include "jni.h"
#include "jni_common.h"

#include <math.h>

namespace latinime {

static float latinime_NativeUtils_powf(float x, float y) {
    return powf(x, y);
}

static JNINativeMethod sMethods[] = {
    {"powf", "(FF)F", (void*)latinime_NativeUtils_powf}
};

int register_NativeUtils(JNIEnv *env) {
    const char* const kClassPathName = "com/android/inputmethod/latin/NativeUtils";
    return registerNativeMethods(env, kClassPathName, sMethods,
            sizeof(sMethods) / sizeof(sMethods[0]));
}

} // namespace latinime
