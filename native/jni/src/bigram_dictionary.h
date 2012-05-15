/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LATINIME_BIGRAM_DICTIONARY_H
#define LATINIME_BIGRAM_DICTIONARY_H

#include <map>
#include <stdint.h>

#include "defines.h"

namespace latinime {

class Dictionary;
class BigramDictionary {
 public:
    BigramDictionary(const unsigned char *dict, int maxWordLength, Dictionary *parentDictionary);
    int getBigrams(const int32_t *word, int length, int *codes, int codesSize,
            unsigned short *outWords, int *frequencies, int maxWordLength, int maxBigrams);
    int getBigramListPositionForWord(const int32_t *prevWord, const int prevWordLength);
    void fillBigramAddressToFrequencyMapAndFilter(const int32_t *prevWord, const int prevWordLength,
            std::map<int, int> *map, uint8_t *filter);
    bool isValidBigram(const int32_t *word1, int length1, const int32_t *word2, int length2);
    ~BigramDictionary();
 private:
    bool addWordBigram(unsigned short *word, int length, int frequency);
    int getBigramAddress(int *pos, bool advance);
    int getBigramFreq(int *pos);
    void searchForTerminalNode(int addressLookingFor, int frequency);
    bool getFirstBitOfByte(int *pos) { return (DICT[*pos] & 0x80) > 0; }
    bool getSecondBitOfByte(int *pos) { return (DICT[*pos] & 0x40) > 0; }
    bool checkFirstCharacter(unsigned short *word);

    const unsigned char *DICT;
    const int MAX_WORD_LENGTH;
    // TODO: Re-implement proximity correction for bigram correction
    static const int MAX_ALTERNATIVES = 1;

    Dictionary *mParentDictionary;
    int *mBigramFreq;
    int mMaxBigrams;
    unsigned short *mBigramChars;
    int *mInputCodes;
    int mInputLength;
};

} // namespace latinime

#endif // LATINIME_BIGRAM_DICTIONARY_H
