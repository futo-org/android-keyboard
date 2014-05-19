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
#include "suggest/core/dictionary/binary_dictionary_bigrams_iterator.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"

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

    void getPrevWordsTerminalPtNodePos(
            const DictionaryStructureWithBufferPolicy *const dictStructurePolicy,
            int *const outPrevWordsTerminalPtNodePos) const {
        for (size_t i = 0; i < NELEMS(mPrevWordCodePoints); ++i) {
            outPrevWordsTerminalPtNodePos[i] = getTerminalPtNodePosOfWord(dictStructurePolicy,
                    mPrevWordCodePoints[i], mPrevWordCodePointCount[i],
                    mIsBeginningOfSentence[i]);
        }
    }

    BinaryDictionaryBigramsIterator getBigramsIteratorForPrediction(
            const DictionaryStructureWithBufferPolicy *const dictStructurePolicy) const {
        int pos = getBigramListPositionForWord(dictStructurePolicy, mPrevWordCodePoints[0],
                mPrevWordCodePointCount[0], false /* forceLowerCaseSearch */);
        // getBigramListPositionForWord returns NOT_A_DICT_POS if this word isn't in the
        // dictionary or has no bigrams
        if (NOT_A_DICT_POS == pos) {
            // If no bigrams for this exact word, search again in lower case.
            pos = getBigramListPositionForWord(dictStructurePolicy, mPrevWordCodePoints[0],
                    mPrevWordCodePointCount[0], true /* forceLowerCaseSearch */);
        }
        return BinaryDictionaryBigramsIterator(
                dictStructurePolicy->getBigramsStructurePolicy(), pos);
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(PrevWordsInfo);

    static int getTerminalPtNodePosOfWord(
            const DictionaryStructureWithBufferPolicy *const dictStructurePolicy,
            const int *const wordCodePoints, const int wordCodePointCount,
            const bool isBeginningOfSentence) {
        if (!dictStructurePolicy || !wordCodePoints) {
            return NOT_A_DICT_POS;
        }
        const int wordPtNodePos = dictStructurePolicy->getTerminalPtNodePositionOfWord(
                wordCodePoints, wordCodePointCount, false /* forceLowerCaseSearch */);
        if (wordPtNodePos != NOT_A_DICT_POS) {
            return wordPtNodePos;
        }
        // Check bigrams for lower-cased previous word if original was not found. Useful for
        // auto-capitalized words like "The [current_word]".
        return dictStructurePolicy->getTerminalPtNodePositionOfWord(
                wordCodePoints, wordCodePointCount, true /* forceLowerCaseSearch */);
    }

    static int getBigramListPositionForWord(
            const DictionaryStructureWithBufferPolicy *const dictStructurePolicy,
            const int *wordCodePoints, const int wordCodePointCount,
            const bool forceLowerCaseSearch) {
        if (!wordCodePoints || wordCodePointCount <= 0) return NOT_A_DICT_POS;
        const int terminalPtNodePos = dictStructurePolicy->getTerminalPtNodePositionOfWord(
                wordCodePoints, wordCodePointCount, forceLowerCaseSearch);
        if (NOT_A_DICT_POS == terminalPtNodePos) return NOT_A_DICT_POS;
        return dictStructurePolicy->getBigramsPositionOfPtNode(terminalPtNodePos);
    }

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
