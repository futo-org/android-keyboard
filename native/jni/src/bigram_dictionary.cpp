/*
**
** Copyright 2010, The Android Open Source Project
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

#include <string.h>

#define LOG_TAG "LatinIME: bigram_dictionary.cpp"

#include "bigram_dictionary.h"
#include "binary_format.h"
#include "bloom_filter.h"
#include "defines.h"
#include "dictionary.h"

namespace latinime {

BigramDictionary::BigramDictionary(const unsigned char *dict, int maxWordLength,
        Dictionary *parentDictionary)
    : DICT(dict), MAX_WORD_LENGTH(maxWordLength), mParentDictionary(parentDictionary) {
    if (DEBUG_DICT) {
        AKLOGI("BigramDictionary - constructor");
    }
}

BigramDictionary::~BigramDictionary() {
}

bool BigramDictionary::addWordBigram(unsigned short *word, int length, int frequency) {
    word[length] = 0;
    if (DEBUG_DICT) {
#ifdef FLAG_DBG
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        AKLOGI("Bigram: Found word = %s, freq = %d :", s, frequency);
#endif
    }

    // Find the right insertion point
    int insertAt = 0;
    while (insertAt < mMaxBigrams) {
        if (frequency > mBigramFreq[insertAt] || (mBigramFreq[insertAt] == frequency
                && length < Dictionary::wideStrLen(mBigramChars + insertAt * MAX_WORD_LENGTH))) {
            break;
        }
        insertAt++;
    }
    if (DEBUG_DICT) {
        AKLOGI("Bigram: InsertAt -> %d maxBigrams: %d", insertAt, mMaxBigrams);
    }
    if (insertAt < mMaxBigrams) {
        memmove((char*) mBigramFreq + (insertAt + 1) * sizeof(mBigramFreq[0]),
               (char*) mBigramFreq + insertAt * sizeof(mBigramFreq[0]),
               (mMaxBigrams - insertAt - 1) * sizeof(mBigramFreq[0]));
        mBigramFreq[insertAt] = frequency;
        memmove((char*) mBigramChars + (insertAt + 1) * MAX_WORD_LENGTH * sizeof(short),
               (char*) mBigramChars + (insertAt    ) * MAX_WORD_LENGTH * sizeof(short),
               (mMaxBigrams - insertAt - 1) * sizeof(short) * MAX_WORD_LENGTH);
        unsigned short *dest = mBigramChars + (insertAt    ) * MAX_WORD_LENGTH;
        while (length--) {
            *dest++ = *word++;
        }
        *dest = 0; // NULL terminate
        if (DEBUG_DICT) {
            AKLOGI("Bigram: Added word at %d", insertAt);
        }
        return true;
    }
    return false;
}

/* Parameters :
 * prevWord: the word before, the one for which we need to look up bigrams.
 * prevWordLength: its length.
 * codes: what user typed, in the same format as for UnigramDictionary::getSuggestions.
 * codesSize: the size of the codes array.
 * bigramChars: an array for output, at the same format as outwords for getSuggestions.
 * bigramFreq: an array to output frequencies.
 * maxWordLength: the maximum size of a word.
 * maxBigrams: the maximum number of bigrams fitting in the bigramChars array.
 * This method returns the number of bigrams this word has, for backward compatibility.
 * Note: this is not the number of bigrams output in the array, which is the number of
 * bigrams this word has WHOSE first letter also matches the letter the user typed.
 * TODO: this may not be a sensible thing to do. It makes sense when the bigrams are
 * used to match the first letter of the second word, but once the user has typed more
 * and the bigrams are used to boost unigram result scores, it makes little sense to
 * reduce their scope to the ones that match the first letter.
 */
