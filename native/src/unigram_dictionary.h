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
#include "proximity_info.h"

namespace latinime {

class UnigramDictionary {

    typedef enum {                             // Used as a return value for character comparison
        SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR,  // Same char, possibly with different case or accent
        NEAR_PROXIMITY_CHAR,                   // It is a char located nearby on the keyboard
        UNRELATED_CHAR                         // It is an unrelated char
    } ProximityType;

public:
    UnigramDictionary(const unsigned char *dict, int typedLetterMultipler, int fullWordMultiplier,
            int maxWordLength, int maxWords, int maxProximityChars, const bool isLatestDictVersion);
    int getSuggestions(const ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize, const int flags,
            unsigned short *outWords, int *frequencies);
    ~UnigramDictionary();

private:
    void getWordSuggestions(const ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize,
            unsigned short *outWords, int *frequencies);
    bool isDigraph(const int* codes, const int i, const int codesSize) const;
    void getWordWithDigraphSuggestionsRec(const ProximityInfo *proximityInfo,
        const int *xcoordinates, const int* ycoordinates, const int *codesBuffer,
        const int codesBufferSize, const int flags, const int* codesSrc, const int codesRemain,
        const int currentDepth, int* codesDest, unsigned short* outWords, int* frequencies);
    void initSuggestions(const int *codes, const int codesSize, unsigned short *outWords,
            int *frequencies);
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
    unsigned short toBaseLowerCase(unsigned short c);
    void getWordsRec(const int childrenCount, const int pos, const int depth, const int maxDepth,
            const bool traverseAllNodes, const int snr, const int inputIndex, const int diffs,
            const int skipPos, const int excessivePos, const int transposedPos, int *nextLetters,
            const int nextLettersSize);
    bool getSplitTwoWordsSuggestion(const int inputLength,
            const int firstWordStartPos, const int firstWordLength,
            const int secondWordStartPos, const int secondWordLength);
    bool getMissingSpaceWords(const int inputLength, const int missingSpacePos);
    bool getMistypedSpaceWords(const int inputLength, const int spaceProximityPos);
    // Keep getWordsOld for comparing performance between getWords and getWordsOld
    void getWordsOld(const int initialPos, const int inputLength, const int skipPos,
            const int excessivePos, const int transposedPos, int *nextLetters,
            const int nextLettersSize);
    void registerNextLetter(unsigned short c, int *nextLetters, int nextLettersSize);
    int calculateFinalFreq(const int inputIndex, const int depth, const int snr, const int skipPos,
            const int excessivePos, const int transposedPos, const int freq,
            const bool sameLength) const;
    void onTerminalWhenUserTypedLengthIsGreaterThanInputLength(unsigned short *word,
            const int inputIndex, const int depth, const int snr, int *nextLetters,
            const int nextLettersSize, const int skipPos, const int excessivePos,
            const int transposedPos, const int freq);
    void onTerminalWhenUserTypedLengthIsSameAsInputLength(unsigned short *word,
            const int inputIndex, const int depth, const int snr, const int skipPos,
            const int excessivePos, const int transposedPos, const int freq);
    bool needsToSkipCurrentNode(const unsigned short c,
            const int inputIndex, const int skipPos, const int depth);
    ProximityType getMatchedProximityId(const int *currentChars, const unsigned short c,
            const int skipPos, const int excessivePos, const int transposedPos);
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
    bool existsAdjacentProximityChars(const int inputIndex, const int inputLength) const;
    inline const int* getInputCharsAt(const int index) const {
        return mInputCodes + (index * MAX_PROXIMITY_CHARS);
    }
    const unsigned char *DICT;
    const int MAX_WORD_LENGTH;
    const int MAX_WORDS;
    const int MAX_PROXIMITY_CHARS;
    const bool IS_LATEST_DICT_VERSION;
    const int TYPED_LETTER_MULTIPLIER;
    const int FULL_WORD_MULTIPLIER;
    const int ROOT_POS;
    const unsigned int BYTES_IN_ONE_CHAR;
    const int MAX_UMLAUT_SEARCH_DEPTH;

    // Flags for special processing
    // Those *must* match the flags in BinaryDictionary.Flags.ALL_FLAGS in BinaryDictionary.java
    // or something very bad (like, the apocalypse) will happen.
    // Please update both at the same time.
    enum {
        REQUIRES_GERMAN_UMLAUT_PROCESSING = 0x1
    };
    static const struct digraph_t { int first; int second; } GERMAN_UMLAUT_DIGRAPHS[];

    int *mFrequencies;
    unsigned short *mOutputChars;
    const int *mInputCodes;
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
    int mNextLettersFrequency[NEXT_LETTERS_SIZE];
};

// ----------------------------------------------------------------------------

}; // namespace latinime

#endif // LATINIME_UNIGRAM_DICTIONARY_H
