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

#include <stdint.h>
#include "correction.h"
#include "correction_state.h"
#include "defines.h"
#include "proximity_info.h"

#ifndef NULL
#define NULL 0
#endif

namespace latinime {

class UnigramDictionary {

public:

    // Mask and flags for children address type selection.
    static const int MASK_GROUP_ADDRESS_TYPE = 0xC0;
    static const int FLAG_GROUP_ADDRESS_TYPE_NOADDRESS = 0x00;
    static const int FLAG_GROUP_ADDRESS_TYPE_ONEBYTE = 0x40;
    static const int FLAG_GROUP_ADDRESS_TYPE_TWOBYTES = 0x80;
    static const int FLAG_GROUP_ADDRESS_TYPE_THREEBYTES = 0xC0;

    // Flag for single/multiple char group
    static const int FLAG_HAS_MULTIPLE_CHARS = 0x20;

    // Flag for terminal groups
    static const int FLAG_IS_TERMINAL = 0x10;

    // Flag for bigram presence
    static const int FLAG_HAS_BIGRAMS = 0x04;

    // Attribute (bigram/shortcut) related flags:
    // Flag for presence of more attributes
    static const int FLAG_ATTRIBUTE_HAS_NEXT = 0x80;
    // Flag for sign of offset. If this flag is set, the offset value must be negated.
    static const int FLAG_ATTRIBUTE_OFFSET_NEGATIVE = 0x40;

    // Mask for attribute frequency, stored on 4 bits inside the flags byte.
    static const int MASK_ATTRIBUTE_FREQUENCY = 0x0F;

    // Mask and flags for attribute address type selection.
    static const int MASK_ATTRIBUTE_ADDRESS_TYPE = 0x30;
    static const int FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE = 0x10;
    static const int FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES = 0x20;
    static const int FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES = 0x30;

    UnigramDictionary(const uint8_t* const streamStart, int typedLetterMultipler,
            int fullWordMultiplier, int maxWordLength, int maxWords, int maxProximityChars,
            const bool isLatestDictVersion);
    bool isValidWord(const uint16_t* const inWord, const int length) const;
    int getBigramPosition(int pos, unsigned short *word, int offset, int length) const;
    int getSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize, const int flags,
            unsigned short *outWords, int *frequencies);
    virtual ~UnigramDictionary();

private:

    void getWordSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize,
            unsigned short *outWords, int *frequencies, const int flags);
    bool isDigraph(const int* codes, const int i, const int codesSize) const;
    void getWordWithDigraphSuggestionsRec(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int* ycoordinates, const int *codesBuffer,
        const int codesBufferSize, const int flags, const int* codesSrc, const int codesRemain,
        const int currentDepth, int* codesDest, unsigned short* outWords, int* frequencies);
    void initSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize,
            unsigned short *outWords, int *frequencies);
    void getSuggestionCandidates(const bool useFullEditDistance);
    bool addWord(unsigned short *word, int length, int frequency);
    void getSplitTwoWordsSuggestion(const int inputLength, Correction *correction);
    void getMissingSpaceWords(const int inputLength, const int missingSpacePos,
            Correction *correction, const bool useFullEditDistance);
    void getMistypedSpaceWords(const int inputLength, const int spaceProximityPos,
            Correction *correction, const bool useFullEditDistance);
    void onTerminal(const int freq, Correction *correction);
    bool needsToSkipCurrentNode(const unsigned short c,
            const int inputIndex, const int skipPos, const int depth);
    // Process a node by considering proximity, missing and excessive character
    bool processCurrentNode(const int initialPos,
            Correction *correction, int *newCount,
            int *newChildPosition, int *nextSiblingPosition);
    int getMostFrequentWordLike(const int startInputIndex, const int inputLength,
            unsigned short *word);
    int getMostFrequentWordLikeInner(const uint16_t* const inWord, const int length,
            short unsigned int* outWord);

    const uint8_t* const DICT_ROOT;
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
        REQUIRES_GERMAN_UMLAUT_PROCESSING = 0x1,
        USE_FULL_EDIT_DISTANCE = 0x2
    };
    static const struct digraph_t { int first; int second; } GERMAN_UMLAUT_DIGRAPHS[];

    int *mFrequencies;
    unsigned short *mOutputChars;
    ProximityInfo *mProximityInfo;
    Correction *mCorrection;
    int mInputLength;
    // MAX_WORD_LENGTH_INTERNAL must be bigger than MAX_WORD_LENGTH
    unsigned short mWord[MAX_WORD_LENGTH_INTERNAL];

    int mStackChildCount[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
    int mStackInputIndex[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
    int mStackSiblingPos[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
};
} // namespace latinime

#endif // LATINIME_UNIGRAM_DICTIONARY_H
