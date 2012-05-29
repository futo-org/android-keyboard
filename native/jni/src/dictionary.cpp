/*
**
** Copyright 2009, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <stdio.h>

#define LOG_TAG "LatinIME: dictionary.cpp"

#include "binary_format.h"
#include "defines.h"
#include "dictionary.h"

namespace latinime {

// TODO: Change the type of all keyCodes to uint32_t
Dictionary::Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust,
        int typedLetterMultiplier, int fullWordMultiplier,
        int maxWordLength, int maxWords)
    : mDict((unsigned char*) dict), mDictSize(dictSize),
      mMmapFd(mmapFd), mDictBufAdjust(dictBufAdjust) {
    if (DEBUG_DICT) {
        if (MAX_WORD_LENGTH_INTERNAL < maxWordLength) {
            AKLOGI("Max word length (%d) is greater than %d",
                    maxWordLength, MAX_WORD_LENGTH_INTERNAL);
            AKLOGI("IN NATIVE SUGGEST Version: %d", (mDict[0] & 0xFF));
        }
    }
    mCorrection = new Correction(typedLetterMultiplier, fullWordMultiplier);
    mWordsPriorityQueuePool = new WordsPriorityQueuePool(
            maxWords, SUB_QUEUE_MAX_WORDS, maxWordLength);
    const unsigned int headerSize = BinaryFormat::getHeaderSize(mDict);
    const unsigned int options = BinaryFormat::getFlags(mDict);
    mUnigramDictionary = new UnigramDictionary(mDict + headerSize, typedLetterMultiplier,
            fullWordMultiplier, maxWordLength, maxWords, options);
    mBigramDictionary = new BigramDictionary(mDict + headerSize, maxWordLength, this);
}

Dictionary::~Dictionary() {
    delete mCorrection;
    delete mWordsPriorityQueuePool;
    delete mUnigramDictionary;
    delete mBigramDictionary;
}

int Dictionary::getFrequency(const int32_t *word, int length) {
    return mUnigramDictionary->getFrequency(word, length);
}

bool Dictionary::isValidBigram(const int32_t *word1, int length1, const int32_t *word2,
        int length2) {
    return mBigramDictionary->isValidBigram(word1, length1, word2, length2);
}

} // namespace latinime
