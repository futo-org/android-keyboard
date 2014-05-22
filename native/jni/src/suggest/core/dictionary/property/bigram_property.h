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

#ifndef LATINIME_BIGRAM_PROPERTY_H
#define LATINIME_BIGRAM_PROPERTY_H

#include <vector>

#include "defines.h"

namespace latinime {

// TODO: Change to NgramProperty.
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
    // Default copy constructor and assign operator are used for using in std::vector.
    DISALLOW_DEFAULT_CONSTRUCTOR(BigramProperty);

    // TODO: Make members const.
    std::vector<int> mTargetCodePoints;
    int mProbability;
    int mTimestamp;
    int mLevel;
    int mCount;
};
} // namespace latinime
#endif // LATINIME_WORD_PROPERTY_H
