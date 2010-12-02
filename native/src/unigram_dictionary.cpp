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

#include <stdio.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <string.h>

#define LOG_TAG "LatinIME: unigram_dictionary.cpp"

#include "basechars.h"
#include "char_utils.h"
#include "dictionary.h"
#include "unigram_dictionary.h"

namespace latinime {

UnigramDictionary::UnigramDictionary(const unsigned char *dict, int typedLetterMultiplier,
        int fullWordMultiplier, int maxWordLength, int maxWords, int maxAlternatives,
        const bool isLatestDictVersion, const bool hasBigram, Dictionary *parentDictionary)
    : DICT(dict), MAX_WORD_LENGTH(maxWordLength),MAX_WORDS(maxWords),
    MAX_ALTERNATIVES(maxAlternatives), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    HAS_BIGRAM(hasBigram), mParentDictionary(parentDictionary)
{
    LOGI("UnigramDictionary - constructor");
    LOGI("Has Bigram : %d \n", hasBigram);
    mTypedLetterMultiplier = typedLetterMultiplier;
    mFullWordMultiplier = fullWordMultiplier;
}

UnigramDictionary::~UnigramDictionary()
{
}

int UnigramDictionary::getSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies,
        int *nextLetters, int nextLettersSize)
{

    initSuggestions(codes, codesSize, outWords, frequencies);

    int suggestedWordsCount = getSuggestionCandidates(codesSize, -1, nextLetters,
            nextLettersSize);

    // If there aren't sufficient suggestions, search for words by allowing wild cards at
    // the different character positions. This feature is not ready for prime-time as we need
    // to figure out the best ranking for such words compared to proximity corrections and
    // completions.
    if (SUGGEST_MISSING_CHARACTERS && suggestedWordsCount < SUGGEST_MISSING_CHARACTERS_THRESHOLD) {
        for (int i = 0; i < codesSize; ++i) {
            int tempCount = getSuggestionCandidates(codesSize, i, NULL, 0);
            if (tempCount > suggestedWordsCount) {
                suggestedWordsCount = tempCount;
                break;
            }
        }
    }

    if (DEBUG_DICT) {
        LOGI("Returning %d words", suggestedWordsCount);
        LOGI("Next letters: ");
        for (int k = 0; k < nextLettersSize; k++) {
            if (nextLetters[k] > 0) {
                LOGI("%c = %d,", k, nextLetters[k]);
            }
        }
        LOGI("\n");
    }
    return suggestedWordsCount;
}

void UnigramDictionary::initSuggestions(int *codes, int codesSize, unsigned short *outWords,
        int *frequencies) {
    mFrequencies = frequencies;
    mOutputChars = outWords;
    mInputCodes = codes;
    mInputLength = codesSize;
    mMaxEditDistance = mInputLength < 5 ? 2 : mInputLength / 2;
}

int UnigramDictionary::getSuggestionCandidates(int inputLength, int skipPos,
        int *nextLetters, int nextLettersSize) {
    if (IS_LATEST_DICT_VERSION) {
        getWordsRec(DICTIONARY_HEADER_SIZE, 0, inputLength * 3, false, 1, 0, 0, skipPos,
                nextLetters, nextLettersSize);
    } else {
        getWordsRec(0, 0, inputLength * 3, false, 1, 0, 0, skipPos, nextLetters, nextLettersSize);
    }

    // Get the word count
    int suggestedWordsCount = 0;
    while (suggestedWordsCount < MAX_WORDS && mFrequencies[suggestedWordsCount] > 0) {
        suggestedWordsCount++;
    }
    return suggestedWordsCount;
}

void UnigramDictionary::registerNextLetter(unsigned short c, int *nextLetters, int nextLettersSize) {
    if (c < nextLettersSize) {
        nextLetters[c]++;
    }
}

int
UnigramDictionary::wideStrLen(unsigned short *str)
{
    if (!str) return 0;
    unsigned short *end = str;
    while (*end)
        end++;
    return end - str;
}

