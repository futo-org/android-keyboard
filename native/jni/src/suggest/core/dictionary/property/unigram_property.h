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
#include "suggest/core/dictionary/property/historical_info.h"

namespace latinime {

class UnigramProperty {
 public:
    class ShortcutProperty {
     public:
        ShortcutProperty(const std::vector<int> &&targetCodePoints, const int probability)
                : mTargetCodePoints(std::move(targetCodePoints)),
                  mProbability(probability) {}

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
              mProbability(NOT_A_PROBABILITY), mHistoricalInfo(), mShortcuts() {}

    UnigramProperty(const bool representsBeginningOfSentence, const bool isNotAWord,
            const bool isBlacklisted, const int probability, const HistoricalInfo historicalInfo,
            const std::vector<ShortcutProperty> &&shortcuts)
            : mRepresentsBeginningOfSentence(representsBeginningOfSentence),
              mIsNotAWord(isNotAWord), mIsBlacklisted(isBlacklisted), mProbability(probability),
              mHistoricalInfo(historicalInfo), mShortcuts(std::move(shortcuts)) {}

    // Without shortcuts.
    UnigramProperty(const bool representsBeginningOfSentence, const bool isNotAWord,
            const bool isBlacklisted, const int probability, const HistoricalInfo historicalInfo)
            : mRepresentsBeginningOfSentence(representsBeginningOfSentence),
              mIsNotAWord(isNotAWord), mIsBlacklisted(isBlacklisted), mProbability(probability),
              mHistoricalInfo(historicalInfo), mShortcuts() {}

    bool representsBeginningOfSentence() const {
        return mRepresentsBeginningOfSentence;
    }

    bool isNotAWord() const {
        return mIsNotAWord;
    }

    bool isBlacklisted() const {
        return mIsBlacklisted;
    }

    bool isPossiblyOffensive() const {
        // TODO: Have dedicated flag.
        return mProbability == 0;
    }

    bool hasShortcuts() const {
        return !mShortcuts.empty();
    }

    int getProbability() const {
        return mProbability;
    }

    const HistoricalInfo getHistoricalInfo() const {
        return mHistoricalInfo;
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
    HistoricalInfo mHistoricalInfo;
    std::vector<ShortcutProperty> mShortcuts;
};
} // namespace latinime
#endif // LATINIME_UNIGRAM_PROPERTY_H
