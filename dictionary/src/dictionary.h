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

namespace latinime {

// 22-bit address = ~4MB dictionary size limit, which on average would be about 200k-300k words
#define ADDRESS_MASK 0x3FFFFF

// The bit that decides if an address follows in the next 22 bits
#define FLAG_ADDRESS_MASK 0x40
// The bit that decides if this is a terminal node for a word. The node could still have children,
// if the word has other endings.
#define FLAG_TERMINAL_MASK 0x80

class Dictionary {
public:
    Dictionary(void *dict, int typedLetterMultipler, int fullWordMultiplier);
    int getSuggestions(int *codes, int codesSize, unsigned short *outWords, int *frequencies,
        int maxWordLength, int maxWords, int maxAlternatives, int skipPos);
    bool isValidWord(unsigned short *word, int length);
    void setAsset(void *asset) { mAsset = asset; }
    void *getAsset() { return mAsset; }
    ~Dictionary();

private:

    int getAddress(int *pos);
    bool getTerminal(int *pos) { return (mDict[*pos] & FLAG_TERMINAL_MASK) > 0; }
    int getFreq(int *pos) { return mDict[(*pos)++] & 0xFF; }
    int getCount(int *pos) { return mDict[(*pos)++] & 0xFF; }
    unsigned short getChar(int *pos);
    int wideStrLen(unsigned short *str);

    bool sameAsTyped(unsigned short *word, int length);
    bool addWord(unsigned short *word, int length, int frequency);
    unsigned short toLowerCase(unsigned short c);
    void getWordsRec(int pos, int depth, int maxDepth, bool completion, int frequency,
            int inputIndex, int diffs);
    bool isValidWordRec(int pos, unsigned short *word, int offset, int length);

    unsigned char *mDict;
    void *mAsset;

    int *mFrequencies;
    int mMaxWords;
    int mMaxWordLength;
    int mWords;
    unsigned short *mOutputChars;
    int *mInputCodes;
    int mInputLength;
    int mMaxAlternatives;
    unsigned short mWord[128];
    int mSkipPos;
    int mMaxEditDistance;

    int mFullWordMultiplier;
    int mTypedLetterMultiplier;
};

// ----------------------------------------------------------------------------

}; // namespace latinime

#endif // LATINIME_DICTIONARY_H
