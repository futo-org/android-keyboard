/*
 * Copyright (C) 2010, The Android Open Source Project
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

#define LOG_TAG "LatinIME: bigram_dictionary.cpp"

#include "bigram_dictionary.h"

#include <algorithm>
#include <cstring>

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_bigrams_iterator.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/prev_words_info.h"
#include "utils/char_utils.h"

namespace latinime {

BigramDictionary::BigramDictionary(
        const DictionaryStructureWithBufferPolicy *const dictionaryStructurePolicy)
        : mDictionaryStructurePolicy(dictionaryStructurePolicy) {
    if (DEBUG_DICT) {
        AKLOGI("BigramDictionary - constructor");
    }
}

BigramDictionary::~BigramDictionary() {
}

/* Parameters :
 * prevWordsInfo: Information of previous words to get the predictions.
 * outSuggestionResults: SuggestionResults to put the predictions.
 */
void BigramDictionary::getPredictions(const PrevWordsInfo *const prevWordsInfo,
        SuggestionResults *const outSuggestionResults) const {
    int pos = getBigramListPositionForWord(prevWordsInfo->getPrevWordCodePoints(),
            prevWordsInfo->getPrevWordCodePointCount(), false /* forceLowerCaseSearch */);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (NOT_A_DICT_POS == pos) {
        // If no bigrams for this exact word, search again in lower case.
        pos = getBigramListPositionForWord(prevWordsInfo->getPrevWordCodePoints(),
                prevWordsInfo->getPrevWordCodePointCount(), true /* forceLowerCaseSearch */);
    }
    // If still no bigrams, we really don't have them!
    if (NOT_A_DICT_POS == pos) return;

    int unigramProbability = 0;
    int bigramCodePoints[MAX_WORD_LENGTH];
    BinaryDictionaryBigramsIterator bigramsIt(
            mDictionaryStructurePolicy->getBigramsStructurePolicy(), pos);
    while (bigramsIt.hasNext()) {
        bigramsIt.next();
        if (bigramsIt.getBigramPos() == NOT_A_DICT_POS) {
            continue;
        }
        const int codePointCount = mDictionaryStructurePolicy->
                getCodePointsAndProbabilityAndReturnCodePointCount(bigramsIt.getBigramPos(),
                        MAX_WORD_LENGTH, bigramCodePoints, &unigramProbability);
        if (codePointCount <= 0) {
            continue;
        }
        // Due to space constraints, the probability for bigrams is approximate - the lower the
        // unigram probability, the worse the precision. The theoritical maximum error in
        // resulting probability is 8 - although in the practice it's never bigger than 3 or 4
        // in very bad cases. This means that sometimes, we'll see some bigrams interverted
        // here, but it can't get too bad.
        const int probability = mDictionaryStructurePolicy->getProbability(
                unigramProbability, bigramsIt.getProbability());
        outSuggestionResults->addPrediction(bigramCodePoints, codePointCount, probability);
    }
}

// Returns a pointer to the start of the bigram list.
// If the word is not found or has no bigrams, this function returns NOT_A_DICT_POS.
int BigramDictionary::getBigramListPositionForWord(const int *prevWord, const int prevWordLength,
        const bool forceLowerCaseSearch) const {
    if (0 >= prevWordLength) return NOT_A_DICT_POS;
    int pos = mDictionaryStructurePolicy->getTerminalPtNodePositionOfWord(prevWord, prevWordLength,
            forceLowerCaseSearch);
    if (NOT_A_DICT_POS == pos) return NOT_A_DICT_POS;
    return mDictionaryStructurePolicy->getBigramsPositionOfPtNode(pos);
}

int BigramDictionary::getBigramProbability(const PrevWordsInfo *const prevWordsInfo,
        const int *word1, int length1) const {
    int pos = getBigramListPositionForWord(prevWordsInfo->getPrevWordCodePoints(),
            prevWordsInfo->getPrevWordCodePointCount(), false /* forceLowerCaseSearch */);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (NOT_A_DICT_POS == pos) return NOT_A_PROBABILITY;
    int nextWordPos = mDictionaryStructurePolicy->getTerminalPtNodePositionOfWord(word1, length1,
            false /* forceLowerCaseSearch */);
    if (NOT_A_DICT_POS == nextWordPos) return NOT_A_PROBABILITY;

    BinaryDictionaryBigramsIterator bigramsIt(
            mDictionaryStructurePolicy->getBigramsStructurePolicy(), pos);
    while (bigramsIt.hasNext()) {
        bigramsIt.next();
        if (bigramsIt.getBigramPos() == nextWordPos
                && bigramsIt.getProbability() != NOT_A_PROBABILITY) {
            return mDictionaryStructurePolicy->getProbability(
                    mDictionaryStructurePolicy->getUnigramProbabilityOfPtNode(nextWordPos),
                    bigramsIt.getProbability());
        }
    }
    return NOT_A_PROBABILITY;
}

// TODO: Move functions related to bigram to here
} // namespace latinime
