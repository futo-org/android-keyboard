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

#include "basechars.h"
#include "bigram_dictionary.h"
#include "char_utils.h"
#include "defines.h"
#include "proximity_info.h"
#include "unigram_dictionary.h"

namespace latinime {

class Dictionary {
public:
    Dictionary(void *dict, int dictSize, int mmapFd, int dictBufAdjust, int typedLetterMultipler,
            int fullWordMultiplier, int maxWordLength, int maxWords, int maxAlternatives);
    int getSuggestions(ProximityInfo *proximityInfo, int *xcoordinates, int *ycoordinates,
            int *codes, int codesSize, int flags, unsigned short *outWords, int *frequencies) {
        return mUnigramDictionary->getSuggestions(proximityInfo, xcoordinates, ycoordinates, codes,
                codesSize, flags, outWords, frequencies);
    }

    // TODO: Call mBigramDictionary instead of mUnigramDictionary
    int getBigrams(unsigned short *word, int length, int *codes, int codesSize,
            unsigned short *outWords, int *frequencies, int maxWordLength, int maxBigrams,
            int maxAlternatives) {
        return mBigramDictionary->getBigrams(word, length, codes, codesSize, outWords, frequencies,
                maxWordLength, maxBigrams, maxAlternatives);
    }

    bool isValidWord(unsigned short *word, int length);
    void *getDict() { return (void *)mDict; }
    int getDictSize() { return mDictSize; }
    int getMmapFd() { return mMmapFd; }
    int getDictBufAdjust() { return mDictBufAdjust; }
    ~Dictionary();

    // public static utility methods
    // static inline methods should be defined in the header file
    static unsigned short getChar(const unsigned char *dict, int *pos);
    static int getCount(const unsigned char *dict, int *pos);
    static bool getTerminal(const unsigned char *dict, int *pos);
    static int getAddress(const unsigned char *dict, int *pos);
    static int getFreq(const unsigned char *dict, const bool isLatestDictVersion, int *pos);
    static int wideStrLen(unsigned short *str);
    // returns next sibling's position
    static int setDictionaryValues(const unsigned char *dict, const bool isLatestDictVersion,
            const int pos, unsigned short *c, int *childrenPosition,
            bool *terminal, int *freq);
    static inline unsigned short toBaseLowerCase(unsigned short c);

private:
    bool hasBigram();

    const unsigned char *mDict;

    // Used only for the mmap version of dictionary loading, but we use these as dummy variables
    // also for the malloc version.
    const int mDictSize;
    const int mMmapFd;
    const int mDictBufAdjust;

    const bool IS_LATEST_DICT_VERSION;
    UnigramDictionary *mUnigramDictionary;
    BigramDictionary *mBigramDictionary;
};

// public static utility methods
// static inline methods should be defined in the header file
inline unsigned short Dictionary::getChar(const unsigned char *dict, int *pos) {
    unsigned short ch = (unsigned short) (dict[(*pos)++] & 0xFF);
    // If the code is 255, then actual 16 bit code follows (in big endian)
    if (ch == 0xFF) {
        ch = ((dict[*pos] & 0xFF) << 8) | (dict[*pos + 1] & 0xFF);
        (*pos) += 2;
    }
    return ch;
}

inline int Dictionary::getCount(const unsigned char *dict, int *pos) {
    return dict[(*pos)++] & 0xFF;
}

inline bool Dictionary::getTerminal(const unsigned char *dict, int *pos) {
    return (dict[*pos] & FLAG_TERMINAL_MASK) > 0;
}

inline int Dictionary::getAddress(const unsigned char *dict, int *pos) {
    int address = 0;
    if ((dict[*pos] & FLAG_ADDRESS_MASK) == 0) {
        *pos += 1;
    } else {
        address += (dict[*pos] & (ADDRESS_MASK >> 16)) << 16;
        address += (dict[*pos + 1] & 0xFF) << 8;
        address += (dict[*pos + 2] & 0xFF);
        *pos += 3;
    }
    return address;
}

inline int Dictionary::getFreq(const unsigned char *dict,
        const bool isLatestDictVersion, int *pos) {
    int freq = dict[(*pos)++] & 0xFF;
    if (isLatestDictVersion) {
        // skipping bigram
        int bigramExist = (dict[*pos] & FLAG_BIGRAM_READ);
        if (bigramExist > 0) {
            int nextBigramExist = 1;
            while (nextBigramExist > 0) {
                (*pos) += 3;
                nextBigramExist = (dict[(*pos)++] & FLAG_BIGRAM_CONTINUED);
            }
        } else {
            (*pos)++;
        }
    }
    return freq;
}

inline int Dictionary::wideStrLen(unsigned short *str) {
    if (!str) return 0;
    unsigned short *end = str;
    while (*end)
        end++;
    return end - str;
}

inline int Dictionary::setDictionaryValues(const unsigned char *dict,
        const bool isLatestDictVersion, const int pos, unsigned short *c,int *childrenPosition,
        bool *terminal, int *freq) {
    int position = pos;
    // -- at char
    *c = Dictionary::getChar(dict, &position);
    // -- at flag/add
    *terminal = Dictionary::getTerminal(dict, &position);
    *childrenPosition = Dictionary::getAddress(dict, &position);
    // -- after address or flag
    *freq = (*terminal) ? Dictionary::getFreq(dict, isLatestDictVersion, &position) : 1;
    // returns next sibling's position
    return position;
}


inline unsigned short Dictionary::toBaseLowerCase(unsigned short c) {
    if (c < sizeof(BASE_CHARS) / sizeof(BASE_CHARS[0])) {
        c = BASE_CHARS[c];
    }
    if (c >='A' && c <= 'Z') {
        c |= 32;
    } else if (c > 127) {
        c = latin_tolower(c);
    }
    return c;
}

} // namespace latinime

#endif // LATINIME_DICTIONARY_H
