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

#include <assert.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>

#define LOG_TAG "LatinIME: unigram_dictionary.cpp"

#include "basechars.h"
#include "char_utils.h"
#include "dictionary.h"
#include "unigram_dictionary.h"

namespace latinime {

UnigramDictionary::UnigramDictionary(const unsigned char *dict, int typedLetterMultiplier,
        int fullWordMultiplier, int maxWordLength, int maxWords, int maxProximityChars,
        const bool isLatestDictVersion)
    : DICT(dict), MAX_WORD_LENGTH(maxWordLength),MAX_WORDS(maxWords),
    MAX_PROXIMITY_CHARS(maxProximityChars), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    TYPED_LETTER_MULTIPLIER(typedLetterMultiplier), FULL_WORD_MULTIPLIER(fullWordMultiplier),
    ROOT_POS(isLatestDictVersion ? DICTIONARY_HEADER_SIZE : 0) {
    LOGI("UnigramDictionary - constructor");
}

UnigramDictionary::~UnigramDictionary() {}

int UnigramDictionary::getSuggestions(int *codes, int codesSize, unsigned short *outWords,
        int *frequencies, int *nextLetters, int nextLettersSize)
{
    initSuggestions(codes, codesSize, outWords, frequencies);
    getSuggestionCandidates(codesSize, -1, -1, nextLetters, nextLettersSize);

    // Suggestion with missing character
    if (SUGGEST_WORDS_WITH_MISSING_CHARACTER) {
        for (int i = 0; i < codesSize; ++i) {
            if (DEBUG_DICT) LOGI("--- Suggest missing characters %d", i);
            getSuggestionCandidates(codesSize, i, -1, NULL, 0);
        }
    }

    // Suggestion with excessive character
    if (SUGGEST_WORDS_WITH_EXCESSIVE_CHARACTER) {
        for (int i = 0; i < codesSize; ++i) {
            if (DEBUG_DICT) LOGI("--- Suggest excessive characters %d", i);
            getSuggestionCandidates(codesSize, -1, i, NULL, 0);
        }
    }

    // Suggestions with missing space
    if (SUGGEST_WORDS_WITH_MISSING_SPACE_CHARACTER && mInputLength > MIN_SUGGEST_DEPTH) {
        for (int i = 1; i < codesSize; ++i) {
            if (DEBUG_DICT) LOGI("--- Suggest missing space characters %d", i);
            getMissingSpaceWords(mInputLength, i);
        }
    }

    // Get the word count
    int suggestedWordsCount = 0;
    while (suggestedWordsCount < MAX_WORDS && mFrequencies[suggestedWordsCount] > 0) {
        suggestedWordsCount++;
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
    if (DEBUG_DICT) LOGI("initSuggest");
    mFrequencies = frequencies;
    mOutputChars = outWords;
    mInputCodes = codes;
    mInputLength = codesSize;
    mMaxEditDistance = mInputLength < 5 ? 2 : mInputLength / 2;
}

void UnigramDictionary::registerNextLetter(
        unsigned short c, int *nextLetters, int nextLettersSize) {
    if (c < nextLettersSize) {
        nextLetters[c]++;
    }
}

// TODO: We need to optimize addWord by using STL or something
bool UnigramDictionary::addWord(unsigned short *word, int length, int frequency) {
    word[length] = 0;
    if (DEBUG_DICT && DEBUG_SHOW_FOUND_WORD) {
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        LOGI("Found word = %s, freq = %d", s, frequency);
    }
    if (length > MAX_WORD_LENGTH) {
        if (DEBUG_DICT) LOGI("Exceeded max word length.");
        return false;
    }

    // Find the right insertion point
    int insertAt = 0;
    while (insertAt < MAX_WORDS) {
        if (frequency > mFrequencies[insertAt] || (mFrequencies[insertAt] == frequency
                && length < Dictionary::wideStrLen(mOutputChars + insertAt * MAX_WORD_LENGTH))) {
            break;
        }
        insertAt++;
    }
    if (insertAt < MAX_WORDS) {
        if (DEBUG_DICT) {
            char s[length + 1];
            for (int i = 0; i <= length; i++) s[i] = word[i];
            LOGI("Added word = %s, freq = %d", s, frequency);
        }
        memmove((char*) mFrequencies + (insertAt + 1) * sizeof(mFrequencies[0]),
               (char*) mFrequencies + insertAt * sizeof(mFrequencies[0]),
               (MAX_WORDS - insertAt - 1) * sizeof(mFrequencies[0]));
        mFrequencies[insertAt] = frequency;
        memmove((char*) mOutputChars + (insertAt + 1) * MAX_WORD_LENGTH * sizeof(short),
               (char*) mOutputChars + insertAt * MAX_WORD_LENGTH * sizeof(short),
               (MAX_WORDS - insertAt - 1) * sizeof(short) * MAX_WORD_LENGTH);
        unsigned short *dest = mOutputChars + insertAt * MAX_WORD_LENGTH;
        while (length--) {
            *dest++ = *word++;
        }
        *dest = 0; // NULL terminate
        if (DEBUG_DICT) LOGI("Added word at %d", insertAt);
        return true;
    }
    return false;
}

unsigned short UnigramDictionary::toLowerCase(unsigned short c) {
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

bool UnigramDictionary::sameAsTyped(unsigned short *word, int length) {
    if (length != mInputLength) {
        return false;
    }
    int *inputCodes = mInputCodes;
    while (length--) {
        if ((unsigned int) *inputCodes != (unsigned int) *word) {
            return false;
        }
        inputCodes += MAX_PROXIMITY_CHARS;
        word++;
    }
    return true;
}

static const char QUOTE = '\'';
static const char SPACE = ' ';

void UnigramDictionary::getSuggestionCandidates(const int inputLength, const int skipPos,
        const int excessivePos, int *nextLetters, const int nextLettersSize) {
    if (DEBUG_DICT) LOGI("getSuggestionCandidates");
    int rootPosition = ROOT_POS;
    const int MAX_DEPTH = min(inputLength * MAX_DEPTH_MULTIPLIER, MAX_WORD_LENGTH);
    // Get the number of child of root, then increment the position
    int childCount = Dictionary::getCount(DICT, &rootPosition);
    int depth = 0;

    mStackChildCount[0] = childCount;
    mStackTraverseAll[0] = (mInputLength <= 0);
    mStackNodeFreq[0] = 1;
    mStackInputIndex[0] = 0;
    mStackDiffs[0] = 0;
    mStackSiblingPos[0] = rootPosition;

    // Depth first search
    while (depth >= 0) {
        if (mStackChildCount[depth] > 0) {
            --mStackChildCount[depth];
            bool traverseAllNodes = mStackTraverseAll[depth];
            int snr = mStackNodeFreq[depth];
            int inputIndex = mStackInputIndex[depth];
            int diffs = mStackDiffs[depth];
            int siblingPos = mStackSiblingPos[depth];
            int firstChildPos;
            // depth will never be greater than MAX_DEPTH because in that case,
            // needsToTraverseChildrenNodes should be false
            const bool needsToTraverseChildrenNodes = processCurrentNode(siblingPos, depth,
                    MAX_DEPTH, traverseAllNodes, snr, inputIndex, diffs, skipPos, excessivePos,
                    nextLetters, nextLettersSize, &childCount, &firstChildPos, &traverseAllNodes,
                    &snr, &inputIndex, &diffs, &siblingPos);
            // Update next sibling pos
            mStackSiblingPos[depth] = siblingPos;
            if (needsToTraverseChildrenNodes) {
                // Goes to child node
                ++depth;
                mStackChildCount[depth] = childCount;
                mStackTraverseAll[depth] = traverseAllNodes;
                mStackNodeFreq[depth] = snr;
                mStackInputIndex[depth] = inputIndex;
                mStackDiffs[depth] = diffs;
                mStackSiblingPos[depth] = firstChildPos;
            }
        } else {
            // Goes to parent sibling node
            --depth;
        }
    }
}

bool UnigramDictionary::getMissingSpaceWords(const int inputLength, const int missingSpacePos) {
    if (missingSpacePos <= 0 || missingSpacePos >= inputLength
            || inputLength >= MAX_WORD_LENGTH) return false;
    const int newWordLength = inputLength + 1;
    // Allocating variable length array on stack
    unsigned short word[newWordLength];
    const int firstFreq = getBestWordFreq(0, missingSpacePos, mWord);
    if (DEBUG_DICT) LOGI("First freq: %d", firstFreq);
    if (firstFreq <= 0) return false;

    for (int i = 0; i < missingSpacePos; ++i) {
        word[i] = mWord[i];
    }

    const int secondFreq = getBestWordFreq(missingSpacePos, inputLength - missingSpacePos, mWord);
    if (DEBUG_DICT) LOGI("Second freq:  %d", secondFreq);
    if (secondFreq <= 0) return false;

    word[missingSpacePos] = SPACE;
    for (int i = (missingSpacePos + 1); i < newWordLength; ++i) {
        word[i] = mWord[i - missingSpacePos - 1];
    }

    int pairFreq = ((firstFreq + secondFreq) / 2);
    for (int i = 0; i < inputLength; ++i) pairFreq *= TYPED_LETTER_MULTIPLIER;
    addWord(word, newWordLength, pairFreq);
    return true;
}

// Keep this for comparing spec to new getWords
void UnigramDictionary::getWordsOld(const int initialPos, const int inputLength, const int skipPos,
        const int excessivePos, int *nextLetters, const int nextLettersSize) {
    int initialPosition = initialPos;
    const int count = Dictionary::getCount(DICT, &initialPosition);
    getWordsRec(count, initialPosition, 0,
            min(inputLength * MAX_DEPTH_MULTIPLIER, MAX_WORD_LENGTH),
            mInputLength <= 0, 1, 0, 0, skipPos, excessivePos, nextLetters, nextLettersSize);
}

void UnigramDictionary::getWordsRec(const int childrenCount, const int pos, const int depth,
        const int maxDepth, const bool traverseAllNodes, const int snr, const int inputIndex,
        const int diffs, const int skipPos, const int excessivePos, int *nextLetters,
        const int nextLettersSize) {
    int siblingPos = pos;
    for (int i = 0; i < childrenCount; ++i) {
        int newCount;
        int newChildPosition;
        const int newDepth = depth + 1;
        bool newTraverseAllNodes;
        int newSnr;
        int newInputIndex;
        int newDiffs;
        int newSiblingPos;
        const bool needsToTraverseChildrenNodes = processCurrentNode(siblingPos, depth, maxDepth,
                traverseAllNodes, snr, inputIndex, diffs, skipPos, excessivePos, nextLetters,
                nextLettersSize,
                &newCount, &newChildPosition, &newTraverseAllNodes, &newSnr,
                &newInputIndex, &newDiffs, &newSiblingPos);
        siblingPos = newSiblingPos;

        if (needsToTraverseChildrenNodes) {
            getWordsRec(newCount, newChildPosition, newDepth, maxDepth, newTraverseAllNodes,
                    newSnr, newInputIndex, newDiffs, skipPos, excessivePos, nextLetters,
                    nextLettersSize);
        }
    }
}

inline void UnigramDictionary::onTerminalWhenUserTypedLengthIsGreaterThanInputLength(
        unsigned short *word, const int inputLength, const int depth, const int snr,
        int *nextLetters, const int nextLettersSize, const int skipPos, const int freq) {
    if (depth >= MIN_SUGGEST_DEPTH) addWord(word, depth + 1, freq * snr);
    if (depth >= inputLength && skipPos < 0) {
        registerNextLetter(mWord[mInputLength], nextLetters, nextLettersSize);
    }
}

inline void UnigramDictionary::onTerminalWhenUserTypedLengthIsSameAsInputLength(
        unsigned short *word, const int depth, const int snr, const int skipPos, const int freq,
        const int addedWeight) {
    if (!sameAsTyped(word, depth + 1)) {
        int finalFreq = freq * snr * addedWeight;
        // Proximity collection will promote a word of the same length as
        // what user typed.
        if (skipPos < 0) finalFreq *= FULL_WORD_MULTIPLIER;
        if (depth >= MIN_SUGGEST_DEPTH) addWord(word, depth + 1, finalFreq);
    }
}

inline bool UnigramDictionary::needsToSkipCurrentNode(const unsigned short c,
        const int inputIndex, const int skipPos, const int depth) {
    const unsigned short userTypedChar = (mInputCodes + (inputIndex * MAX_PROXIMITY_CHARS))[0];
    // Skip the ' or other letter and continue deeper
    return (c == QUOTE && userTypedChar != QUOTE) || skipPos == depth;
}

inline int UnigramDictionary::getMatchedProximityId(const int *currentChars,
        const unsigned short c, const int skipPos) {
    const unsigned short lowerC = toLowerCase(c);
    int j = 0;
    while (currentChars[j] > 0) {
        const bool matched = (currentChars[j] == lowerC || currentChars[j] == c);
        // If skipPos is defined, not to search proximity collections.
        // First char is what user typed.
        if (matched) {
            return j;
        } else if (skipPos >= 0) {
            return -1;
        }
        ++j;
    }
    return -1;
}

inline bool UnigramDictionary::processCurrentNode(const int pos, const int depth,
        const int maxDepth, const bool traverseAllNodes, const int snr, int inputIndex,
        const int diffs, const int skipPos, const int excessivePos, int *nextLetters,
        const int nextLettersSize, int *newCount, int *newChildPosition, bool *newTraverseAllNodes,
        int *newSnr, int*newInputIndex, int *newDiffs, int *nextSiblingPosition) {
    if (DEBUG_DICT) assert(skipPos < 0 || excessivePos < 0);
    unsigned short c;
    int childPosition;
    bool terminal;
    int freq;

    if (excessivePos == depth) ++inputIndex;

    *nextSiblingPosition = Dictionary::setDictionaryValues(DICT, IS_LATEST_DICT_VERSION, pos, &c,
            &childPosition, &terminal, &freq);

    const bool needsToTraverseChildrenNodes = childPosition != 0;

    // If we are only doing traverseAllNodes, no need to look at the typed characters.
    if (traverseAllNodes || needsToSkipCurrentNode(c, inputIndex, skipPos, depth)) {
        mWord[depth] = c;
        if (traverseAllNodes && terminal) {
            onTerminalWhenUserTypedLengthIsGreaterThanInputLength(mWord, mInputLength, depth,
                    snr, nextLetters, nextLettersSize, skipPos, freq);
        }
        if (!needsToTraverseChildrenNodes) return false;
        *newTraverseAllNodes = traverseAllNodes;
        *newSnr = snr;
        *newDiffs = diffs;
        *newInputIndex = inputIndex;
    } else {
        int *currentChars = mInputCodes + (inputIndex * MAX_PROXIMITY_CHARS);
        int matchedProximityCharId = getMatchedProximityId(currentChars, c, skipPos);
        if (matchedProximityCharId < 0) return false;
        mWord[depth] = c;
        // If inputIndex is greater than mInputLength, that means there is no
        // proximity chars. So, we don't need to check proximity.
        const int addedWeight = matchedProximityCharId == 0 ? TYPED_LETTER_MULTIPLIER : 1;
        const bool isSameAsUserTypedLength = mInputLength == inputIndex + 1;
        if (isSameAsUserTypedLength && terminal) {
            onTerminalWhenUserTypedLengthIsSameAsInputLength(mWord, depth, snr,
                    skipPos, freq, addedWeight);
        }
        if (!needsToTraverseChildrenNodes) return false;
        // Start traversing all nodes after the index exceeds the user typed length
        *newTraverseAllNodes = isSameAsUserTypedLength;
        *newSnr = snr * addedWeight;
        *newDiffs = diffs + (matchedProximityCharId > 0);
        *newInputIndex = inputIndex + 1;
    }
    // Optimization: Prune out words that are too long compared to how much was typed.
    if (depth >= maxDepth || *newDiffs > mMaxEditDistance) {
        return false;
    }

    // If inputIndex is greater than mInputLength, that means there are no proximity chars.
    if (mInputLength <= *newInputIndex) {
        *newTraverseAllNodes = true;
    }
    // get the count of nodes and increment childAddress.
    *newCount = Dictionary::getCount(DICT, &childPosition);
    *newChildPosition = childPosition;
    if (DEBUG_DICT) assert(needsToTraverseChildrenNodes);
    return needsToTraverseChildrenNodes;
}

inline int UnigramDictionary::getBestWordFreq(const int startInputIndex, const int inputLength,
        unsigned short *word) {
    int pos = ROOT_POS;
    int count = Dictionary::getCount(DICT, &pos);
    int maxFreq = 0;
    int depth = 0;
    unsigned short newWord[MAX_WORD_LENGTH_INTERNAL];
    bool terminal = false;

    mStackChildCount[0] = count;
    mStackSiblingPos[0] = pos;

    while (depth >= 0) {
        if (mStackChildCount[depth] > 0) {
            --mStackChildCount[depth];
            int firstChildPos;
            int newFreq;
            int siblingPos = mStackSiblingPos[depth];
            const bool needsToTraverseChildrenNodes = processCurrentNodeForExactMatch(siblingPos,
                    startInputIndex, depth, newWord, &firstChildPos, &count, &terminal, &newFreq,
                    &siblingPos);
            mStackSiblingPos[depth] = siblingPos;
            if (depth == (inputLength - 1)) {
                // Traverse sibling node
                if (terminal) {
                    if (newFreq > maxFreq) {
                        for (int i = 0; i < inputLength; ++i) word[i] = newWord[i];
                        if (DEBUG_DICT && DEBUG_NODE) {
                            char s[inputLength + 1];
                            for (int i = 0; i < inputLength; ++i) s[i] = word[i];
                            s[inputLength] = 0;
                            LOGI("New missing space word found: %d > %d (%s), %d, %d",
                                    newFreq, maxFreq, s, inputLength, depth);
                        }
                        maxFreq = newFreq;
                    }
                }
            } else if (needsToTraverseChildrenNodes) {
                // Traverse children nodes
                ++depth;
                mStackChildCount[depth] = count;
                mStackSiblingPos[depth] = firstChildPos;
            }
        } else {
            // Traverse parent node
            --depth;
        }
    }

    word[inputLength] = 0;
    return maxFreq;
}

inline bool UnigramDictionary::processCurrentNodeForExactMatch(const int firstChildPos,
        const int startInputIndex, const int depth, unsigned short *word, int *newChildPosition,
        int *newCount, bool *newTerminal, int *newFreq, int *siblingPos) {
    const int inputIndex = startInputIndex + depth;
    const int *currentChars = mInputCodes + (inputIndex * MAX_PROXIMITY_CHARS);
    unsigned short c;
    *siblingPos = Dictionary::setDictionaryValues(DICT, IS_LATEST_DICT_VERSION, firstChildPos, &c,
            newChildPosition, newTerminal, newFreq);
    const unsigned int inputC = currentChars[0];
    if (DEBUG_DICT) assert(inputC <= U_SHORT_MAX);
    const unsigned short lowerC = toLowerCase(c);
    const bool matched = (inputC == lowerC || inputC == c);
    const bool hasChild = *newChildPosition != 0;
    if (matched) {
        word[depth] = c;
        if (DEBUG_DICT && DEBUG_NODE) {
            LOGI("Node(%c, %c)<%d>, %d, %d", inputC, c, matched, hasChild, *newFreq);
            if (*newTerminal) LOGI("Terminal %d", *newFreq);
        }
        if (hasChild) {
            *newCount = Dictionary::getCount(DICT, newChildPosition);
            return true;
        } else {
            return false;
        }
    } else {
        // If this node is not user typed character, this method treats this word as unmatched.
        // Thus newTerminal shouldn't be true.
        *newTerminal = false;
        return false;
    }
}
} // namespace latinime
