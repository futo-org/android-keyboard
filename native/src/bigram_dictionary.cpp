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

namespace latinime {

BigramDictionary::BigramDictionary(const unsigned char *dict, int maxWordLength,
        int maxAlternatives, const bool isLatestDictVersion, const bool hasBigram,
        Dictionary *parentDictionary)
    : DICT(dict), MAX_WORD_LENGTH(maxWordLength),
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
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        LOGI("Bigram: Found word = %s, freq = %d :", s, frequency);
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

int BigramDictionary::getBigramAddress(int *pos, bool advance) {
    int address = 0;

    address += (DICT[*pos] & 0x3F) << 16;
    address += (DICT[*pos + 1] & 0xFF) << 8;
    address += (DICT[*pos + 2] & 0xFF);

    if (advance) {
        *pos += 3;
    }

    return address;
}

int BigramDictionary::getBigramFreq(int *pos) {
    int freq = DICT[(*pos)++] & FLAG_BIGRAM_FREQ;

    return freq;
}


int BigramDictionary::getBigrams(unsigned short *prevWord, int prevWordLength, int *codes,
        int codesSize, unsigned short *bigramChars, int *bigramFreq, int maxWordLength,
        int maxBigrams, int maxAlternatives) {
    mBigramFreq = bigramFreq;
    mBigramChars = bigramChars;
    mInputCodes = codes;
    mInputLength = codesSize;
    mMaxBigrams = maxBigrams;

    if (HAS_BIGRAM && IS_LATEST_DICT_VERSION) {
        int pos = mParentDictionary->isValidWordRec(
                DICTIONARY_HEADER_SIZE, prevWord, 0, prevWordLength);
        if (DEBUG_DICT) {
            LOGI("Pos -> %d", pos);
        }
        if (pos < 0) {
            return 0;
        }

        int bigramCount = 0;
        int bigramExist = (DICT[pos] & FLAG_BIGRAM_READ);
        if (bigramExist > 0) {
            int nextBigramExist = 1;
            while (nextBigramExist > 0 && bigramCount < maxBigrams) {
                int bigramAddress = getBigramAddress(&pos, true);
                int frequency = (FLAG_BIGRAM_FREQ & DICT[pos]);
                // search for all bigrams and store them
                searchForTerminalNode(bigramAddress, frequency);
                nextBigramExist = (DICT[pos++] & FLAG_BIGRAM_CONTINUED);
                bigramCount++;
            }
        }

        return bigramCount;
    }
    return 0;
}

void BigramDictionary::searchForTerminalNode(int addressLookingFor, int frequency) {
    // track word with such address and store it in an array
    unsigned short word[MAX_WORD_LENGTH];

    int pos;
    int followDownBranchAddress = DICTIONARY_HEADER_SIZE;
    bool found = false;
    char followingChar = ' ';
    int depth = -1;

    while(!found) {
        bool followDownAddressSearchStop = false;
        bool firstAddress = true;
        bool haveToSearchAll = true;

        if (depth < MAX_WORD_LENGTH && depth >= 0) {
            word[depth] = (unsigned short) followingChar;
        }
        pos = followDownBranchAddress; // pos start at count
        int count = DICT[pos] & 0xFF;
        if (DEBUG_DICT) {
            LOGI("count - %d",count);
        }
        pos++;
        for (int i = 0; i < count; i++) {
            // pos at data
            pos++;
            // pos now at flag
            if (!getFirstBitOfByte(&pos)) { // non-terminal
                if (!followDownAddressSearchStop) {
                    int addr = getBigramAddress(&pos, false);
                    if (addr > addressLookingFor) {
                        followDownAddressSearchStop = true;
                        if (firstAddress) {
                            firstAddress = false;
                            haveToSearchAll = true;
                        } else if (!haveToSearchAll) {
                            break;
                        }
                    } else {
                        followDownBranchAddress = addr;
                        followingChar = (char)(0xFF & DICT[pos-1]);
                        if (firstAddress) {
                            firstAddress = false;
                            haveToSearchAll = false;
                        }
                    }
                }
                pos += 3;
            } else if (getFirstBitOfByte(&pos)) { // terminal
                if (addressLookingFor == (pos-1)) { // found !!
                    depth++;
                    word[depth] = (0xFF & DICT[pos-1]);
                    found = true;
                    break;
                }
                if (getSecondBitOfByte(&pos)) { // address + freq (4 byte)
                    if (!followDownAddressSearchStop) {
                        int addr = getBigramAddress(&pos, false);
                        if (addr > addressLookingFor) {
                            followDownAddressSearchStop = true;
                            if (firstAddress) {
                                firstAddress = false;
                                haveToSearchAll = true;
                            } else if (!haveToSearchAll) {
                                break;
                            }
                        } else {
                            followDownBranchAddress = addr;
                            followingChar = (char)(0xFF & DICT[pos-1]);
                            if (firstAddress) {
                                firstAddress = false;
                                haveToSearchAll = true;
                            }
                        }
                    }
                    pos += 4;
                } else { // freq only (2 byte)
                    pos += 2;
                }

                // skipping bigram
                int bigramExist = (DICT[pos] & FLAG_BIGRAM_READ);
                if (bigramExist > 0) {
                    int nextBigramExist = 1;
                    while (nextBigramExist > 0) {
                        pos += 3;
                        nextBigramExist = (DICT[pos++] & FLAG_BIGRAM_CONTINUED);
                    }
                } else {
                    pos++;
                }
            }
        }
        depth++;
        if (followDownBranchAddress == 0) {
            if (DEBUG_DICT) {
                LOGI("ERROR!!! Cannot find bigram!!");
            }
            break;
        }
    }
    if (checkFirstCharacter(word)) {
        addWordBigram(word, depth, frequency);
    }
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