int BigramDictionary::getBigrams(const int32_t *prevWord, int prevWordLength, int *codes,
        int codesSize, unsigned short *bigramChars, int *bigramFreq, int maxWordLength,
        int maxBigrams) {
    // TODO: remove unused arguments, and refrain from storing stuff in members of this class
    // TODO: have "in" arguments before "out" ones, and make out args explicit in the name
    mBigramFreq = bigramFreq;
    mBigramChars = bigramChars;
    mInputCodes = codes;
    mMaxBigrams = maxBigrams;

    const uint8_t* const root = DICT;
    int pos = getBigramListPositionForWord(prevWord, prevWordLength);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (0 == pos) return 0;
    int bigramFlags;
    int bigramCount = 0;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        uint16_t bigramBuffer[MAX_WORD_LENGTH];
        int unigramFreq = 0;
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        const int length = BinaryFormat::getWordAtAddress(root, bigramPos, MAX_WORD_LENGTH,
                bigramBuffer, &unigramFreq);

        // codesSize == 0 means we are trying to find bigram predictions.
        if (codesSize < 1 || checkFirstCharacter(bigramBuffer)) {
            const int bigramFreq = UnigramDictionary::MASK_ATTRIBUTE_FREQUENCY & bigramFlags;
            // Due to space constraints, the frequency for bigrams is approximate - the lower the
            // unigram frequency, the worse the precision. The theoritical maximum error in
            // resulting frequency is 8 - although in the practice it's never bigger than 3 or 4
            // in very bad cases. This means that sometimes, we'll see some bigrams interverted
            // here, but it can't get too bad.
            const int frequency =
                    BinaryFormat::computeFrequencyForBigram(unigramFreq, bigramFreq);
            if (addWordBigram(bigramBuffer, length, frequency)) {
                ++bigramCount;
            }
        }
    } while (UnigramDictionary::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags);
    return bigramCount;
}

// Returns a pointer to the start of the bigram list.
// If the word is not found or has no bigrams, this function returns 0.
int BigramDictionary::getBigramListPositionForWord(const int32_t *prevWord,
        const int prevWordLength) {
    if (0 >= prevWordLength) return 0;
    const uint8_t* const root = DICT;
    int pos = BinaryFormat::getTerminalPosition(root, prevWord, prevWordLength);

    if (NOT_VALID_WORD == pos) return 0;
    const int flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
    if (0 == (flags & UnigramDictionary::FLAG_HAS_BIGRAMS)) return 0;
    if (0 == (flags & UnigramDictionary::FLAG_HAS_MULTIPLE_CHARS)) {
        BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
    } else {
        pos = BinaryFormat::skipOtherCharacters(root, pos);
    }
    pos = BinaryFormat::skipFrequency(flags, pos);
    pos = BinaryFormat::skipChildrenPosition(flags, pos);
    pos = BinaryFormat::skipShortcuts(root, flags, pos);
    return pos;
}

void BigramDictionary::fillBigramAddressToFrequencyMapAndFilter(const int32_t *prevWord,
        const int prevWordLength, std::map<int, int> *map, uint8_t *filter) {
    memset(filter, 0, BIGRAM_FILTER_BYTE_SIZE);
    const uint8_t* const root = DICT;
    int pos = getBigramListPositionForWord(prevWord, prevWordLength);
    if (0 == pos) return;

    int bigramFlags;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        const int frequency = UnigramDictionary::MASK_ATTRIBUTE_FREQUENCY & bigramFlags;
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        (*map)[bigramPos] = frequency;
        setInFilter(filter, bigramPos);
    } while (0 != (UnigramDictionary::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags));
}

bool BigramDictionary::checkFirstCharacter(unsigned short *word) {
    // Checks whether this word starts with same character or neighboring characters of
    // what user typed.

    int *inputCodes = mInputCodes;
    int maxAlt = MAX_ALTERNATIVES;
    const unsigned short firstBaseChar = toBaseLowerCase(*word);
    while (maxAlt > 0) {
        if (toBaseLowerCase(*inputCodes) == firstBaseChar) {
            return true;
        }
        inputCodes++;
        maxAlt--;
    }
    return false;
}

bool BigramDictionary::isValidBigram(const int32_t *word1, int length1, const int32_t *word2,
        int length2) {
    const uint8_t* const root = DICT;
    int pos = getBigramListPositionForWord(word1, length1);
    // getBigramListPositionForWord returns 0 if this word isn't in the dictionary or has no bigrams
    if (0 == pos) return false;
    int nextWordPos = BinaryFormat::getTerminalPosition(root, word2, length2);
    if (NOT_VALID_WORD == nextWordPos) return false;
    int bigramFlags;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        if (bigramPos == nextWordPos) {
            return true;
        }
    } while (UnigramDictionary::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags);
    return false;
}

// TODO: Move functions related to bigram to here
} // namespace latinime
