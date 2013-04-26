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
#include "binary_format.h"
#include "bloom_filter.h"
#include "char_utils.h"
#include "defines.h"
#include "dictionary.h"

namespace latinime {

BigramDictionary::BigramDictionary(const uint8_t *const streamStart) : DICT_ROOT(streamStart) {
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
                && length < getCodePointCount(MAX_WORD_LENGTH,
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
 * inputCodePoints: what user typed, in the same format as for UnigramDictionary::getSuggestions.
 * inputSize: the size of the codes array.
 * bigramCodePoints: an array for output, at the same format as outwords for getSuggestions.
 * bigramProbability: an array to output frequencies.
 * outputTypes: an array to output types.
 * This method returns the number of bigrams this word has, for backward compatibility.
 * Note: this is not the number of bigrams output in the array, which is the number of
 * bigrams this word has WHOSE first letter also matches the letter the user typed.
 * TODO: this may not be a sensible thing to do. It makes sense when the bigrams are
 * used to match the first letter of the second word, but once the user has typed more
 * and the bigrams are used to boost unigram result scores, it makes little sense to
 * reduce their scope to the ones that match the first letter.
 */
int BigramDictionary::getBigrams(const int *prevWord, int prevWordLength, int *inputCodePoints,
        int inputSize, int *bigramCodePoints, int *bigramProbability, int *outputTypes) const {
    // TODO: remove unused arguments, and refrain from storing stuff in members of this class
    // TODO: have "in" arguments before "out" ones, and make out args explicit in the name

    const uint8_t *const root = DICT_ROOT;
    int pos = getBigramListPositionForWord(prevWord, prevWordLength,
            false /* forceLowerCaseSearch */);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (0 == pos) {
        // If no bigrams for this exact word, search again in lower case.
        pos = getBigramListPositionForWord(prevWord, prevWordLength,
                true /* forceLowerCaseSearch */);
    }
    // If still no bigrams, we really don't have them!
    if (0 == pos) return 0;
    uint8_t bigramFlags;
    int bigramCount = 0;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        int bigramBuffer[MAX_WORD_LENGTH];
        int unigramProbability = 0;
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        const int length = BinaryFormat::getWordAtAddress(root, bigramPos, MAX_WORD_LENGTH,
                bigramBuffer, &unigramProbability);

        // inputSize == 0 means we are trying to find bigram predictions.
        if (inputSize < 1 || checkFirstCharacter(bigramBuffer, inputCodePoints)) {
            const int bigramProbabilityTemp =
                    BinaryFormat::MASK_ATTRIBUTE_PROBABILITY & bigramFlags;
            // Due to space constraints, the probability for bigrams is approximate - the lower the
            // unigram probability, the worse the precision. The theoritical maximum error in
            // resulting probability is 8 - although in the practice it's never bigger than 3 or 4
            // in very bad cases. This means that sometimes, we'll see some bigrams interverted
            // here, but it can't get too bad.
            const int probability = BinaryFormat::computeProbabilityForBigram(
                    unigramProbability, bigramProbabilityTemp);
            addWordBigram(bigramBuffer, length, probability, bigramProbability, bigramCodePoints,
                    outputTypes);
            ++bigramCount;
        }
    } while (BinaryFormat::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags);
    return min(bigramCount, MAX_RESULTS);
}

// Returns a pointer to the start of the bigram list.
// If the word is not found or has no bigrams, this function returns 0.
int BigramDictionary::getBigramListPositionForWord(const int *prevWord, const int prevWordLength,
        const bool forceLowerCaseSearch) const {
    if (0 >= prevWordLength) return 0;
    const uint8_t *const root = DICT_ROOT;
    int pos = BinaryFormat::getTerminalPosition(root, prevWord, prevWordLength,
            forceLowerCaseSearch);

    if (NOT_VALID_WORD == pos) return 0;
    const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
    if (0 == (flags & BinaryFormat::FLAG_HAS_BIGRAMS)) return 0;
    if (0 == (flags & BinaryFormat::FLAG_HAS_MULTIPLE_CHARS)) {
        BinaryFormat::getCodePointAndForwardPointer(root, &pos);
    } else {
        pos = BinaryFormat::skipOtherCharacters(root, pos);
    }
    pos = BinaryFormat::skipProbability(flags, pos);
    pos = BinaryFormat::skipChildrenPosition(flags, pos);
    pos = BinaryFormat::skipShortcuts(root, flags, pos);
    return pos;
}

void BigramDictionary::fillBigramAddressToProbabilityMapAndFilter(const int *prevWord,
        const int prevWordLength, std::map<int, int> *map, uint8_t *filter) const {
    memset(filter, 0, BIGRAM_FILTER_BYTE_SIZE);
    const uint8_t *const root = DICT_ROOT;
    int pos = getBigramListPositionForWord(prevWord, prevWordLength,
            false /* forceLowerCaseSearch */);
    if (0 == pos) {
        // If no bigrams for this exact string, search again in lower case.
        pos = getBigramListPositionForWord(prevWord, prevWordLength,
                true /* forceLowerCaseSearch */);
    }
    if (0 == pos) return;

    uint8_t bigramFlags;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        const int probability = BinaryFormat::MASK_ATTRIBUTE_PROBABILITY & bigramFlags;
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        (*map)[bigramPos] = probability;
        setInFilter(filter, bigramPos);
    } while (BinaryFormat::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags);
}

bool BigramDictionary::checkFirstCharacter(int *word, int *inputCodePoints) const {
    // Checks whether this word starts with same character or neighboring characters of
    // what user typed.

    int maxAlt = MAX_ALTERNATIVES;
    const int firstBaseLowerCodePoint = toBaseLowerCase(*word);
    while (maxAlt > 0) {
        if (toBaseLowerCase(*inputCodePoints) == firstBaseLowerCodePoint) {
            return true;
        }
        inputCodePoints++;
        maxAlt--;
    }
    return false;
}

bool BigramDictionary::isValidBigram(const int *word1, int length1, const int *word2,
        int length2) const {
    const uint8_t *const root = DICT_ROOT;
    int pos = getBigramListPositionForWord(word1, length1, false /* forceLowerCaseSearch */);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (0 == pos) return false;
    int nextWordPos = BinaryFormat::getTerminalPosition(root, word2, length2,
            false /* forceLowerCaseSearch */);
    if (NOT_VALID_WORD == nextWordPos) return false;
    uint8_t bigramFlags;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        if (bigramPos == nextWordPos) {
            return true;
        }
    } while (BinaryFormat::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags);
    return false;
}

// TODO: Move functions related to bigram to here
} // namespace latinime
