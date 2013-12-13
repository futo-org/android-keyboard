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

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/bigram_dictionary.h"
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest.h"
#include "suggest/core/suggest_options.h"
#include "suggest/policyimpl/gesture/gesture_suggest_policy_factory.h"
#include "suggest/policyimpl/typing/typing_suggest_policy_factory.h"
#include "utils/log_utils.h"

namespace latinime {

const int Dictionary::HEADER_ATTRIBUTE_BUFFER_SIZE = 32;

Dictionary::Dictionary(JNIEnv *env,
        DictionaryStructureWithBufferPolicy *const dictionaryStructureWithBufferPolicy)
        : mDictionaryStructureWithBufferPolicy(dictionaryStructureWithBufferPolicy),
          mBigramDictionary(new BigramDictionary(mDictionaryStructureWithBufferPolicy)),
          mGestureSuggest(new Suggest(GestureSuggestPolicyFactory::getGestureSuggestPolicy())),
          mTypingSuggest(new Suggest(TypingSuggestPolicyFactory::getTypingSuggestPolicy())) {
    logDictionaryInfo(env);
}

Dictionary::~Dictionary() {
    delete mBigramDictionary;
    delete mGestureSuggest;
    delete mTypingSuggest;
    delete mDictionaryStructureWithBufferPolicy;
}

int Dictionary::getSuggestions(ProximityInfo *proximityInfo, DicTraverseSession *traverseSession,
        int *xcoordinates, int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints,
        int inputSize, int *prevWordCodePoints, int prevWordLength, int commitPoint,
        const SuggestOptions *const suggestOptions, int *outWords, int *frequencies,
        int *spaceIndices, int *outputTypes, int *outputAutoCommitFirstWordConfidence) const {
    int result = 0;
    if (suggestOptions->isGesture()) {
        DicTraverseSession::initSessionInstance(
                traverseSession, this, prevWordCodePoints, prevWordLength, suggestOptions);
        result = mGestureSuggest->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint, outWords,
                frequencies, spaceIndices, outputTypes, outputAutoCommitFirstWordConfidence);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies);
        }
        return result;
    } else {
        DicTraverseSession::initSessionInstance(
                traverseSession, this, prevWordCodePoints, prevWordLength, suggestOptions);
        result = mTypingSuggest->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint,
                outWords, frequencies, spaceIndices, outputTypes,
                outputAutoCommitFirstWordConfidence);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies);
        }
        return result;
    }
}

int Dictionary::getBigrams(const int *word, int length, int *outWords, int *frequencies,
        int *outputTypes) const {
    if (length <= 0) return 0;
    return mBigramDictionary->getPredictions(word, length, outWords, frequencies, outputTypes);
}

int Dictionary::getProbability(const int *word, int length) const {
    int pos = getDictionaryStructurePolicy()->getTerminalNodePositionOfWord(word, length,
            false /* forceLowerCaseSearch */);
    if (NOT_A_DICT_POS == pos) {
        return NOT_A_PROBABILITY;
    }
    return getDictionaryStructurePolicy()->getUnigramProbabilityOfPtNode(pos);
}

int Dictionary::getBigramProbability(const int *word0, int length0, const int *word1,
        int length1) const {
    return mBigramDictionary->getBigramProbability(word0, length0, word1, length1);
}

void Dictionary::addUnigramWord(const int *const word, const int length, const int probability) {
    mDictionaryStructureWithBufferPolicy->addUnigramWord(word, length, probability);
}

void Dictionary::addBigramWords(const int *const word0, const int length0, const int *const word1,
        const int length1, const int probability) {
    mDictionaryStructureWithBufferPolicy->addBigramWords(word0, length0, word1, length1,
            probability);
}

void Dictionary::removeBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1) {
    mDictionaryStructureWithBufferPolicy->removeBigramWords(word0, length0, word1, length1);
}

void Dictionary::flush(const char *const filePath) {
    mDictionaryStructureWithBufferPolicy->flush(filePath);
}

void Dictionary::flushWithGC(const char *const filePath) {
    mDictionaryStructureWithBufferPolicy->flushWithGC(filePath);
}

bool Dictionary::needsToRunGC(const bool mindsBlockByGC) {
    return mDictionaryStructureWithBufferPolicy->needsToRunGC(mindsBlockByGC);
}

void Dictionary::getProperty(const char *const query, char *const outResult,
        const int maxResultLength) {
    return mDictionaryStructureWithBufferPolicy->getProperty(query, outResult, maxResultLength);
}

void Dictionary::logDictionaryInfo(JNIEnv *const env) const {
    int dictionaryIdCodePointBuffer[HEADER_ATTRIBUTE_BUFFER_SIZE];
    int versionStringCodePointBuffer[HEADER_ATTRIBUTE_BUFFER_SIZE];
    int dateStringCodePointBuffer[HEADER_ATTRIBUTE_BUFFER_SIZE];
    const DictionaryHeaderStructurePolicy *const headerPolicy =
            getDictionaryStructurePolicy()->getHeaderStructurePolicy();
    headerPolicy->readHeaderValueOrQuestionMark("dictionary", dictionaryIdCodePointBuffer,
            HEADER_ATTRIBUTE_BUFFER_SIZE);
    headerPolicy->readHeaderValueOrQuestionMark("version", versionStringCodePointBuffer,
            HEADER_ATTRIBUTE_BUFFER_SIZE);
    headerPolicy->readHeaderValueOrQuestionMark("date", dateStringCodePointBuffer,
            HEADER_ATTRIBUTE_BUFFER_SIZE);

    char dictionaryIdCharBuffer[HEADER_ATTRIBUTE_BUFFER_SIZE];
    char versionStringCharBuffer[HEADER_ATTRIBUTE_BUFFER_SIZE];
    char dateStringCharBuffer[HEADER_ATTRIBUTE_BUFFER_SIZE];
    intArrayToCharArray(dictionaryIdCodePointBuffer, HEADER_ATTRIBUTE_BUFFER_SIZE,
            dictionaryIdCharBuffer, HEADER_ATTRIBUTE_BUFFER_SIZE);
    intArrayToCharArray(versionStringCodePointBuffer, HEADER_ATTRIBUTE_BUFFER_SIZE,
            versionStringCharBuffer, HEADER_ATTRIBUTE_BUFFER_SIZE);
    intArrayToCharArray(dateStringCodePointBuffer, HEADER_ATTRIBUTE_BUFFER_SIZE,
            dateStringCharBuffer, HEADER_ATTRIBUTE_BUFFER_SIZE);

    LogUtils::logToJava(env,
            "Dictionary info: dictionary = %s ; version = %s ; date = %s",
            dictionaryIdCharBuffer, versionStringCharBuffer, dateStringCharBuffer);
}

} // namespace latinime