bool
UnigramDictionary::addWord(unsigned short *word, int length, int frequency)
{
    word[length] = 0;
    if (DEBUG_DICT) {
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        LOGI("Found word = %s, freq = %d : \n", s, frequency);
    }

    // Find the right insertion point
    int insertAt = 0;
    while (insertAt < MAX_WORDS) {
        if (frequency > mFrequencies[insertAt]
                 || (mFrequencies[insertAt] == frequency
                     && length < wideStrLen(mOutputChars + insertAt * MAX_WORD_LENGTH))) {
            break;
        }
        insertAt++;
    }
    if (insertAt < MAX_WORDS) {
        memmove((char*) mFrequencies + (insertAt + 1) * sizeof(mFrequencies[0]),
               (char*) mFrequencies + insertAt * sizeof(mFrequencies[0]),
               (MAX_WORDS - insertAt - 1) * sizeof(mFrequencies[0]));
        mFrequencies[insertAt] = frequency;
        memmove((char*) mOutputChars + (insertAt + 1) * MAX_WORD_LENGTH * sizeof(short),
               (char*) mOutputChars + (insertAt    ) * MAX_WORD_LENGTH * sizeof(short),
               (MAX_WORDS - insertAt - 1) * sizeof(short) * MAX_WORD_LENGTH);
        unsigned short *dest = mOutputChars + (insertAt    ) * MAX_WORD_LENGTH;
        while (length--) {
            *dest++ = *word++;
        }
        *dest = 0; // NULL terminate
        if (DEBUG_DICT) LOGI("Added word at %d\n", insertAt);
        return true;
    }
    return false;
}

bool
UnigramDictionary::addWordBigram(unsigned short *word, int length, int frequency)
{
    word[length] = 0;
    if (DEBUG_DICT) {
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        LOGI("Bigram: Found word = %s, freq = %d : \n", s, frequency);
    }

    // Find the right insertion point
    int insertAt = 0;
    while (insertAt < mMaxBigrams) {
        if (frequency > mBigramFreq[insertAt]
                 || (mBigramFreq[insertAt] == frequency
                     && length < wideStrLen(mBigramChars + insertAt * MAX_WORD_LENGTH))) {
            break;
        }
        insertAt++;
    }
    LOGI("Bigram: InsertAt -> %d maxBigrams: %d\n", insertAt, mMaxBigrams);
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
        if (DEBUG_DICT) LOGI("Bigram: Added word at %d\n", insertAt);
        return true;
    }
    return false;
}

