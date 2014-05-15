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
    PrevWordsInfo() {
        clear();
    }

    PrevWordsInfo(const int *const prevWordCodePoints, const int prevWordCodePointCount,
            const bool isBeginningOfSentence) {
        clear();
        mPrevWordCodePoints[0] = prevWordCodePoints;
        mPrevWordCodePointCount[0] = prevWordCodePointCount;
        mIsBeginningOfSentence[0] = isBeginningOfSentence;
    }
    const int *getPrevWordCodePoints() const {
        return mPrevWordCodePoints[0];
    }

    int getPrevWordCodePointCount() const {
        return mPrevWordCodePointCount[0];
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(PrevWordsInfo);

    void clear() {
        for (size_t i = 0; i < NELEMS(mPrevWordCodePoints); ++i) {
            mPrevWordCodePoints[i] = nullptr;
            mPrevWordCodePointCount[i] = 0;
            mIsBeginningOfSentence[i] = false;
        }
    }

    const int *mPrevWordCodePoints[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    int mPrevWordCodePointCount[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    bool mIsBeginningOfSentence[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
};
} // namespace latinime
#endif // LATINIME_PREV_WORDS_INFO_H
