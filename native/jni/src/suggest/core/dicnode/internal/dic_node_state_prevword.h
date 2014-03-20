/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_DIC_NODE_STATE_PREVWORD_H
#define LATINIME_DIC_NODE_STATE_PREVWORD_H

#include <algorithm>
#include <cstring> // for memset() and memmove()
#include <stdint.h>

#include "defines.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/layout/proximity_info_state.h"

namespace latinime {

class DicNodeStatePrevWord {
 public:
    AK_FORCE_INLINE DicNodeStatePrevWord()
            : mPrevWordCount(0), mPrevWordLength(0), mPrevWordStart(0),
              mPrevWordPtNodePos(NOT_A_DICT_POS), mSecondWordFirstInputIndex(NOT_AN_INDEX) {}

    ~DicNodeStatePrevWord() {}

    void init(const int prevWordNodePos) {
        mPrevWordLength = 0;
        mPrevWordCount = 0;
        mPrevWordStart = 0;
        mPrevWordPtNodePos = prevWordNodePos;
        mSecondWordFirstInputIndex = NOT_AN_INDEX;
        mPrevWord[0] = 0;
    }

    // Init by copy
    AK_FORCE_INLINE void init(const DicNodeStatePrevWord *const prevWord) {
        mPrevWordLength = prevWord->mPrevWordLength;
        mPrevWordCount = prevWord->mPrevWordCount;
        mPrevWordStart = prevWord->mPrevWordStart;
        mPrevWordPtNodePos = prevWord->mPrevWordPtNodePos;
        mSecondWordFirstInputIndex = prevWord->mSecondWordFirstInputIndex;
        memmove(mPrevWord, prevWord->mPrevWord, prevWord->mPrevWordLength * sizeof(mPrevWord[0]));
    }

    void init(const int16_t prevWordCount, const int prevWordNodePos, const int *const src0,
            const int16_t length0, const int *const src1, const int16_t length1,
            const int prevWordSecondWordFirstInputIndex, const int lastInputIndex) {
        mPrevWordCount = std::min(prevWordCount, static_cast<int16_t>(MAX_RESULTS));
        mPrevWordPtNodePos = prevWordNodePos;
        int twoWordsLen =
                DicNodeUtils::appendTwoWords(src0, length0, src1, length1, mPrevWord);
        if (twoWordsLen >= MAX_WORD_LENGTH) {
            twoWordsLen = MAX_WORD_LENGTH - 1;
        }
        mPrevWord[twoWordsLen] = KEYCODE_SPACE;
        mPrevWordStart = length0;
        mPrevWordLength = static_cast<int16_t>(twoWordsLen + 1);
        mSecondWordFirstInputIndex = prevWordSecondWordFirstInputIndex;
    }

    void setSecondWordFirstInputIndex(const int inputIndex) {
        mSecondWordFirstInputIndex = inputIndex;
    }

    int getSecondWordFirstInputIndex() const {
        return mSecondWordFirstInputIndex;
    }

    // TODO: remove
    int16_t getPrevWordLength() const {
        return mPrevWordLength;
    }

    int16_t getPrevWordCount() const {
        return mPrevWordCount;
    }

    int16_t getPrevWordStart() const {
        return mPrevWordStart;
    }

    int getPrevWordPtNodePos() const {
        return mPrevWordPtNodePos;
    }

    int getPrevWordCodePointAt(const int id) const {
        return mPrevWord[id];
    }

    const int *getPrevWordBuf() const {
        return mPrevWord;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(DicNodeStatePrevWord);

    int16_t mPrevWordCount;
    int16_t mPrevWordLength;
    int16_t mPrevWordStart;
    int mPrevWordPtNodePos;
    int mSecondWordFirstInputIndex;
    int mPrevWord[MAX_WORD_LENGTH];
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_STATE_PREVWORD_H
