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
            int maxWordLength, int maxWords, int maxAlternatives,  const bool isLatestDictVersion,
            const bool hasBigram, Dictionary *parentDictionary);
    int getSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies,
            int *nextLetters, int nextLettersSize);
    int getBigrams(unsigned short *word, int length, int *codes, int codesSize,
            unsigned short *outWords, int *frequencies, int maxWordLength, int maxBigrams,
            int maxAlternatives);
    ~UnigramDictionary();

private:
    void initSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies);
    int getSuggestionCandidates(int inputLength, int skipPos, int *nextLetters, int nextLettersSize);
    void getVersionNumber();
    bool checkIfDictVersionIsLatest();
    int getAddress(int *pos);
    int getBigramAddress(int *pos, bool advance);
    int getFreq(int *pos);
    int getBigramFreq(int *pos);
    void searchForTerminalNode(int address, int frequency);

    bool getFirstBitOfByte(int *pos) { return (DICT[*pos] & 0x80) > 0; }
    bool getSecondBitOfByte(int *pos) { return (DICT[*pos] & 0x40) > 0; }
    bool getTerminal(int *pos) { return (DICT[*pos] & FLAG_TERMINAL_MASK) > 0; }
    int wideStrLen(unsigned short *str);

    bool sameAsTyped(unsigned short *word, int length);
    bool checkFirstCharacter(unsigned short *word);
    bool addWord(unsigned short *word, int length, int frequency);
    bool addWordBigram(unsigned short *word, int length, int frequency);
    unsigned short toLowerCase(unsigned short c);
    void getWordsRec(int pos, int depth, int maxDepth, bool completion, int frequency,
            int inputIndex, int diffs, int skipPos, int *nextLetters, int nextLettersSize);
    void registerNextLetter(unsigned short c, int *nextLetters, int nextLettersSize);

    const unsigned char *DICT;

    const int MAX_WORDS;
    const int MAX_WORD_LENGTH;
    const int MAX_ALTERNATIVES;
    const bool IS_LATEST_DICT_VERSION;
    const bool HAS_BIGRAM;

    Dictionary *mParentDictionary;
    int *mFrequencies;
    int *mBigramFreq;
    int mMaxBigrams;
    unsigned short *mOutputChars;
    unsigned short *mBigramChars;
    int *mInputCodes;
    int mInputLength;
    unsigned short mWord[128];
    int mMaxEditDistance;

    int mFullWordMultiplier;
    int mTypedLetterMultiplier;
    int mVersion;
    int mBigram;
};

// ----------------------------------------------------------------------------

}; // namespace latinime

#endif // LATINIME_UNIGRAM_DICTIONARY_H
