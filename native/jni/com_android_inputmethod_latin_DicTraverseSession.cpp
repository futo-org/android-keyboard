/*
 * Copyright (C) 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "LatinIME: jni: Session"

#include "com_android_inputmethod_latin_DicTraverseSession.h"
#include "jni.h"
#include "jni_common.h"

namespace latinime {
void *(*DicTraverseWrapper::sDicTraverseSessionFactoryMethod)() = 0;
void (*DicTraverseWrapper::sDicTraverseSessionReleaseMethod)(void *) = 0;

static jlong latinime_setDicTraverseSession(JNIEnv *env, jobject object,
        jstring localejStr) {
    void *session = DicTraverseWrapper::getDicTraverseSession();
    return reinterpret_cast<jlong>(session);
}

static void latinime_DicTraverseSession_release(JNIEnv *env, jobject object, jlong session) {
    void *pi = reinterpret_cast<void*>(session);
    if (!pi) return;
    DicTraverseWrapper::releaseDicTraverseSession(pi);
}

static JNINativeMethod sMethods[] = {
    {"setDicTraverseSessionNative", "(Ljava/lang/String;)J", (void*)latinime_setDicTraverseSession},
    {"releaseDicTraverseSessionNative", "(J)V", (void*)latinime_DicTraverseSession_release}
};

int register_DicTraverseSession(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/DicTraverseSession";
    return registerNativeMethods(env, kClassPathName, sMethods,
            sizeof(sMethods) / sizeof(sMethods[0]));
}
} // namespace latinime
