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

#include <stdint.h>

#include "defines.h"

namespace latinime {

class BigramDictionary;
class ProximityInfo;
class SuggestInterface;
class UnigramDictionary;

class Dictionary {
 public:
    // Taken from SuggestedWords.java
    static const int KIND_MASK_KIND = 0xFF; // Mask to get only the kind
    static const int KIND_TYPED = 0; // What user typed
    static const int KIND_CORRECTION = 1; // Simple correction/suggestion
    static const int KIND_COMPLETION = 2; // Completion (suggestion with appended chars)
    static const int KIND_WHITELIST = 3; // Whitelisted word
    static const int KIND_BLACKLIST = 4; // Blacklisted word
    static const int KIND_HARDCODED = 5; // Hardcoded suggestion, e.g. punctuation
    static const int KIND_APP_DEFINED = 6; // Suggested by the application
    static const int KIND_SHORTCUT = 7; // A shortcut
    static const int KIND_PREDICTION = 8; // A prediction (== a suggestion with no input)

    static const int KIND_MASK_FLAGS = 0xFFFFFF00; // Mask to get the flags
    static const int KIND_FLAG_POSSIBLY_OFFENSIVE = 0x80000000;
    static const int KIND_FLAG_EXACT_MATCH = 0x40000000;

    Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust);

    int getSuggestions(ProximityInfo *proximityInfo, void *traverseSession, int *xcoordinates,
            int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints, int inputSize,
            int *prevWordCodePoints, int prevWordLength, int commitPoint, bool isGesture,
            bool useFullEditDistance, int *outWords, int *frequencies, int *spaceIndices,
            int *outputTypes) const;

    int getBigrams(const int *word, int length, int *inputCodePoints, int inputSize, int *outWords,
            int *frequencies, int *outputTypes) const;

    int getProbability(const int *word, int length) const;
    bool isValidBigram(const int *word1, int length1, const int *word2, int length2) const;
    const uint8_t *getDict() const { // required to release dictionary buffer
        return mDict;
    }
    const uint8_t *getOffsetDict() const {
        return mOffsetDict;
    }
    int getDictSize() const { return mDictSize; }
    int getMmapFd() const { return mMmapFd; }
    int getDictBufAdjust() const { return mDictBufAdjust; }
    int getDictFlags() const;
    virtual ~Dictionary();

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Dictionary);
    const uint8_t *mDict;
    const uint8_t *mOffsetDict;

    // Used only for the mmap version of dictionary loading, but we use these as dummy variables
    // also for the malloc version.
    const int mDictSize;
    const int mMmapFd;
    const int mDictBufAdjust;

    const UnigramDictionary *mUnigramDictionary;
    const BigramDictionary *mBigramDictionary;
    SuggestInterface *mGestureSuggest;
    SuggestInterface *mTypingSuggest;
};
} // namespace latinime
#endif // LATINIME_DICTIONARY_H
