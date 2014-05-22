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

#ifndef LATINIME_UNIGRAM_PROPERTY_H
#define LATINIME_UNIGRAM_PROPERTY_H

#include <vector>

#include "defines.h"

namespace latinime {

class UnigramProperty {
 public:
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
        // Default copy constructor and assign operator are used for using in std::vector.
        DISALLOW_DEFAULT_CONSTRUCTOR(ShortcutProperty);

        // TODO: Make members const.
        std::vector<int> mTargetCodePoints;
        int mProbability;
    };

    UnigramProperty()
            : mRepresentsBeginningOfSentence(false), mIsNotAWord(false), mIsBlacklisted(false),
              mProbability(NOT_A_PROBABILITY), mTimestamp(NOT_A_TIMESTAMP), mLevel(0), mCount(0),
              mShortcuts() {}

    UnigramProperty(const bool representsBeginningOfSentence, const bool isNotAWord,
            const bool isBlacklisted, const int probability, const int timestamp, const int level,
            const int count, const std::vector<ShortcutProperty> *const shortcuts)
            : mRepresentsBeginningOfSentence(representsBeginningOfSentence),
              mIsNotAWord(isNotAWord), mIsBlacklisted(isBlacklisted), mProbability(probability),
              mTimestamp(timestamp), mLevel(level), mCount(count), mShortcuts(*shortcuts) {}

    bool representsBeginningOfSentence() const {
        return mRepresentsBeginningOfSentence;
    }

    bool isNotAWord() const {
        return mIsNotAWord;
    }

    bool isBlacklisted() const {
        return mIsBlacklisted;
    }

    bool hasShortcuts() const {
        return !mShortcuts.empty();
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

    const std::vector<ShortcutProperty> &getShortcuts() const {
        return mShortcuts;
    }

 private:
    // Default copy constructor is used for using as a return value.
    DISALLOW_ASSIGNMENT_OPERATOR(UnigramProperty);

    // TODO: Make members const.
    bool mRepresentsBeginningOfSentence;
    bool mIsNotAWord;
    bool mIsBlacklisted;
    int mProbability;
    // Historical information
    int mTimestamp;
    int mLevel;
    int mCount;
    std::vector<ShortcutProperty> mShortcuts;
};
} // namespace latinime
#endif // LATINIME_UNIGRAM_PROPERTY_H
