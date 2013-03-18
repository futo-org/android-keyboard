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

class BigramDictionary {
 public:
    BigramDictionary(const uint8_t *const streamStart);
    int getBigrams(const int *word, int length, int *inputCodePoints, int inputSize, int *outWords,
            int *frequencies, int *outputTypes) const;
    void fillBigramAddressToProbabilityMapAndFilter(const int *prevWord, const int prevWordLength,
            std::map<int, int> *map, uint8_t *filter) const;
    bool isValidBigram(const int *word1, int length1, const int *word2, int length2) const;
    ~BigramDictionary();
 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(BigramDictionary);
    void addWordBigram(int *word, int length, int probability, int *bigramProbability,
            int *bigramCodePoints, int *outputTypes) const;
    bool checkFirstCharacter(int *word, int *inputCodePoints) const;
    int getBigramListPositionForWord(const int *prevWord, const int prevWordLength,
            const bool forceLowerCaseSearch) const;

    const uint8_t *const DICT_ROOT;
    // TODO: Re-implement proximity correction for bigram correction
    static const int MAX_ALTERNATIVES = 1;
};
} // namespace latinime
#endif // LATINIME_BIGRAM_DICTIONARY_H
