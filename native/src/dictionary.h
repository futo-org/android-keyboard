/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef LATINIME_DICTIONARY_H
#define LATINIME_DICTIONARY_H

#include "bigram_dictionary.h"
#include "unigram_dictionary.h"

namespace latinime {

// 22-bit address = ~4MB dictionary size limit, which on average would be about 200k-300k words
#define ADDRESS_MASK 0x3FFFFF

// The bit that decides if an address follows in the next 22 bits
#define FLAG_ADDRESS_MASK 0x40
// The bit that decides if this is a terminal node for a word. The node could still have children,
// if the word has other endings.
#define FLAG_TERMINAL_MASK 0x80

#define FLAG_BIGRAM_READ 0x80
#define FLAG_BIGRAM_CHILDEXIST 0x40
#define FLAG_BIGRAM_CONTINUED 0x80
#define FLAG_BIGRAM_FREQ 0x7F

class Dictionary {
public:
    Dictionary(void *dict, int typedLetterMultipler, int fullWordMultiplier, int maxWordLength,
            int maxWords, int maxAlternatives);
    int getSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies,
            int *nextLetters, int nextLettersSize) {
        return mUnigramDictionary->getSuggestions(codes, codesSize, outWords, frequencies,
                nextLetters, nextLettersSize);
    }

    // TODO: Call mBigramDictionary instead of mUnigramDictionary
    int getBigrams(unsigned short *word, int length, int *codes, int codesSize,
            unsigned short *outWords, int *frequencies, int maxWordLength, int maxBigrams,
            int maxAlternatives) {
        return mUnigramDictionary->getBigrams(word, length, codes, codesSize, outWords, frequencies,
                maxWordLength, maxBigrams, maxAlternatives);
    }
    bool isValidWord(unsigned short *word, int length) {
        return mUnigramDictionary->isValidWord(word, length);
    }
    void setAsset(void *asset) { mAsset = asset; }
    void *getAsset() { return mAsset; }
    ~Dictionary();

private:
    void *mAsset;
    BigramDictionary *mBigramDictionary;
    UnigramDictionary *mUnigramDictionary;
};

// ----------------------------------------------------------------------------

}; // namespace latinime

#endif // LATINIME_DICTIONARY_H
