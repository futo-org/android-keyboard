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
        const bool isLatestDictVersion)
    : DICT(dict), MAX_WORD_LENGTH(maxWordLength),MAX_WORDS(maxWords),
    MAX_ALTERNATIVES(maxAlternatives), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    TYPED_LETTER_MULTIPLIER(typedLetterMultiplier), FULL_WORD_MULTIPLIER(fullWordMultiplier) {
    LOGI("UnigramDictionary - constructor");
}

UnigramDictionary::~UnigramDictionary() {}

int UnigramDictionary::getSuggestions(int *codes, int codesSize, unsigned short *outWords,
        int *frequencies, int *nextLetters, int nextLettersSize)
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
                     && length < Dictionary::wideStrLen(mOutputChars + insertAt * MAX_WORD_LENGTH))) {
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
UnigramDictionary::getWordsRec(int pos, int depth, int maxDepth, bool completion, int snr,
        int inputIndex, int diffs, int skipPos, int *nextLetters, int nextLettersSize)
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
                    int addedWeight = j == 0 ? TYPED_LETTER_MULTIPLIER : 1;
                    mWord[depth] = c;
                    if (mInputLength == inputIndex + 1) {
                        if (terminal) {
                            if (//INCLUDE_TYPED_WORD_IF_VALID ||
                                !sameAsTyped(mWord, depth + 1)) {
                                int finalFreq = freq * snr * addedWeight;
                                if (skipPos < 0) finalFreq *= FULL_WORD_MULTIPLIER;
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

} // namespace latinime
