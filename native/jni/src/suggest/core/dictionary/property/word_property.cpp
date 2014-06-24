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

#include "suggest/core/dictionary/property/word_property.h"

#include "utils/jni_data_utils.h"

namespace latinime {

void WordProperty::outputProperties(JNIEnv *const env, jintArray outCodePoints,
        jbooleanArray outFlags, jintArray outProbabilityInfo, jobject outBigramTargets,
        jobject outBigramProbabilities, jobject outShortcutTargets,
        jobject outShortcutProbabilities) const {
    JniDataUtils::outputCodePoints(env, outCodePoints, 0 /* start */,
            MAX_WORD_LENGTH /* maxLength */, mCodePoints.data(), mCodePoints.size(),
            false /* needsNullTermination */);
    jboolean flags[] = {mUnigramProperty.isNotAWord(), mUnigramProperty.isBlacklisted(),
            !mBigrams.empty(), mUnigramProperty.hasShortcuts(),
            mUnigramProperty.representsBeginningOfSentence()};
    env->SetBooleanArrayRegion(outFlags, 0 /* start */, NELEMS(flags), flags);
    int probabilityInfo[] = {mUnigramProperty.getProbability(), mUnigramProperty.getTimestamp(),
            mUnigramProperty.getLevel(), mUnigramProperty.getCount()};
    env->SetIntArrayRegion(outProbabilityInfo, 0 /* start */, NELEMS(probabilityInfo),
            probabilityInfo);

    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID intToIntegerConstructorId = env->GetMethodID(integerClass, "<init>", "(I)V");
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID addMethodId = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // Output bigrams.
    for (const auto &bigramProperty : mBigrams) {
        const std::vector<int> *const word1CodePoints = bigramProperty.getTargetCodePoints();
        jintArray bigramWord1CodePointArray = env->NewIntArray(word1CodePoints->size());
        JniDataUtils::outputCodePoints(env, bigramWord1CodePointArray, 0 /* start */,
                word1CodePoints->size(), word1CodePoints->data(), word1CodePoints->size(),
                false /* needsNullTermination */);
        env->CallBooleanMethod(outBigramTargets, addMethodId, bigramWord1CodePointArray);
        env->DeleteLocalRef(bigramWord1CodePointArray);

        int bigramProbabilityInfo[] = {bigramProperty.getProbability(),
                bigramProperty.getTimestamp(), bigramProperty.getLevel(),
                bigramProperty.getCount()};
        jintArray bigramProbabilityInfoArray = env->NewIntArray(NELEMS(bigramProbabilityInfo));
        env->SetIntArrayRegion(bigramProbabilityInfoArray, 0 /* start */,
                NELEMS(bigramProbabilityInfo), bigramProbabilityInfo);
        env->CallBooleanMethod(outBigramProbabilities, addMethodId, bigramProbabilityInfoArray);
        env->DeleteLocalRef(bigramProbabilityInfoArray);
    }

    // Output shortcuts.
    for (const auto &shortcut : mUnigramProperty.getShortcuts()) {
        const std::vector<int> *const targetCodePoints = shortcut.getTargetCodePoints();
        jintArray shortcutTargetCodePointArray = env->NewIntArray(targetCodePoints->size());
        env->SetIntArrayRegion(shortcutTargetCodePointArray, 0 /* start */,
                targetCodePoints->size(), targetCodePoints->data());
        JniDataUtils::outputCodePoints(env, shortcutTargetCodePointArray, 0 /* start */,
                targetCodePoints->size(), targetCodePoints->data(), targetCodePoints->size(),
                false /* needsNullTermination */);
        env->CallBooleanMethod(outShortcutTargets, addMethodId, shortcutTargetCodePointArray);
        env->DeleteLocalRef(shortcutTargetCodePointArray);
        jobject integerProbability = env->NewObject(integerClass, intToIntegerConstructorId,
                shortcut.getProbability());
        env->CallBooleanMethod(outShortcutProbabilities, addMethodId, integerProbability);
        env->DeleteLocalRef(integerProbability);
    }
    env->DeleteLocalRef(integerClass);
    env->DeleteLocalRef(arrayListClass);
}

} // namespace latinime
