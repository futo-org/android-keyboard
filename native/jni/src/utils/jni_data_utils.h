/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef LATINIME_JNI_DATA_UTILS_H
#define LATINIME_JNI_DATA_UTILS_H

#include <vector>

#include "defines.h"
#include "jni.h"

namespace latinime {

class JniDataUtils {
 public:
    static void jintarrayToVector(JNIEnv *env, jintArray array, std::vector<int> *const outVector) {
        if (!array) {
            outVector->clear();
            return;
        }
        const jsize arrayLength = env->GetArrayLength(array);
        outVector->resize(arrayLength);
        env->GetIntArrayRegion(array, 0 /* start */, arrayLength, outVector->data());
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(JniDataUtils);
};
} // namespace latinime
#endif // LATINIME_JNI_DATA_UTILS_H
