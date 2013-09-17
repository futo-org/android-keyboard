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

#include <cstring> // for memset()
#include <stdint.h>

#include "defines.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/layout/proximity_info_state.h"

namespace latinime {

class DicNodeStatePrevWord {
 public:
    AK_FORCE_INLINE DicNodeStatePrevWord()
            : mPrevWordCount(0), mPrevWordLength(0), mPrevWordStart(0), mPrevWordProbability(0),
              mPrevWordNodePos(NOT_A_DICT_POS), mSecondWordFirstInputIndex(NOT_AN_INDEX) {
        memset(mPrevWord, 0, sizeof(mPrevWord));
    }

    virtual ~DicNodeStatePrevWord() {}

    void init() {
        mPrevWordLength = 0;
        mPrevWordCount = 0;
        mPrevWordStart = 0;
        mPrevWordProbability = -1;
        mPrevWordNodePos = NOT_A_DICT_POS;
        mSecondWordFirstInputIndex = NOT_AN_INDEX;
    }

    void init(const int prevWordNodePos) {
        mPrevWordLength = 0;
        mPrevWordCount = 0;
        mPrevWordStart = 0;
        mPrevWordProbability = -1;
        mPrevWordNodePos = prevWordNodePos;
        mSecondWordFirstInputIndex = NOT_AN_INDEX;
    }

    // Init by copy
    AK_FORCE_INLINE void init(const DicNodeStatePrevWord *const prevWord) {
        mPrevWordLength = prevWord->mPrevWordLength;
        mPrevWordCount = prevWord->mPrevWordCount;
        mPrevWordStart = prevWord->mPrevWordStart;
        mPrevWordProbability = prevWord->mPrevWordProbability;
        mPrevWordNodePos = prevWord->mPrevWordNodePos;
        mSecondWordFirstInputIndex = prevWord->mSecondWordFirstInputIndex;
        memcpy(mPrevWord, prevWord->mPrevWord, prevWord->mPrevWordLength * sizeof(mPrevWord[0]));
    }

    void init(const int16_t prevWordCount, const int16_t prevWordProbability,
            const int prevWordNodePos, const int *const src0, const int16_t length0,
            const int *const src1, const int16_t length1,
            const int prevWordSecondWordFirstInputIndex, const int lastInputIndex) {
        mPrevWordCount = min(prevWordCount, static_cast<int16_t>(MAX_RESULTS));
        mPrevWordProbability = prevWordProbability;
        mPrevWordNodePos = prevWordNodePos;
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

    void truncate(const int offset) {
        // TODO: memmove
        if (mPrevWordLength < offset) {
            memset(mPrevWord, 0, sizeof(mPrevWord));
            mPrevWordLength = 0;
            return;
        }
        const int newPrevWordLength = mPrevWordLength - offset;
        memmove(mPrevWord, &mPrevWord[offset], newPrevWordLength * sizeof(mPrevWord[0]));
        mPrevWordLength = newPrevWordLength;
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

    int getPrevWordNodePos() const {
        return mPrevWordNodePos;
    }

    int getPrevWordCodePointAt(const int id) const {
        return mPrevWord[id];
    }

    bool startsWith(const DicNodeStatePrevWord *const prefix, const int prefixLen) const {
        if (prefixLen > mPrevWordLength) {
            return false;
        }
        for (int i = 0; i < prefixLen; ++i) {
            if (mPrevWord[i] != prefix->mPrevWord[i]) {
                return false;
            }
        }
        return true;
    }

    // TODO: Move to private
    int mPrevWord[MAX_WORD_LENGTH];

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class
    int16_t mPrevWordCount;
    int16_t mPrevWordLength;
    int16_t mPrevWordStart;
    int16_t mPrevWordProbability;
    int mPrevWordNodePos;
    int mSecondWordFirstInputIndex;
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_STATE_PREVWORD_H
