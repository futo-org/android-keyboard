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

#include <map>
#include <stdint.h>
#include "correction.h"
#include "correction_state.h"
#include "defines.h"
#include "proximity_info.h"
#include "words_priority_queue.h"
#include "words_priority_queue_pool.h"

namespace latinime {

class TerminalAttributes;
class UnigramDictionary {
    typedef struct { int first; int second; int replacement; } digraph_t;

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

    // Flag for shortcut targets presence
    static const int FLAG_HAS_SHORTCUT_TARGETS = 0x08;
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

    // Error tolerances
    static const int DEFAULT_MAX_ERRORS = 2;
    static const int MAX_ERRORS_FOR_TWO_WORDS = 1;

    static const int FLAG_MULTIPLE_SUGGEST_ABORT = 0;
    static const int FLAG_MULTIPLE_SUGGEST_SKIP = 1;
    static const int FLAG_MULTIPLE_SUGGEST_CONTINUE = 2;
    UnigramDictionary(const uint8_t* const streamStart, int typedLetterMultipler,
            int fullWordMultiplier, int maxWordLength, int maxWords, const unsigned int flags);
    int getFrequency(const int32_t* const inWord, const int length) const;
    int getBigramPosition(int pos, unsigned short *word, int offset, int length) const;
    int getSuggestions(ProximityInfo *proximityInfo, WordsPriorityQueuePool *queuePool,
            Correction *correction, const int *xcoordinates, const int *ycoordinates,
            const int *codes, const int codesSize, const std::map<int, int> *bigramMap,
            const uint8_t *bigramFilter, const bool useFullEditDistance, unsigned short *outWords,
            int *frequencies);
    virtual ~UnigramDictionary();

 private:
    void getWordSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int inputLength,
            const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
            const bool useFullEditDistance, Correction *correction,
            WordsPriorityQueuePool *queuePool);
    int getDigraphReplacement(const int *codes, const int i, const int codesSize,
            const digraph_t* const digraphs, const unsigned int digraphsSize) const;
    void getWordWithDigraphSuggestionsRec(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int* ycoordinates, const int *codesBuffer,
        int *xCoordinatesBuffer, int *yCoordinatesBuffer, const int codesBufferSize,
        const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        const bool useFullEditDistance, const int* codesSrc, const int codesRemain,
        const int currentDepth, int* codesDest, Correction *correction,
        WordsPriorityQueuePool* queuePool, const digraph_t* const digraphs,
        const unsigned int digraphsSize);
    void initSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize, Correction *correction);
    void getOneWordSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const std::map<int, int> *bigramMap,
            const uint8_t *bigramFilter, const bool useFullEditDistance, const int inputLength,
            Correction *correction, WordsPriorityQueuePool* queuePool);
    void getSuggestionCandidates(
            const bool useFullEditDistance, const int inputLength,
            const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
            Correction *correction, WordsPriorityQueuePool* queuePool, const bool doAutoCompletion,
            const int maxErrors, const int currentWordIndex);
    void getSplitMultipleWordsSuggestions(ProximityInfo *proximityInfo,
            const int *xcoordinates, const int *ycoordinates, const int *codes,
            const bool useFullEditDistance, const int inputLength,
            Correction *correction, WordsPriorityQueuePool* queuePool,
            const bool hasAutoCorrectionCandidate);
    void onTerminal(const int freq, const TerminalAttributes& terminalAttributes,
            Correction *correction, WordsPriorityQueuePool *queuePool, const bool addToMasterQueue,
            const int currentWordIndex);
    bool needsToSkipCurrentNode(const unsigned short c,
            const int inputIndex, const int skipPos, const int depth);
    // Process a node by considering proximity, missing and excessive character
    bool processCurrentNode(const int initialPos, const std::map<int, int> *bigramMap,
            const uint8_t *bigramFilter, Correction *correction, int *newCount,
            int *newChildPosition, int *nextSiblingPosition, WordsPriorityQueuePool *queuePool,
            const int currentWordIndex);
    int getMostFrequentWordLike(const int startInputIndex, const int inputLength,
            ProximityInfo *proximityInfo, unsigned short *word);
    int getMostFrequentWordLikeInner(const uint16_t* const inWord, const int length,
            short unsigned int *outWord);
    int getSubStringSuggestion(
            ProximityInfo *proximityInfo, const int *xcoordinates, const int *ycoordinates,
            const int *codes, const bool useFullEditDistance, Correction *correction,
            WordsPriorityQueuePool* queuePool, const int inputLength,
            const bool hasAutoCorrectionCandidate, const int currentWordIndex,
            const int inputWordStartPos, const int inputWordLength,
            const int outputWordStartPos, const bool isSpaceProximity, int *freqArray,
            int *wordLengthArray, unsigned short* outputWord, int *outputWordLength);
    void getMultiWordsSuggestionRec(ProximityInfo *proximityInfo,
            const int *xcoordinates, const int *ycoordinates, const int *codes,
            const bool useFullEditDistance, const int inputLength,
            Correction *correction, WordsPriorityQueuePool* queuePool,
            const bool hasAutoCorrectionCandidate, const int startPos, const int startWordIndex,
            const int outputWordLength, int *freqArray, int* wordLengthArray,
            unsigned short* outputWord);

    const uint8_t* const DICT_ROOT;
    const int MAX_WORD_LENGTH;
    const int MAX_WORDS;
    const int TYPED_LETTER_MULTIPLIER;
    const int FULL_WORD_MULTIPLIER;
    const int ROOT_POS;
    const unsigned int BYTES_IN_ONE_CHAR;
    const int MAX_DIGRAPH_SEARCH_DEPTH;
    const int FLAGS;

    static const digraph_t GERMAN_UMLAUT_DIGRAPHS[];
    static const digraph_t FRENCH_LIGATURES_DIGRAPHS[];

    // Still bundled members
    unsigned short mWord[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
    int mStackChildCount[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
    int mStackInputIndex[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
    int mStackSiblingPos[MAX_WORD_LENGTH_INTERNAL];// TODO: remove
};
} // namespace latinime

#endif // LATINIME_UNIGRAM_DICTIONARY_H
