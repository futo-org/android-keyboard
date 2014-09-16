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

#include <array>

#include "defines.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "utils/char_utils.h"
#include "utils/int_array_view.h"

namespace latinime {

class PrevWordsInfo {
 public:
    // No prev word information.
    PrevWordsInfo() : mPrevWordCount(0) {
        clear();
    }

    PrevWordsInfo(PrevWordsInfo &&prevWordsInfo)
            : mPrevWordCount(prevWordsInfo.mPrevWordCount) {
        for (size_t i = 0; i < mPrevWordCount; ++i) {
            mPrevWordCodePointCount[i] = prevWordsInfo.mPrevWordCodePointCount[i];
            memmove(mPrevWordCodePoints[i], prevWordsInfo.mPrevWordCodePoints[i],
                    sizeof(mPrevWordCodePoints[i][0]) * mPrevWordCodePointCount[i]);
            mIsBeginningOfSentence[i] = prevWordsInfo.mIsBeginningOfSentence[i];
        }
    }

    // Construct from previous words.
    PrevWordsInfo(const int prevWordCodePoints[][MAX_WORD_LENGTH],
            const int *const prevWordCodePointCount, const bool *const isBeginningOfSentence,
            const size_t prevWordCount)
            : mPrevWordCount(std::min(NELEMS(mPrevWordCodePoints), prevWordCount)) {
        clear();
        for (size_t i = 0; i < mPrevWordCount; ++i) {
            if (prevWordCodePointCount[i] < 0 || prevWordCodePointCount[i] > MAX_WORD_LENGTH) {
                continue;
            }
            memmove(mPrevWordCodePoints[i], prevWordCodePoints[i],
                    sizeof(mPrevWordCodePoints[i][0]) * prevWordCodePointCount[i]);
            mPrevWordCodePointCount[i] = prevWordCodePointCount[i];
            mIsBeginningOfSentence[i] = isBeginningOfSentence[i];
        }
    }

    // Construct from a previous word.
    PrevWordsInfo(const int *const prevWordCodePoints, const int prevWordCodePointCount,
            const bool isBeginningOfSentence) : mPrevWordCount(1) {
        clear();
        if (prevWordCodePointCount > MAX_WORD_LENGTH || !prevWordCodePoints) {
            return;
        }
        memmove(mPrevWordCodePoints[0], prevWordCodePoints,
                sizeof(mPrevWordCodePoints[0][0]) * prevWordCodePointCount);
        mPrevWordCodePointCount[0] = prevWordCodePointCount;
        mIsBeginningOfSentence[0] = isBeginningOfSentence;
    }

    bool isValid() const {
        if (mPrevWordCodePointCount[0] > 0) {
            return true;
        }
        if (mIsBeginningOfSentence[0]) {
            return true;
        }
        return false;
    }

    template<size_t N>
    const WordIdArrayView getPrevWordIds(
            const DictionaryStructureWithBufferPolicy *const dictStructurePolicy,
            std::array<int, N> *const prevWordIdBuffer, const bool tryLowerCaseSearch) const {
        for (size_t i = 0; i < std::min(mPrevWordCount, N); ++i) {
            prevWordIdBuffer->at(i) = getWordId(dictStructurePolicy,
                    mPrevWordCodePoints[i], mPrevWordCodePointCount[i],
                    mIsBeginningOfSentence[i], tryLowerCaseSearch);
        }
        return WordIdArrayView::fromArray(*prevWordIdBuffer).limit(mPrevWordCount);
    }

    // n is 1-indexed.
    const CodePointArrayView getNthPrevWordCodePoints(const size_t n) const {
        if (n <= 0 || n > mPrevWordCount) {
            return CodePointArrayView();
        }
        return CodePointArrayView(mPrevWordCodePoints[n - 1], mPrevWordCodePointCount[n - 1]);
    }

    // n is 1-indexed.
    bool isNthPrevWordBeginningOfSentence(const size_t n) const {
        if (n <= 0 || n > mPrevWordCount) {
            return false;
        }
        return mIsBeginningOfSentence[n - 1];
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(PrevWordsInfo);

    static int getWordId(const DictionaryStructureWithBufferPolicy *const dictStructurePolicy,
            const int *const wordCodePoints, const int wordCodePointCount,
            const bool isBeginningOfSentence, const bool tryLowerCaseSearch) {
        if (!dictStructurePolicy || !wordCodePoints || wordCodePointCount > MAX_WORD_LENGTH) {
            return NOT_A_WORD_ID;
        }
        int codePoints[MAX_WORD_LENGTH];
        int codePointCount = wordCodePointCount;
        memmove(codePoints, wordCodePoints, sizeof(int) * codePointCount);
        if (isBeginningOfSentence) {
            codePointCount = CharUtils::attachBeginningOfSentenceMarker(codePoints,
                    codePointCount, MAX_WORD_LENGTH);
            if (codePointCount <= 0) {
                return NOT_A_WORD_ID;
            }
        }
        const CodePointArrayView codePointArrayView(codePoints, codePointCount);
        const int wordId = dictStructurePolicy->getWordId(
                codePointArrayView, false /* forceLowerCaseSearch */);
        if (wordId != NOT_A_WORD_ID || !tryLowerCaseSearch) {
            // Return the id when when the word was found or doesn't try lower case search.
            return wordId;
        }
        // Check bigrams for lower-cased previous word if original was not found. Useful for
        // auto-capitalized words like "The [current_word]".
        return dictStructurePolicy->getWordId(codePointArrayView, true /* forceLowerCaseSearch */);
    }

    void clear() {
        for (size_t i = 0; i < NELEMS(mPrevWordCodePoints); ++i) {
            mPrevWordCodePointCount[i] = 0;
            mIsBeginningOfSentence[i] = false;
        }
    }

    const size_t mPrevWordCount;
    int mPrevWordCodePoints[MAX_PREV_WORD_COUNT_FOR_N_GRAM][MAX_WORD_LENGTH];
    int mPrevWordCodePointCount[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    bool mIsBeginningOfSentence[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
};
} // namespace latinime
#endif // LATINIME_PREV_WORDS_INFO_H
