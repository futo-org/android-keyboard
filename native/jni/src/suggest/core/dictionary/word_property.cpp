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

#include "suggest/core/dictionary/word_property.h"

namespace latinime {

void WordProperty::outputProperties(JNIEnv *const env, jintArray outCodePoints,
        jbooleanArray outFlags, jintArray outProbabilityInfo, jobject outBigramTargets,
        jobject outBigramProbabilities, jobject outShortcutTargets,
        jobject outShortcutProbabilities) const {
    env->SetIntArrayRegion(outCodePoints, 0 /* start */, mCodePoints.size(), &mCodePoints[0]);
    jboolean flags[] = {mIsNotAWord, mIsBlacklisted, mHasBigrams, mHasShortcuts};
    env->SetBooleanArrayRegion(outFlags, 0 /* start */, NELEMS(flags), flags);
    int probabilityInfo[] = {mProbability, mTimestamp, mLevel, mCount};
    env->SetIntArrayRegion(outProbabilityInfo, 0 /* start */, NELEMS(probabilityInfo),
            probabilityInfo);

    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID intToIntegerConstructorId = env->GetMethodID(integerClass, "<init>", "(I)V");
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID addMethodId = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // TODO: Output bigrams.
    // Output shortcuts.
    const int shortcutTargetCount = mShortcuts.size();
    for (int i = 0; i < shortcutTargetCount; ++i) {
        const std::vector<int> *const targetCodePoints = mShortcuts[i].getTargetCodePoints();
        jintArray shortcutTargetCodePointArray = env->NewIntArray(targetCodePoints->size());
        env->SetIntArrayRegion(shortcutTargetCodePointArray, 0 /* start */,
                targetCodePoints->size(), &targetCodePoints->at(0));
        env->CallVoidMethod(outShortcutTargets, addMethodId, shortcutTargetCodePointArray);
        env->DeleteLocalRef(shortcutTargetCodePointArray);
        jobject integerProbability = env->NewObject(integerClass, intToIntegerConstructorId,
                mShortcuts[i].getProbability());
        env->CallVoidMethod(outShortcutProbabilities, addMethodId, integerProbability);
        env->DeleteLocalRef(integerProbability);
    }
    env->DeleteLocalRef(integerClass);
    env->DeleteLocalRef(arrayListClass);
}

} // namespace latinime
