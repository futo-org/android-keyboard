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

#include "dictionary.h"

#include <map> // TODO: remove
#include <stdint.h>

#include "bigram_dictionary.h"
#include "binary_format.h"
#include "defines.h"
#include "dic_traverse_wrapper.h"
#include "suggest/core/suggest.h"
#include "suggest/policyimpl/gesture/gesture_suggest_policy_factory.h"
#include "suggest/policyimpl/typing/typing_suggest_policy_factory.h"
#include "unigram_dictionary.h"

namespace latinime {

Dictionary::Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust)
        : mDict(static_cast<unsigned char *>(dict)),
          mOffsetDict((static_cast<unsigned char *>(dict))
                  + BinaryFormat::getHeaderSize(mDict, dictSize)),
          mDictSize(dictSize), mMmapFd(mmapFd), mDictBufAdjust(dictBufAdjust),
          mUnigramDictionary(new UnigramDictionary(mOffsetDict,
                  BinaryFormat::getFlags(mDict, dictSize))),
          mBigramDictionary(new BigramDictionary(mOffsetDict)),
          mGestureSuggest(new Suggest(GestureSuggestPolicyFactory::getGestureSuggestPolicy())),
          mTypingSuggest(new Suggest(TypingSuggestPolicyFactory::getTypingSuggestPolicy())) {
}

Dictionary::~Dictionary() {
    delete mUnigramDictionary;
    delete mBigramDictionary;
    delete mGestureSuggest;
    delete mTypingSuggest;
}

int Dictionary::getSuggestions(ProximityInfo *proximityInfo, void *traverseSession,
        int *xcoordinates, int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints,
        int inputSize, int *prevWordCodePoints, int prevWordLength, int commitPoint, bool isGesture,
        bool useFullEditDistance, int *outWords, int *frequencies, int *spaceIndices,
        int *outputTypes) const {
    int result = 0;
    if (isGesture) {
        DicTraverseWrapper::initDicTraverseSession(
                traverseSession, this, prevWordCodePoints, prevWordLength);
        result = mGestureSuggest->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint, outWords,
                frequencies, spaceIndices, outputTypes);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies);
        }
        return result;
    } else {
        if (USE_SUGGEST_INTERFACE_FOR_TYPING) {
            DicTraverseWrapper::initDicTraverseSession(
                    traverseSession, this, prevWordCodePoints, prevWordLength);
            result = mTypingSuggest->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                    ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint,
                    outWords, frequencies, spaceIndices, outputTypes);
            if (DEBUG_DICT) {
                DUMP_RESULT(outWords, frequencies);
            }
            return result;
        } else {
            std::map<int, int> bigramMap;
            uint8_t bigramFilter[BIGRAM_FILTER_BYTE_SIZE];
            mBigramDictionary->fillBigramAddressToProbabilityMapAndFilter(prevWordCodePoints,
                    prevWordLength, &bigramMap, bigramFilter);
            result = mUnigramDictionary->getSuggestions(proximityInfo, xcoordinates, ycoordinates,
                    inputCodePoints, inputSize, &bigramMap, bigramFilter, useFullEditDistance,
                    outWords, frequencies, outputTypes);
            return result;
        }
    }
}

int Dictionary::getBigrams(const int *word, int length, int *inputCodePoints, int inputSize,
        int *outWords, int *frequencies, int *outputTypes) const {
    if (length <= 0) return 0;
    return mBigramDictionary->getBigrams(word, length, inputCodePoints, inputSize, outWords,
            frequencies, outputTypes);
}

int Dictionary::getProbability(const int *word, int length) const {
    return mUnigramDictionary->getProbability(word, length);
}

bool Dictionary::isValidBigram(const int *word1, int length1, const int *word2, int length2) const {
    return mBigramDictionary->isValidBigram(word1, length1, word2, length2);
}

int Dictionary::getDictFlags() const {
    return mUnigramDictionary->getDictFlags();
}

} // namespace latinime
