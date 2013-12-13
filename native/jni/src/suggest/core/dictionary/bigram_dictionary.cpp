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

#include <cstring>

#define LOG_TAG "LatinIME: bigram_dictionary.cpp"

#include "bigram_dictionary.h"

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_bigrams_iterator.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
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

void BigramDictionary::addWordBigram(int *word, int length, int probability, int *bigramProbability,
        int *bigramCodePoints, int *outputTypes) const {
    word[length] = 0;
    if (DEBUG_DICT_FULL) {
#ifdef FLAG_DBG
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = static_cast<char>(word[i]);
        AKLOGI("Bigram: Found word = %s, freq = %d :", s, probability);
#endif
    }

    // Find the right insertion point
    int insertAt = 0;
    while (insertAt < MAX_RESULTS) {
        if (probability > bigramProbability[insertAt] || (bigramProbability[insertAt] == probability
                && length < CharUtils::getCodePointCount(MAX_WORD_LENGTH,
                        bigramCodePoints + insertAt * MAX_WORD_LENGTH))) {
            break;
        }
        insertAt++;
    }
    if (DEBUG_DICT_FULL) {
        AKLOGI("Bigram: InsertAt -> %d MAX_RESULTS: %d", insertAt, MAX_RESULTS);
    }
    if (insertAt >= MAX_RESULTS) {
        return;
    }
    memmove(bigramProbability + (insertAt + 1),
            bigramProbability + insertAt,
            (MAX_RESULTS - insertAt - 1) * sizeof(bigramProbability[0]));
    bigramProbability[insertAt] = probability;
    outputTypes[insertAt] = Dictionary::KIND_PREDICTION;
    memmove(bigramCodePoints + (insertAt + 1) * MAX_WORD_LENGTH,
            bigramCodePoints + insertAt * MAX_WORD_LENGTH,
            (MAX_RESULTS - insertAt - 1) * sizeof(bigramCodePoints[0]) * MAX_WORD_LENGTH);
    int *dest = bigramCodePoints + insertAt * MAX_WORD_LENGTH;
    while (length--) {
        *dest++ = *word++;
    }
    *dest = 0; // NULL terminate
    if (DEBUG_DICT_FULL) {
        AKLOGI("Bigram: Added word at %d", insertAt);
    }
}

/* Parameters :
 * prevWord: the word before, the one for which we need to look up bigrams.
 * prevWordLength: its length.
 * outBigramCodePoints: an array for output, at the same format as outwords for getSuggestions.
 * outBigramProbability: an array to output frequencies.
 * outputTypes: an array to output types.
 * This method returns the number of bigrams this word has, for backward compatibility.
 */
int BigramDictionary::getPredictions(const int *prevWord, const int prevWordLength,
        int *const outBigramCodePoints, int *const outBigramProbability,
        int *const outputTypes) const {
    // TODO: remove unused arguments, and refrain from storing stuff in members of this class
    // TODO: have "in" arguments before "out" ones, and make out args explicit in the name

    int pos = getBigramListPositionForWord(prevWord, prevWordLength,
            false /* forceLowerCaseSearch */);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (NOT_A_DICT_POS == pos) {
        // If no bigrams for this exact word, search again in lower case.
        pos = getBigramListPositionForWord(prevWord, prevWordLength,
                true /* forceLowerCaseSearch */);
    }
    // If still no bigrams, we really don't have them!
    if (NOT_A_DICT_POS == pos) return 0;

    int bigramCount = 0;
    int unigramProbability = 0;
    int bigramBuffer[MAX_WORD_LENGTH];
    BinaryDictionaryBigramsIterator bigramsIt(
            mDictionaryStructurePolicy->getBigramsStructurePolicy(), pos);
    while (bigramsIt.hasNext()) {
        bigramsIt.next();
        if (bigramsIt.getBigramPos() == NOT_A_DICT_POS) {
            continue;
        }
        const int codePointCount = mDictionaryStructurePolicy->
                getCodePointsAndProbabilityAndReturnCodePointCount(bigramsIt.getBigramPos(),
                        MAX_WORD_LENGTH, bigramBuffer, &unigramProbability);
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
        addWordBigram(bigramBuffer, codePointCount, probability, outBigramProbability,
                outBigramCodePoints, outputTypes);
        ++bigramCount;
    }
    return min(bigramCount, MAX_RESULTS);
}

// Returns a pointer to the start of the bigram list.
// If the word is not found or has no bigrams, this function returns NOT_A_DICT_POS.
int BigramDictionary::getBigramListPositionForWord(const int *prevWord, const int prevWordLength,
        const bool forceLowerCaseSearch) const {
    if (0 >= prevWordLength) return NOT_A_DICT_POS;
    int pos = mDictionaryStructurePolicy->getTerminalNodePositionOfWord(prevWord, prevWordLength,
            forceLowerCaseSearch);
    if (NOT_A_DICT_POS == pos) return NOT_A_DICT_POS;
    return mDictionaryStructurePolicy->getBigramsPositionOfPtNode(pos);
}

int BigramDictionary::getBigramProbability(const int *word0, int length0, const int *word1,
        int length1) const {
    int pos = getBigramListPositionForWord(word0, length0, false /* forceLowerCaseSearch */);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (NOT_A_DICT_POS == pos) return NOT_A_PROBABILITY;
    int nextWordPos = mDictionaryStructurePolicy->getTerminalNodePositionOfWord(word1, length1,
            false /* forceLowerCaseSearch */);
    if (NOT_A_DICT_POS == nextWordPos) return NOT_A_PROBABILITY;

    BinaryDictionaryBigramsIterator bigramsIt(
            mDictionaryStructurePolicy->getBigramsStructurePolicy(), pos);
    while (bigramsIt.hasNext()) {
        bigramsIt.next();
        if (bigramsIt.getBigramPos() == nextWordPos) {
            return mDictionaryStructurePolicy->getProbability(
                    mDictionaryStructurePolicy->getUnigramProbabilityOfPtNode(nextWordPos),
                    bigramsIt.getProbability());
        }
    }
    return NOT_A_PROBABILITY;
}

// TODO: Move functions related to bigram to here
} // namespace latinime
