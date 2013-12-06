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

#ifndef LATINIME_UNIGRAM_PROPERTY_H
#define LATINIME_UNIGRAM_PROPERTY_H

#include <vector>

#include "defines.h"
#include "jni.h"

namespace latinime {

// This class is used for returning information belonging to a unigram to java side.
class UnigramProperty {
 public:
    // Invalid unigram.
    UnigramProperty()
            : mCodePoints(), mCodePointCount(0), mIsNotAWord(false), mIsBlacklisted(false),
              mHasBigrams(false), mHasShortcuts(false), mProbability(NOT_A_PROBABILITY),
              mTimestamp(0), mLevel(0), mCount(0), mShortcutTargets(),
              mShortcutProbabilities() {}

    UnigramProperty(const UnigramProperty &unigramProperty)
            : mCodePoints(), mCodePointCount(unigramProperty.mCodePointCount),
              mIsNotAWord(unigramProperty.mIsNotAWord),
              mIsBlacklisted(unigramProperty.mIsBlacklisted),
              mHasBigrams(unigramProperty.mHasBigrams),
              mHasShortcuts(unigramProperty.mHasShortcuts),
              mProbability(unigramProperty.mProbability),
              mTimestamp(unigramProperty.mTimestamp), mLevel(unigramProperty.mLevel),
              mCount(unigramProperty.mCount), mShortcutTargets(unigramProperty.mShortcutTargets),
              mShortcutProbabilities(unigramProperty.mShortcutProbabilities) {
        memcpy(mCodePoints, unigramProperty.mCodePoints, sizeof(mCodePoints));
    }

    UnigramProperty(const int *const codePoints, const int codePointCount,
            const bool isNotAWord, const bool isBlacklisted, const bool hasBigrams,
            const bool hasShortcuts, const int probability, const int timestamp,
            const int level, const int count,
            const std::vector<std::vector<int> > *const shortcutTargets,
            const std::vector<int> *const shortcutProbabilities)
            : mCodePoints(), mCodePointCount(codePointCount),
              mIsNotAWord(isNotAWord), mIsBlacklisted(isBlacklisted), mHasBigrams(hasBigrams),
              mHasShortcuts(hasShortcuts), mProbability(probability), mTimestamp(timestamp),
              mLevel(level), mCount(count), mShortcutTargets(*shortcutTargets),
              mShortcutProbabilities(*shortcutProbabilities) {
        memcpy(mCodePoints, codePoints, sizeof(mCodePoints));
    }

    void outputProperties(JNIEnv *const env, jintArray outCodePoints, jbooleanArray outFlags,
            jintArray outProbability, jintArray outHistoricalInfo, jobject outShortcutTargets,
            jobject outShortcutProbabilities) const;

 private:
    DISALLOW_ASSIGNMENT_OPERATOR(UnigramProperty);

    int mCodePoints[MAX_WORD_LENGTH];
    int mCodePointCount;
    bool mIsNotAWord;
    bool mIsBlacklisted;
    bool mHasBigrams;
    bool mHasShortcuts;
    int mProbability;
    // Historical information
    int mTimestamp;
    int mLevel;
    int mCount;
    // Shortcut
    std::vector<std::vector<int> > mShortcutTargets;
    std::vector<int> mShortcutProbabilities;
};
} // namespace latinime
#endif // LATINIME_UNIGRAM_PROPERTY_H
