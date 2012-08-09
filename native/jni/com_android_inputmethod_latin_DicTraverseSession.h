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

#ifndef _COM_ANDROID_INPUTMETHOD_LATIN_DICTRAVERSESESSION_H
#define _COM_ANDROID_INPUTMETHOD_LATIN_DICTRAVERSESESSION_H

#include "defines.h"
#include "jni.h"

namespace latinime {

// TODO: Remove
class DicTraverseWrapper {
 public:
    static void *getDicTraverseSession() {
        if (sDicTraverseSessionFactoryMethod) {
            return sDicTraverseSessionFactoryMethod();
        }
        return 0;
    }
    static void initDicTraverseSession(JNIEnv *env, void *traverseSession,
            const jintArray prevWord, const jint prevWordLength) {
        if (sDicTraverseSessionInitMethod) {
            sDicTraverseSessionInitMethod(env, traverseSession, prevWord, prevWordLength);
        }
    }
    static void releaseDicTraverseSession(void *traverseSession) {
        if (sDicTraverseSessionReleaseMethod) {
            sDicTraverseSessionReleaseMethod(traverseSession);
        }
    }
 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicTraverseWrapper);
    static void *(*sDicTraverseSessionFactoryMethod)();
    static void (*sDicTraverseSessionInitMethod)(JNIEnv *, void *, const jintArray, const jint);
    static void (*sDicTraverseSessionReleaseMethod)(void *);
};
int register_DicTraverseSession(JNIEnv *env);
} // namespace latinime
#endif // _COM_ANDROID_INPUTMETHOD_LATIN_DICTRAVERSESESSION_H
