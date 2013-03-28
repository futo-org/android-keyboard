/*
 * Copyright (C) 2012 The Android Open Source Project
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

#define LOG_TAG "LatinIME: jni: Session"

#include "com_android_inputmethod_latin_DicTraverseSession.h"
#include "defines.h"
#include "dic_traverse_wrapper.h"
#include "jni.h"
#include "jni_common.h"

namespace latinime {
class Dictionary;
static jlong latinime_setDicTraverseSession(JNIEnv *env, jclass clazz, jstring localeJStr) {
    void *traverseSession = DicTraverseWrapper::getDicTraverseSession(env, localeJStr);
    return reinterpret_cast<jlong>(traverseSession);
}

static void latinime_initDicTraverseSession(JNIEnv *env, jclass clazz, jlong traverseSession,
        jlong dictionary, jintArray previousWord, jint previousWordLength) {
    void *ts = reinterpret_cast<void *>(traverseSession);
    Dictionary *dict = reinterpret_cast<Dictionary *>(dictionary);
    if (!previousWord) {
        DicTraverseWrapper::initDicTraverseSession(ts, dict, 0, 0);
        return;
    }
    int prevWord[previousWordLength];
    env->GetIntArrayRegion(previousWord, 0, previousWordLength, prevWord);
    DicTraverseWrapper::initDicTraverseSession(ts, dict, prevWord, previousWordLength);
}

static void latinime_releaseDicTraverseSession(JNIEnv *env, jclass clazz, jlong traverseSession) {
    void *ts = reinterpret_cast<void *>(traverseSession);
    DicTraverseWrapper::releaseDicTraverseSession(ts);
}

static JNINativeMethod sMethods[] = {
    {const_cast<char *>("setDicTraverseSessionNative"),
     const_cast<char *>("(Ljava/lang/String;)J"),
     reinterpret_cast<void *>(latinime_setDicTraverseSession)},
    {const_cast<char *>("initDicTraverseSessionNative"),
     const_cast<char *>("(JJ[II)V"),
     reinterpret_cast<void *>(latinime_initDicTraverseSession)},
    {const_cast<char *>("releaseDicTraverseSessionNative"),
     const_cast<char *>("(J)V"),
     reinterpret_cast<void *>(latinime_releaseDicTraverseSession)}
};

int register_DicTraverseSession(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/DicTraverseSession";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
