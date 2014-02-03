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

#ifndef LATINIME_WORD_PROPERTY_H
#define LATINIME_WORD_PROPERTY_H

#include <cstring>
#include <vector>

#include "defines.h"
#include "jni.h"

namespace latinime {

// This class is used for returning information belonging to a word to java side.
class WordProperty {
 public:
    class BigramProperty {
     public:
        BigramProperty(const std::vector<int> *const targetCodePoints,
                const int probability, const int timestamp, const int level, const int count)
                : mTargetCodePoints(*targetCodePoints), mProbability(probability),
                  mTimestamp(timestamp), mLevel(level), mCount(count) {}

        const std::vector<int> *getTargetCodePoints() const {
            return &mTargetCodePoints;
        }

        int getProbability() const {
            return mProbability;
        }

        int getTimestamp() const {
            return mTimestamp;
        }

        int getLevel() const {
            return mLevel;
        }

        int getCount() const {
            return mCount;
        }

     private:
        std::vector<int> mTargetCodePoints;
        int mProbability;
        int mTimestamp;
        int mLevel;
        int mCount;
    };

    class ShortcutProperty {
     public:
        ShortcutProperty(const std::vector<int> *const targetCodePoints, const int probability)
                : mTargetCodePoints(*targetCodePoints), mProbability(probability) {}

        const std::vector<int> *getTargetCodePoints() const {
            return &mTargetCodePoints;
        }

        int getProbability() const {
            return mProbability;
        }

     private:
        std::vector<int> mTargetCodePoints;
        int mProbability;
    };

    // Invalid word.
    WordProperty()
            : mCodePoints(), mIsNotAWord(false), mIsBlacklisted(false),
              mHasBigrams(false), mHasShortcuts(false), mProbability(NOT_A_PROBABILITY),
              mTimestamp(0), mLevel(0), mCount(0), mBigrams(), mShortcuts() {}

    WordProperty(const std::vector<int> *const codePoints,
            const bool isNotAWord, const bool isBlacklisted, const bool hasBigrams,
            const bool hasShortcuts, const int probability, const int timestamp,
            const int level, const int count, const std::vector<BigramProperty> *const bigrams,
            const std::vector<ShortcutProperty> *const shortcuts)
            : mCodePoints(*codePoints), mIsNotAWord(isNotAWord), mIsBlacklisted(isBlacklisted),
              mHasBigrams(hasBigrams), mHasShortcuts(hasShortcuts), mProbability(probability),
              mTimestamp(timestamp), mLevel(level), mCount(count), mBigrams(*bigrams),
              mShortcuts(*shortcuts) {}

    void outputProperties(JNIEnv *const env, jintArray outCodePoints, jbooleanArray outFlags,
            jintArray outProbabilityInfo, jobject outBigramTargets, jobject outBigramProbabilities,
            jobject outShortcutTargets, jobject outShortcutProbabilities) const;

 private:
    DISALLOW_ASSIGNMENT_OPERATOR(WordProperty);

    std::vector<int> mCodePoints;
    bool mIsNotAWord;
    bool mIsBlacklisted;
    bool mHasBigrams;
    bool mHasShortcuts;
    int mProbability;
    // Historical information
    int mTimestamp;
    int mLevel;
    int mCount;
    std::vector<BigramProperty> mBigrams;
    std::vector<ShortcutProperty> mShortcuts;
};
} // namespace latinime
#endif // LATINIME_WORD_PROPERTY_H