unsigned short
UnigramDictionary::toLowerCase(unsigned short c) {
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

bool
UnigramDictionary::sameAsTyped(unsigned short *word, int length)
{
    if (length != mInputLength) {
        return false;
    }
    int *inputCodes = mInputCodes;
    while (length--) {
        if ((unsigned int) *inputCodes != (unsigned int) *word) {
            return false;
        }
        inputCodes += MAX_ALTERNATIVES;
        word++;
    }
    return true;
}

static char QUOTE = '\'';

void
UnigramDictionary::getWordsRec(int pos, int depth, int maxDepth, bool completion, int snr, int inputIndex,
                        int diffs, int skipPos, int *nextLetters, int nextLettersSize)
{
    // Optimization: Prune out words that are too long compared to how much was typed.
    if (depth > maxDepth) {
        return;
    }
    if (diffs > mMaxEditDistance) {
        return;
    }
    int count = Dictionary::getCount(DICT, &pos);
    int *currentChars = NULL;
    if (mInputLength <= inputIndex) {
        completion = true;
    } else {
        currentChars = mInputCodes + (inputIndex * MAX_ALTERNATIVES);
    }

    for (int i = 0; i < count; i++) {
        // -- at char
        unsigned short c = Dictionary::getChar(DICT, &pos);
        // -- at flag/add
        unsigned short lowerC = toLowerCase(c);
        bool terminal = Dictionary::getTerminal(DICT, &pos);
        int childrenAddress = Dictionary::getAddress(DICT, &pos);
        // -- after address or flag
        int freq = 1;
        if (terminal) freq = Dictionary::getFreq(DICT, IS_LATEST_DICT_VERSION, &pos);
        // -- after add or freq

        // If we are only doing completions, no need to look at the typed characters.
        if (completion) {
            mWord[depth] = c;
            if (terminal) {
                addWord(mWord, depth + 1, freq * snr);
                if (depth >= mInputLength && skipPos < 0) {
                    registerNextLetter(mWord[mInputLength], nextLetters, nextLettersSize);
                }
            }
            if (childrenAddress != 0) {
                getWordsRec(childrenAddress, depth + 1, maxDepth, completion, snr, inputIndex,
                        diffs, skipPos, nextLetters, nextLettersSize);
            }
        } else if ((c == QUOTE && currentChars[0] != QUOTE) || skipPos == depth) {
            // Skip the ' or other letter and continue deeper
            mWord[depth] = c;
            if (childrenAddress != 0) {
                getWordsRec(childrenAddress, depth + 1, maxDepth, false, snr, inputIndex, diffs,
                        skipPos, nextLetters, nextLettersSize);
            }
        } else {
            int j = 0;
            while (currentChars[j] > 0) {
                if (currentChars[j] == lowerC || currentChars[j] == c) {
                    int addedWeight = j == 0 ? mTypedLetterMultiplier : 1;
                    mWord[depth] = c;
                    if (mInputLength == inputIndex + 1) {
                        if (terminal) {
                            if (//INCLUDE_TYPED_WORD_IF_VALID ||
                                !sameAsTyped(mWord, depth + 1)) {
                                int finalFreq = freq * snr * addedWeight;
                                if (skipPos < 0) finalFreq *= mFullWordMultiplier;
                                addWord(mWord, depth + 1, finalFreq);
                            }
                        }
                        if (childrenAddress != 0) {
                            getWordsRec(childrenAddress, depth + 1,
                                    maxDepth, true, snr * addedWeight, inputIndex + 1,
                                    diffs + (j > 0), skipPos, nextLetters, nextLettersSize);
                        }
                    } else if (childrenAddress != 0) {
                        getWordsRec(childrenAddress, depth + 1, maxDepth,
                                false, snr * addedWeight, inputIndex + 1, diffs + (j > 0),
                                skipPos, nextLetters, nextLettersSize);
                    }
                }
                j++;
                if (skipPos >= 0) break;
            }
        }
    }
}

int
UnigramDictionary::getBigramAddress(int *pos, bool advance)
{
    int address = 0;

    address += (DICT[*pos] & 0x3F) << 16;
    address += (DICT[*pos + 1] & 0xFF) << 8;
    address += (DICT[*pos + 2] & 0xFF);

    if (advance) {
        *pos += 3;
    }

    return address;
}

int
UnigramDictionary::getBigramFreq(int *pos)
{
    int freq = DICT[(*pos)++] & FLAG_BIGRAM_FREQ;

    return freq;
}


int
UnigramDictionary::getBigrams(unsigned short *prevWord, int prevWordLength, int *codes, int codesSize,
        unsigned short *bigramChars, int *bigramFreq, int maxWordLength, int maxBigrams,
        int maxAlternatives)
{
    mBigramFreq = bigramFreq;
    mBigramChars = bigramChars;
    mInputCodes = codes;
    mInputLength = codesSize;
    mMaxBigrams = maxBigrams;

    if (HAS_BIGRAM && IS_LATEST_DICT_VERSION) {
        int pos = mParentDictionary->isValidWordRec(
                DICTIONARY_HEADER_SIZE, prevWord, 0, prevWordLength);
        LOGI("Pos -> %d\n", pos);
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

void
UnigramDictionary::searchForTerminalNode(int addressLookingFor, int frequency)
{
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

        if (depth >= 0) {
            word[depth] = (unsigned short) followingChar;
        }
        pos = followDownBranchAddress; // pos start at count
        int count = DICT[pos] & 0xFF;
        LOGI("count - %d\n",count);
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
            LOGI("ERROR!!! Cannot find bigram!!");
            break;
        }
    }
    if (checkFirstCharacter(word)) {
        addWordBigram(word, depth, frequency);
    }
}

bool
UnigramDictionary::checkFirstCharacter(unsigned short *word)
{
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

} // namespace latinime
