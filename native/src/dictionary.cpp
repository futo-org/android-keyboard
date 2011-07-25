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

#include "dictionary.h"

namespace latinime {

// TODO: Change the type of all keyCodes to uint32_t
Dictionary::Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust,
        int typedLetterMultiplier, int fullWordMultiplier,
        int maxWordLength, int maxWords, int maxAlternatives)
    : mDict((unsigned char*) dict), mDictSize(dictSize),
    mMmapFd(mmapFd), mDictBufAdjust(dictBufAdjust),
    // Checks whether it has the latest dictionary or the old dictionary
    IS_LATEST_DICT_VERSION((((unsigned char*) dict)[0] & 0xFF) >= DICTIONARY_VERSION_MIN) {
    if (DEBUG_DICT) {
        if (MAX_WORD_LENGTH_INTERNAL < maxWordLength) {
            LOGI("Max word length (%d) is greater than %d",
                    maxWordLength, MAX_WORD_LENGTH_INTERNAL);
            LOGI("IN NATIVE SUGGEST Version: %d", (mDict[0] & 0xFF));
        }
    }
    mUnigramDictionary = new UnigramDictionary(mDict, typedLetterMultiplier, fullWordMultiplier,
            maxWordLength, maxWords, maxAlternatives, IS_LATEST_DICT_VERSION);
    mBigramDictionary = new BigramDictionary(mDict, maxWordLength, maxAlternatives,
            IS_LATEST_DICT_VERSION, hasBigram(), this);
}

Dictionary::~Dictionary() {
    delete mUnigramDictionary;
    delete mBigramDictionary;
}

bool Dictionary::hasBigram() {
    return ((mDict[1] & 0xFF) == 1);
}

bool Dictionary::isValidWord(unsigned short *word, int length) {
    return mUnigramDictionary->isValidWord(word, length);
}

} // namespace latinime
