/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "LatinIME: jni: Ver3DictDecoder"

#include "com_android_inputmethod_latin_makedict_Ver3DictDecoder.h"

#include "defines.h"
#include "jni.h"
#include "jni_common.h"

namespace latinime {
static int latinime_Ver3DictDecoder_doNothing(JNIEnv *env, jclass clazz) {
    // This is a phony method for test - it does nothing. It just returns some value
    // unlikely to be in memory by chance for testing purposes.
    // TODO: remove this method.
    return 2097;
}

static const JNINativeMethod sMethods[] = {
    {
        // TODO: remove this entry when we have one useful method in here
        const_cast<char *>("doNothing"),
        const_cast<char *>("()I"),
        reinterpret_cast<void *>(latinime_Ver3DictDecoder_doNothing)
    },
};

int register_Ver3DictDecoder(JNIEnv *env) {
    const char *const kClassPathName =
            "com/android/inputmethod/latin/makedict/Ver3DictDecoder";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
