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
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest.h"
#include "suggest/core/suggest_options.h"
#include "suggest/policyimpl/gesture/gesture_suggest_policy_factory.h"
#include "suggest/policyimpl/typing/typing_suggest_policy_factory.h"
#include "utils/log_utils.h"
#include "utils/time_keeper.h"

namespace latinime {

const int Dictionary::HEADER_ATTRIBUTE_BUFFER_SIZE = 32;

Dictionary::Dictionary(JNIEnv *env, const DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        &dictionaryStructureWithBufferPolicy)
        : mDictionaryStructureWithBufferPolicy(dictionaryStructureWithBufferPolicy),
          mBigramDictionary(new BigramDictionary(mDictionaryStructureWithBufferPolicy.get())),
          mGestureSuggest(new Suggest(GestureSuggestPolicyFactory::getGestureSuggestPolicy())),
          mTypingSuggest(new Suggest(TypingSuggestPolicyFactory::getTypingSuggestPolicy())) {
    logDictionaryInfo(env);
}

int Dictionary::getSuggestions(ProximityInfo *proximityInfo, DicTraverseSession *traverseSession,
        int *xcoordinates, int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints,
        int inputSize, int *prevWordCodePoints, int prevWordLength, int commitPoint,
        const SuggestOptions *const suggestOptions, int *outWords, int *frequencies,
        int *spaceIndices, int *outputTypes, int *outputAutoCommitFirstWordConfidence) const {
    TimeKeeper::setCurrentTime();
    int result = 0;
    if (suggestOptions->isGesture()) {
        DicTraverseSession::initSessionInstance(
                traverseSession, this, prevWordCodePoints, prevWordLength, suggestOptions);
        result = mGestureSuggest.get()->getSuggestions(proximityInfo, traverseSession, xcoordinates,
                ycoordinates, times, pointerIds, inputCodePoints, inputSize, commitPoint, outWords,
                frequencies, spaceIndices, outputTypes, outputAutoCommitFirstWordConfidence);
        if (DEBUG_DICT) {
            DUMP_RESULT(outWords, frequencies);
        }
        return result;
    } else {
        DicTraverseSession::initSessionInstance(
                traverseSession, this, prevWordCodePoints, prevWordLength, suggestOptions);
        result = mTypingSuggest.get()->getSuggestions(proximityInfo, traverseSession, xcoordinates,
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
    TimeKeeper::setCurrentTime();
    if (length <= 0) return 0;
    return mBigramDictionary.get()->getPredictions(word, length, outWords, frequencies,
            outputTypes);
}

int Dictionary::getProbability(const int *word, int length) const {
    TimeKeeper::setCurrentTime();
    int pos = getDictionaryStructurePolicy()->getTerminalPtNodePositionOfWord(word, length,
            false /* forceLowerCaseSearch */);
    if (NOT_A_DICT_POS == pos) {
        return NOT_A_PROBABILITY;
    }
    return getDictionaryStructurePolicy()->getUnigramProbabilityOfPtNode(pos);
}

int Dictionary::getBigramProbability(const int *word0, int length0, const int *word1,
        int length1) const {
    TimeKeeper::setCurrentTime();
    return mBigramDictionary.get()->getBigramProbability(word0, length0, word1, length1);
}

void Dictionary::addUnigramWord(const int *const word, const int length, const int probability,
        const int *const shortcutTargetCodePoints, const int shortcutLength,
        const int shortcutProbability, const bool isNotAWord, const bool isBlacklisted,
        const int timestamp) {
    TimeKeeper::setCurrentTime();
    mDictionaryStructureWithBufferPolicy.get()->addUnigramWord(word, length, probability,
            shortcutTargetCodePoints, shortcutLength, shortcutProbability, isNotAWord,
            isBlacklisted, timestamp);
}

void Dictionary::addBigramWords(const int *const word0, const int length0, const int *const word1,
        const int length1, const int probability, const int timestamp) {
    TimeKeeper::setCurrentTime();
    mDictionaryStructureWithBufferPolicy.get()->addBigramWords(word0, length0, word1, length1,
            probability, timestamp);
}

void Dictionary::removeBigramWords(const int *const word0, const int length0,
        const int *const word1, const int length1) {
    TimeKeeper::setCurrentTime();
    mDictionaryStructureWithBufferPolicy.get()->removeBigramWords(word0, length0, word1, length1);
}

void Dictionary::flush(const char *const filePath) {
    TimeKeeper::setCurrentTime();
    mDictionaryStructureWithBufferPolicy.get()->flush(filePath);
}

void Dictionary::flushWithGC(const char *const filePath) {
    TimeKeeper::setCurrentTime();
    mDictionaryStructureWithBufferPolicy.get()->flushWithGC(filePath);
}

bool Dictionary::needsToRunGC(const bool mindsBlockByGC) {
    TimeKeeper::setCurrentTime();
    return mDictionaryStructureWithBufferPolicy.get()->needsToRunGC(mindsBlockByGC);
}

void Dictionary::getProperty(const char *const query, const int queryLength, char *const outResult,
        const int maxResultLength) {
    TimeKeeper::setCurrentTime();
    return mDictionaryStructureWithBufferPolicy.get()->getProperty(query, queryLength, outResult,
            maxResultLength);
}

const UnigramProperty Dictionary::getUnigramProperty(const int *const codePoints,
        const int codePointCount) {
    TimeKeeper::setCurrentTime();
    return mDictionaryStructureWithBufferPolicy.get()->getUnigramProperty(
            codePoints, codePointCount);
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
