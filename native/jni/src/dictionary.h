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
class IncrementalDecoderInterface;
class ProximityInfo;
class UnigramDictionary;

class Dictionary {
 public:
    // Taken from SuggestedWords.java
    const static int KIND_TYPED = 0; // What user typed
    const static int KIND_CORRECTION = 1; // Simple correction/suggestion
    const static int KIND_COMPLETION = 2; // Completion (suggestion with appended chars)
    const static int KIND_WHITELIST = 3; // Whitelisted word
    const static int KIND_BLACKLIST = 4; // Blacklisted word
    const static int KIND_HARDCODED = 5; // Hardcoded suggestion, e.g. punctuation
    const static int KIND_APP_DEFINED = 6; // Suggested by the application
    const static int KIND_SHORTCUT = 7; // A shortcut
    const static int KIND_PREDICTION = 8; // A prediction (== a suggestion with no input)

    Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust, int typedLetterMultipler,
            int fullWordMultiplier, int maxWordLength, int maxWords, int maxPredictions);

    int getSuggestions(ProximityInfo *proximityInfo, void *traverseSession, int *xcoordinates,
            int *ycoordinates, int *times, int *pointerIds, int *codes, int codesSize,
            int *prevWordChars, int prevWordLength, int commitPoint, bool isGesture,
            bool useFullEditDistance, unsigned short *outWords,
            int *frequencies, int *spaceIndices, int *outputTypes) const;

    int getBigrams(const int32_t *word, int length, int *codes, int codesSize,
            unsigned short *outWords, int *frequencies, int *outputTypes) const;

    int getFrequency(const int32_t *word, int length) const;
    bool isValidBigram(const int32_t *word1, int length1, const int32_t *word2, int length2) const;
    const uint8_t *getDict() const { // required to release dictionary buffer
        return mDict;
    }
    const uint8_t *getOffsetDict() const {
        return mOffsetDict;
    }
    int getDictSize() const { return mDictSize; }
    int getMmapFd() const { return mMmapFd; }
    int getDictBufAdjust() const { return mDictBufAdjust; }
    virtual ~Dictionary();

    // public static utility methods
    // static inline methods should be defined in the header file
    static int wideStrLen(unsigned short *str);

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
    IncrementalDecoderInterface *mGestureDecoder;
};

// public static utility methods
// static inline methods should be defined in the header file
inline int Dictionary::wideStrLen(unsigned short *str) {
    if (!str) return 0;
    int length = 0;
    while (*str) {
        str++;
        length++;
    }
    return length;
}
} // namespace latinime
#endif // LATINIME_DICTIONARY_H
