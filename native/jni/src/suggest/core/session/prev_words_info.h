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

#ifndef LATINIME_PREV_WORDS_INFO_H
#define LATINIME_PREV_WORDS_INFO_H

#include "defines.h"

namespace latinime {

// TODO: Support n-gram.
// TODO: Support beginning of sentence.
// This class does not take ownership of any code point buffers.
class PrevWordsInfo {
 public:
    // No prev word information.
    PrevWordsInfo()
            : mPrevWordCodePoints(nullptr), mPrevWordCodePointCount(0) {}

    PrevWordsInfo(const int *const prevWordCodePoints, const int prevWordCodePointCount,
            const bool isBeginningOfSentence)
            : mPrevWordCodePoints(prevWordCodePoints),
              mPrevWordCodePointCount(prevWordCodePointCount) {}

    const int *getPrevWordCodePoints() const {
        return mPrevWordCodePoints;
    }

    int getPrevWordCodePointCount() const {
        return mPrevWordCodePointCount;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(PrevWordsInfo);

    const int *const mPrevWordCodePoints;
    const int mPrevWordCodePointCount;
};
} // namespace latinime
#endif // LATINIME_PREV_WORDS_INFO_H
