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

#include "suggest/core/dictionary/dictionary.h"

#include <map> // TODO: remove
#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/bigram_dictionary.h"
#include "suggest/core/dictionary/binary_format.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest.h"
#include "suggest/core/suggest_options.h"
#include "suggest/policyimpl/gesture/gesture_suggest_policy_factory.h"
#include "suggest/policyimpl/typing/typing_suggest_policy_factory.h"

namespace latinime {

Dictionary::Dictionary(void *dict, int dictSize, int mmapFd, int dictBufOffset, bool isUpdatable)
        : mBinaryDictionaryInfo(static_cast<const uint8_t *>(dict), dictSize, mmapFd,
                dictBufOffset, isUpdatable),
          mBigramDictionary(new BigramDictionary(&mBinaryDictionaryInfo)),
          mGestureSuggest(new Suggest(GestureSuggestPolicyFactory::getGestureSuggestPolicy())),
          mTypingSuggest(new Suggest(TypingSuggestPolicyFactory::getTypingSuggestPolicy())) {
}

Dictionary::~Dictionary() {
    delete mBigramDictionary;
    delete mGestureSuggest;
    delete mTypingSuggest;
}

int Dictionary::getSuggestions(ProximityInfo *proximityInfo, DicTraverseSession *traverseSession,
        int *xcoordinates, int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints,
        int inputSize, int *prevWordCodePoints, int prevWordLength, int commitPoint,
        const SuggestOptions *const suggestOptions, int *outWords, int *frequencies,
        int *spaceIndices, int *outputTypes) const {
    int result = 0;
    if (suggestOptions->isGesture()) {
        DicTraverseSession::initSessionInstance(
                traverseSession, this, prevWordCodePoints, prevWordLength, suggestOptions);
        result = mGestureSuggest->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint, outWords,
                frequencies, spaceIndices, outputTypes);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies);
        }
        return result;
    } else {
        DicTraverseSession::initSessionInstance(
                traverseSession, this, prevWordCodePoints, prevWordLength, suggestOptions);
        result = mTypingSuggest->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint,
                outWords, frequencies, spaceIndices, outputTypes);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies);
        }
        return result;
    }
}

int Dictionary::getBigrams(const int *word, int length, int *inputCodePoints, int inputSize,
        int *outWords, int *frequencies, int *outputTypes) const {
    if (length <= 0) return 0;
    return mBigramDictionary->getPredictions(word, length, inputCodePoints, inputSize, outWords,
            frequencies, outputTypes);
}

int Dictionary::getProbability(const int *word, int length) const {
    const DictionaryStructurePolicy *const structurePolicy =
            mBinaryDictionaryInfo.getStructurePolicy();
    int pos = structurePolicy->getTerminalNodePositionOfWord(&mBinaryDictionaryInfo, word, length,
            false /* forceLowerCaseSearch */);
    if (NOT_VALID_WORD == pos) {
        return NOT_A_PROBABILITY;
    }
    return structurePolicy->getUnigramProbability(&mBinaryDictionaryInfo, pos);
}

bool Dictionary::isValidBigram(const int *word0, int length0, const int *word1, int length1) const {
    return mBigramDictionary->isValidBigram(word0, length0, word1, length1);
}

void Dictionary::addUnigramWord(const int *const word, const int length, const int probability) {
    if (!mBinaryDictionaryInfo.isDynamicallyUpdatable()) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: Dictionary::addUnigramWord() is called for non-updatable dictionary.");
        return;
    }
    // TODO: Support dynamic update
}

void Dictionary::addBigramWords(const int *const word0, const int length0, const int *const word1,
        const int length1, const int probability) {
    if (!mBinaryDictionaryInfo.isDynamicallyUpdatable()) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: Dictionary::addBigramWords() is called for non-updatable dictionary.");
        return;
    }
    // TODO: Support dynamic update
}

void Dictionary::removeBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1) {
    if (!mBinaryDictionaryInfo.isDynamicallyUpdatable()) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: Dictionary::removeBigramWords() is called for non-updatable dictionary.");
        return;
    }
    // TODO: Support dynamic update
}

} // namespace latinime
