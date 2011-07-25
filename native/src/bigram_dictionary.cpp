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
#include "dictionary.h"
#include "binary_format.h"

namespace latinime {

BigramDictionary::BigramDictionary(const unsigned char *dict, int maxWordLength,
        int maxAlternatives, const bool isLatestDictVersion, const bool hasBigram,
        Dictionary *parentDictionary)
    : DICT(dict + NEW_DICTIONARY_HEADER_SIZE), MAX_WORD_LENGTH(maxWordLength),
    MAX_ALTERNATIVES(maxAlternatives), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    HAS_BIGRAM(hasBigram), mParentDictionary(parentDictionary) {
    if (DEBUG_DICT) {
        LOGI("BigramDictionary - constructor");
        LOGI("Has Bigram : %d", hasBigram);
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
        LOGI("Bigram: Found word = %s, freq = %d :", s, frequency);
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
        LOGI("Bigram: InsertAt -> %d maxBigrams: %d", insertAt, mMaxBigrams);
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
            LOGI("Bigram: Added word at %d", insertAt);
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
 * maxAlteratives: unused.
 * This method returns the number of bigrams this word has, for backward compatibility.
 * Note: this is not the number of bigrams output in the array, which is the number of
 * bigrams this word has WHOSE first letter also matches the letter the user typed.
 * TODO: this may not be a sensible thing to do. It makes sense when the bigrams are
 * used to match the first letter of the second word, but once the user has typed more
 * and the bigrams are used to boost unigram result scores, it makes little sense to
 * reduce their scope to the ones that match the first letter.
 */
int BigramDictionary::getBigrams(unsigned short *prevWord, int prevWordLength, int *codes,
        int codesSize, unsigned short *bigramChars, int *bigramFreq, int maxWordLength,
        int maxBigrams, int maxAlternatives) {
    // TODO: remove unused arguments, and refrain from storing stuff in members of this class
    // TODO: have "in" arguments before "out" ones, and make out args explicit in the name
    mBigramFreq = bigramFreq;
    mBigramChars = bigramChars;
    mInputCodes = codes;
    mMaxBigrams = maxBigrams;

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
    pos = BinaryFormat::skipChildrenPosition(flags, pos);
    pos = BinaryFormat::skipFrequency(flags, pos);
    int bigramFlags;
    int bigramCount = 0;
    do {
        bigramFlags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
        uint16_t bigramBuffer[MAX_WORD_LENGTH];
        const int bigramPos = BinaryFormat::getAttributeAddressAndForwardPointer(root, bigramFlags,
                &pos);
        const int length = BinaryFormat::getWordAtAddress(root, bigramPos, MAX_WORD_LENGTH,
                bigramBuffer);

        if (checkFirstCharacter(bigramBuffer)) {
            const int frequency = UnigramDictionary::MASK_ATTRIBUTE_FREQUENCY & bigramFlags;
            addWordBigram(bigramBuffer, length, frequency);
        }
        ++bigramCount;
    } while (0 != (UnigramDictionary::FLAG_ATTRIBUTE_HAS_NEXT & bigramFlags));
    return bigramCount;
}

bool BigramDictionary::checkFirstCharacter(unsigned short *word) {
    // Checks whether this word starts with same character or neighboring characters of
    // what user typed.

    int *inputCodes = mInputCodes;
    int maxAlt = MAX_ALTERNATIVES;
    while (maxAlt > 0) {
        if ((unsigned int) *inputCodes == (unsigned int) *word) {
            return true;
        }
        inputCodes++;
        maxAlt--;
    }
    return false;
}

// TODO: Move functions related to bigram to here
} // namespace latinime
