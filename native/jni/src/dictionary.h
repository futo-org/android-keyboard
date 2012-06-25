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

#include <map>

#include "bigram_dictionary.h"
#include "char_utils.h"
#include "defines.h"
#include "proximity_info.h"
#include "unigram_dictionary.h"
#include "words_priority_queue_pool.h"

namespace latinime {

class Dictionary {
 public:
    Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust, int typedLetterMultipler,
            int fullWordMultiplier, int maxWordLength, int maxWords);

    int getSuggestions(ProximityInfo *proximityInfo, int *xcoordinates, int *ycoordinates,
            int *times, int *pointerIds, int *codes, int codesSize, int *prevWordChars,
            int prevWordLength, int commitPoint, bool isGesture, int dicTypeId,
            bool useFullEditDistance, unsigned short *outWords,
            int *frequencies, int *spaceIndices) {
        int result = 0;
        std::map<int, int> bigramMap;
        uint8_t bigramFilter[BIGRAM_FILTER_BYTE_SIZE];
        mBigramDictionary->fillBigramAddressToFrequencyMapAndFilter(prevWordChars,
                prevWordLength, &bigramMap, bigramFilter);
        result = mUnigramDictionary->getSuggestions(proximityInfo, xcoordinates,
                ycoordinates, codes, codesSize, &bigramMap, bigramFilter,
                useFullEditDistance, outWords, frequencies);
        return result;
    }

    int getBigrams(const int32_t *word, int length, int *codes, int codesSize,
            unsigned short *outWords, int *frequencies, int maxWordLength, int maxBigrams) const {
        return mBigramDictionary->getBigrams(word, length, codes, codesSize, outWords, frequencies,
                maxWordLength, maxBigrams);
    }

    int getFrequency(const int32_t *word, int length) const;
    bool isValidBigram(const int32_t *word1, int length1, const int32_t *word2, int length2) const;
    void *getDict() const { return (void *)mDict; }
    int getDictSize() const { return mDictSize; }
    int getMmapFd() const { return mMmapFd; }
    int getDictBufAdjust() const { return mDictBufAdjust; }
    ~Dictionary();

    // public static utility methods
    // static inline methods should be defined in the header file
    static int wideStrLen(unsigned short *str);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Dictionary);
    const unsigned char *mDict;

    // Used only for the mmap version of dictionary loading, but we use these as dummy variables
    // also for the malloc version.
    const int mDictSize;
    const int mMmapFd;
    const int mDictBufAdjust;

    const UnigramDictionary *mUnigramDictionary;
    const BigramDictionary *mBigramDictionary;
};

// public static utility methods
// static inline methods should be defined in the header file
inline int Dictionary::wideStrLen(unsigned short *str) {
    if (!str) return 0;
    unsigned short *end = str;
    while (*end)
        end++;
    return end - str;
}
} // namespace latinime

#endif // LATINIME_DICTIONARY_H
