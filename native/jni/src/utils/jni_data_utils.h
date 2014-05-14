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
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/policyimpl/dictionary/header/header_read_write_utils.h"
#include "utils/char_utils.h"

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

    static DictionaryHeaderStructurePolicy::AttributeMap constructAttributeMap(JNIEnv *env,
            jobjectArray attributeKeyStringArray, jobjectArray attributeValueStringArray) {
        DictionaryHeaderStructurePolicy::AttributeMap attributeMap;
        const int keyCount = env->GetArrayLength(attributeKeyStringArray);
        for (int i = 0; i < keyCount; i++) {
            jstring keyString = static_cast<jstring>(
                    env->GetObjectArrayElement(attributeKeyStringArray, i));
            const jsize keyUtf8Length = env->GetStringUTFLength(keyString);
            char keyChars[keyUtf8Length + 1];
            env->GetStringUTFRegion(keyString, 0, env->GetStringLength(keyString), keyChars);
            keyChars[keyUtf8Length] = '\0';
            DictionaryHeaderStructurePolicy::AttributeMap::key_type key;
            HeaderReadWriteUtils::insertCharactersIntoVector(keyChars, &key);

            jstring valueString = static_cast<jstring>(
                    env->GetObjectArrayElement(attributeValueStringArray, i));
            const jsize valueUtf8Length = env->GetStringUTFLength(valueString);
            char valueChars[valueUtf8Length + 1];
            env->GetStringUTFRegion(valueString, 0, env->GetStringLength(valueString), valueChars);
            valueChars[valueUtf8Length] = '\0';
            DictionaryHeaderStructurePolicy::AttributeMap::mapped_type value;
            HeaderReadWriteUtils::insertCharactersIntoVector(valueChars, &value);
            attributeMap[key] = value;
        }
        return attributeMap;
    }

    static void outputCodePoints(JNIEnv *env, jintArray intArrayToOutputCodePoints, const int start,
            const int maxLength, const int *const codePoints, const int codePointCount,
            const bool needsNullTermination) {
        const int outputCodePointCount = std::min(maxLength, codePointCount);
        int outputCodePonts[outputCodePointCount];
        for (int i = 0; i < outputCodePointCount; ++i) {
            const int codePoint = codePoints[i];
            if (!CharUtils::isInUnicodeSpace(codePoint)) {
                outputCodePonts[i] = CODE_POINT_REPLACEMENT_CHARACTER;
            } else if (codePoint >= 0x01 && codePoint <= 0x1F) {
                // Control code.
                outputCodePonts[i] = CODE_POINT_REPLACEMENT_CHARACTER;
            } else {
                outputCodePonts[i] = codePoint;
            }
        }
        env->SetIntArrayRegion(intArrayToOutputCodePoints, start, outputCodePointCount,
                outputCodePonts);
        if (needsNullTermination && outputCodePointCount < maxLength) {
            env->SetIntArrayRegion(intArrayToOutputCodePoints, start + outputCodePointCount,
                    1 /* len */, &CODE_POINT_NULL);
        }
    }

    static void putIntToArray(JNIEnv *env, jintArray array, const int index, const int value) {
        env->SetIntArrayRegion(array, index, 1 /* len */, &value);
    }

    static void putFloatToArray(JNIEnv *env, jfloatArray array, const int index,
            const float value) {
        env->SetFloatArrayRegion(array, index, 1 /* len */, &value);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(JniDataUtils);

    static const int CODE_POINT_REPLACEMENT_CHARACTER;
    static const int CODE_POINT_NULL;
};
} // namespace latinime
#endif // LATINIME_JNI_DATA_UTILS_H
