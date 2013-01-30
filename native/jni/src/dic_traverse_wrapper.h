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

#ifndef LATINIME_DIC_TRAVERSE_WRAPPER_H
#define LATINIME_DIC_TRAVERSE_WRAPPER_H

#include "defines.h"
#include "jni.h"

namespace latinime {
class Dictionary;
// TODO: Remove
class DicTraverseWrapper {
 public:
    static void *getDicTraverseSession(JNIEnv *env, jstring locale) {
        if (sDicTraverseSessionFactoryMethod) {
            return sDicTraverseSessionFactoryMethod(env, locale);
        }
        return 0;
    }
    static void initDicTraverseSession(void *traverseSession, const Dictionary *const dictionary,
            const int *prevWord, const int prevWordLength) {
        if (sDicTraverseSessionInitMethod) {
            sDicTraverseSessionInitMethod(traverseSession, dictionary, prevWord, prevWordLength);
        }
    }
    static void releaseDicTraverseSession(void *traverseSession) {
        if (sDicTraverseSessionReleaseMethod) {
            sDicTraverseSessionReleaseMethod(traverseSession);
        }
    }
    static void setTraverseSessionFactoryMethod(void *(*factoryMethod)(JNIEnv *, jstring)) {
        sDicTraverseSessionFactoryMethod = factoryMethod;
    }
    static void setTraverseSessionInitMethod(
            void (*initMethod)(void *, const Dictionary *const, const int *, const int)) {
        sDicTraverseSessionInitMethod = initMethod;
    }
    static void setTraverseSessionReleaseMethod(void (*releaseMethod)(void *)) {
        sDicTraverseSessionReleaseMethod = releaseMethod;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicTraverseWrapper);
    static void *(*sDicTraverseSessionFactoryMethod)(JNIEnv *, jstring);
    static void (*sDicTraverseSessionInitMethod)(
            void *, const Dictionary *const, const int *, const int);
    static void (*sDicTraverseSessionReleaseMethod)(void *);
};
} // namespace latinime
#endif // LATINIME_DIC_TRAVERSE_WRAPPER_H
