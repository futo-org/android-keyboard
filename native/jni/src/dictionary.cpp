/*
 * Copyright (C) 2009, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "LatinIME: dictionary.cpp"

#include <stdint.h>

#include "bigram_dictionary.h"
#include "binary_format.h"
#include "defines.h"
#include "dictionary.h"
#include "dic_traverse_wrapper.h"
#include "gesture_decoder_wrapper.h"
#include "unigram_dictionary.h"

namespace latinime {

// TODO: Change the type of all keyCodes to uint32_t
Dictionary::Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust,
        int typedLetterMultiplier, int fullWordMultiplier, int maxWordLength, int maxWords,
        int maxPredictions)
        : mDict(static_cast<unsigned char *>(dict)),
          mOffsetDict((static_cast<unsigned char *>(dict)) + BinaryFormat::getHeaderSize(mDict)),
          mDictSize(dictSize), mMmapFd(mmapFd), mDictBufAdjust(dictBufAdjust),
          mUnigramDictionary(new UnigramDictionary(mOffsetDict, typedLetterMultiplier,
                  fullWordMultiplier, maxWordLength, maxWords, BinaryFormat::getFlags(mDict))),
          mBigramDictionary(new BigramDictionary(mOffsetDict, maxWordLength, maxPredictions)),
          mGestureDecoder(new GestureDecoderWrapper(maxWordLength, maxWords)) {
    if (DEBUG_DICT) {
        if (MAX_WORD_LENGTH_INTERNAL < maxWordLength) {
            AKLOGI("Max word length (%d) is greater than %d",
                    maxWordLength, MAX_WORD_LENGTH_INTERNAL);
            AKLOGI("IN NATIVE SUGGEST Version: %d", (mDict[0] & 0xFF));
        }
    }
}

Dictionary::~Dictionary() {
    delete mUnigramDictionary;
    delete mBigramDictionary;
    delete mGestureDecoder;
}

int Dictionary::getSuggestions(ProximityInfo *proximityInfo, void *traverseSession,
        int *xcoordinates, int *ycoordinates, int *times, int *pointerIds,
        int *codes, int codesSize, int *prevWordChars,
        int prevWordLength, int commitPoint, bool isGesture,
        bool useFullEditDistance, unsigned short *outWords,
        int *frequencies, int *spaceIndices, int *outputTypes) const {
    int result = 0;
    if (isGesture) {
        DicTraverseWrapper::initDicTraverseSession(
                traverseSession, this, prevWordChars, prevWordLength);
        result = mGestureDecoder->getSuggestions(proximityInfo, traverseSession,
                xcoordinates, ycoordinates, times, pointerIds, codes, codesSize, commitPoint,
                outWords, frequencies, spaceIndices, outputTypes);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies, 18 /* MAX_WORDS */, MAX_WORD_LENGTH_INTERNAL);
        }
        return result;
    } else {
        std::map<int, int> bigramMap;
        uint8_t bigramFilter[BIGRAM_FILTER_BYTE_SIZE];
        mBigramDictionary->fillBigramAddressToFrequencyMapAndFilter(prevWordChars,
                prevWordLength, &bigramMap, bigramFilter);
        result = mUnigramDictionary->getSuggestions(proximityInfo, xcoordinates,
                ycoordinates, codes, codesSize, &bigramMap, bigramFilter,
                useFullEditDistance, outWords, frequencies, outputTypes);
        return result;
    }
}

int Dictionary::getBigrams(const int32_t *word, int length, int *codes, int codesSize,
        unsigned short *outWords, int *frequencies, int *outputTypes) const {
    if (length <= 0) return 0;
    return mBigramDictionary->getBigrams(word, length, codes, codesSize, outWords, frequencies,
            outputTypes);
}

int Dictionary::getFrequency(const int32_t *word, int length) const {
    return mUnigramDictionary->getFrequency(word, length);
}

bool Dictionary::isValidBigram(const int32_t *word1, int length1, const int32_t *word2,
        int length2) const {
    return mBigramDictionary->isValidBigram(word1, length1, word2, length2);
}
} // namespace latinime
