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

#include "dic_traverse_wrapper.h"

namespace latinime {
void *(*DicTraverseWrapper::sDicTraverseSessionFactoryMethod)(JNIEnv *, jstring) = 0;
void (*DicTraverseWrapper::sDicTraverseSessionReleaseMethod)(void *) = 0;
void (*DicTraverseWrapper::sDicTraverseSessionInitMethod)(
        void *, const Dictionary *const, const int *, const int) = 0;
} // namespace latinime
