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

#ifndef LATINIME_UNIGRAM_DICTIONARY_H
#define LATINIME_UNIGRAM_DICTIONARY_H

#include "defines.h"

namespace latinime {

class UnigramDictionary {
public:
    UnigramDictionary(const unsigned char *dict, int typedLetterMultipler, int fullWordMultiplier,
            int maxWordLength, int maxWords, int maxProximityChars, const bool isLatestDictVersion);
    int getSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies,
            int *nextLetters, int nextLettersSize);
    ~UnigramDictionary();

private:
    void initSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies);
    void getSuggestionCandidates(const int skipPos, const int excessivePos,
            const int transposedPos, int *nextLetters, const int nextLettersSize,
            const int maxDepth);
    void getVersionNumber();
    bool checkIfDictVersionIsLatest();
    int getAddress(int *pos);
    int getFreq(int *pos);
    int wideStrLen(unsigned short *str);
    bool sameAsTyped(unsigned short *word, int length);
    bool addWord(unsigned short *word, int length, int frequency);
    unsigned short toLowerCase(unsigned short c);
    void getWordsRec(const int childrenCount, const int pos, const int depth, const int maxDepth,
            const bool traverseAllNodes, const int snr, const int inputIndex, const int diffs,
            const int skipPos, const int excessivePos, const int transposedPos, int *nextLetters,
            const int nextLettersSize);
    bool getMissingSpaceWords(const int inputLength, const int missingSpacePos);
    // Keep getWordsOld for comparing performance between getWords and getWordsOld
    void getWordsOld(const int initialPos, const int inputLength, const int skipPos,
            const int excessivePos, const int transposedPos, int *nextLetters,
            const int nextLettersSize);
    void registerNextLetter(unsigned short c, int *nextLetters, int nextLettersSize);
    int calculateFinalFreq(const int inputIndex, const int snr, const int skipPos,
            const int excessivePos, const int transposedPos, const int freq, const bool sameLength);
    void onTerminalWhenUserTypedLengthIsGreaterThanInputLength(unsigned short *word,
            const int inputIndex, const int depth, const int snr, int *nextLetters,
            const int nextLettersSize, const int skipPos, const int excessivePos,
            const int transposedPos, const int freq);
    void onTerminalWhenUserTypedLengthIsSameAsInputLength(unsigned short *word,
            const int inputIndex, const int depth, const int snr, const int skipPos,
            const int excessivePos, const int transposedPos, const int freq, const int addedWeight);
    bool needsToSkipCurrentNode(const unsigned short c,
            const int inputIndex, const int skipPos, const int depth);
    int getMatchedProximityId(const int *currentChars, const unsigned short c, const int skipPos,
            const int excessivePos, const int transposedPos);
    // Process a node by considering proximity, missing and excessive character
    bool processCurrentNode(const int pos, const int depth,
            const int maxDepth, const bool traverseAllNodes, const int snr, int inputIndex,
            const int diffs, const int skipPos, const int excessivePos, const int transposedPos,
            int *nextLetters, const int nextLettersSize, int *newCount, int *newChildPosition,
            bool *newTraverseAllNodes, int *newSnr, int*newInputIndex, int *newDiffs,
            int *nextSiblingPosition);
    int getBestWordFreq(const int startInputIndex, const int inputLength, unsigned short *word);
    // Process a node by considering missing space
    bool processCurrentNodeForExactMatch(const int firstChildPos,
            const int startInputIndex, const int depth, unsigned short *word,
            int *newChildPosition, int *newCount, bool *newTerminal, int *newFreq, int *siblingPos);
    bool existsAdjacentProximityChars(const int inputIndex, const int inputLength);
    int* getInputCharsAt(const int index) {return mInputCodes + (index * MAX_PROXIMITY_CHARS);}
    const unsigned char *DICT;
    const int MAX_WORD_LENGTH;
    const int MAX_WORDS;
    const int MAX_PROXIMITY_CHARS;
    const bool IS_LATEST_DICT_VERSION;
    const int TYPED_LETTER_MULTIPLIER;
    const int FULL_WORD_MULTIPLIER;
    const int ROOT_POS;

    int *mFrequencies;
    unsigned short *mOutputChars;
    int *mInputCodes;
    int mInputLength;
    // MAX_WORD_LENGTH_INTERNAL must be bigger than MAX_WORD_LENGTH
    unsigned short mWord[MAX_WORD_LENGTH_INTERNAL];
    int mMaxEditDistance;

    int mStackChildCount[MAX_WORD_LENGTH_INTERNAL];
    bool mStackTraverseAll[MAX_WORD_LENGTH_INTERNAL];
    int mStackNodeFreq[MAX_WORD_LENGTH_INTERNAL];
    int mStackInputIndex[MAX_WORD_LENGTH_INTERNAL];
    int mStackDiffs[MAX_WORD_LENGTH_INTERNAL];
    int mStackSiblingPos[MAX_WORD_LENGTH_INTERNAL];
};

// ----------------------------------------------------------------------------

}; // namespace latinime

#endif // LATINIME_UNIGRAM_DICTIONARY_H
