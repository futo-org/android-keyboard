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

// TODO: use uint16_t instead of unsigned short
bool Dictionary::isValidWord(unsigned short *word, int length) {
    if (IS_LATEST_DICT_VERSION) {
        return (isValidWordRec(DICTIONARY_HEADER_SIZE, word, 0, length) != NOT_VALID_WORD);
    } else {
        return (isValidWordRec(0, word, 0, length) != NOT_VALID_WORD);
    }
}

int Dictionary::isValidWordRec(int pos, unsigned short *word, int offset, int length) {
    // returns address of bigram data of that word
    // return -99 if not found

    int count = Dictionary::getCount(mDict, &pos);
    unsigned short currentChar = (unsigned short) word[offset];
    for (int j = 0; j < count; j++) {
        unsigned short c = Dictionary::getChar(mDict, &pos);
        int terminal = Dictionary::getTerminal(mDict, &pos);
        int childPos = Dictionary::getAddress(mDict, &pos);
        if (c == currentChar) {
            if (offset == length - 1) {
                if (terminal) {
                    return (pos+1);
                }
            } else {
                if (childPos != 0) {
                    int t = isValidWordRec(childPos, word, offset + 1, length);
                    if (t > 0) {
                        return t;
                    }
                }
            }
        }
        if (terminal) {
            Dictionary::getFreq(mDict, IS_LATEST_DICT_VERSION, &pos);
        }
        // There could be two instances of each alphabet - upper and lower case. So continue
        // looking ...
    }
    return NOT_VALID_WORD;
}
} // namespace latinime
