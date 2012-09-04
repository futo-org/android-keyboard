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
#include "defines.h"

namespace latinime {

class Correction;
class ProximityInfo;
class TerminalAttributes;
class WordsPriorityQueuePool;

class UnigramDictionary {
    typedef struct { int first; int second; int replacement; } digraph_t;

 public:
    // Error tolerances
    static const int DEFAULT_MAX_ERRORS = 2;
    static const int MAX_ERRORS_FOR_TWO_WORDS = 1;

    static const int FLAG_MULTIPLE_SUGGEST_ABORT = 0;
    static const int FLAG_MULTIPLE_SUGGEST_SKIP = 1;
    static const int FLAG_MULTIPLE_SUGGEST_CONTINUE = 2;
    UnigramDictionary(const uint8_t *const streamStart, int typedLetterMultipler,
            int fullWordMultiplier, int maxWordLength, int maxWords, const unsigned int flags);
    int getFrequency(const int32_t *const inWord, const int length) const;
    int getBigramPosition(int pos, unsigned short *word, int offset, int length) const;
    int getSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize,
            const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
            const bool useFullEditDistance, unsigned short *outWords, int *frequencies,
            int *outputTypes) const;
    virtual ~UnigramDictionary();

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(UnigramDictionary);
    void getWordSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int inputSize,
            const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
            const bool useFullEditDistance, Correction *correction,
            WordsPriorityQueuePool *queuePool) const;
    int getDigraphReplacement(const int *codes, const int i, const int codesSize,
            const digraph_t *const digraphs, const unsigned int digraphsSize) const;
    void getWordWithDigraphSuggestionsRec(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codesBuffer,
        int *xCoordinatesBuffer, int *yCoordinatesBuffer, const int codesBufferSize,
        const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        const bool useFullEditDistance, const int *codesSrc, const int codesRemain,
        const int currentDepth, int *codesDest, Correction *correction,
        WordsPriorityQueuePool *queuePool, const digraph_t *const digraphs,
        const unsigned int digraphsSize) const;
    void initSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const int codesSize,
            Correction *correction) const;
    void getOneWordSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
            const int *ycoordinates, const int *codes, const std::map<int, int> *bigramMap,
            const uint8_t *bigramFilter, const bool useFullEditDistance, const int inputSize,
            Correction *correction, WordsPriorityQueuePool *queuePool) const;
    void getSuggestionCandidates(
            const bool useFullEditDistance, const int inputSize,
            const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
            Correction *correction, WordsPriorityQueuePool *queuePool, const bool doAutoCompletion,
            const int maxErrors, const int currentWordIndex) const;
    void getSplitMultipleWordsSuggestions(ProximityInfo *proximityInfo,
            const int *xcoordinates, const int *ycoordinates, const int *codes,
            const bool useFullEditDistance, const int inputSize,
            Correction *correction, WordsPriorityQueuePool *queuePool,
            const bool hasAutoCorrectionCandidate) const;
    void onTerminal(const int freq, const TerminalAttributes& terminalAttributes,
            Correction *correction, WordsPriorityQueuePool *queuePool, const bool addToMasterQueue,
            const int currentWordIndex) const;
    // Process a node by considering proximity, missing and excessive character
    bool processCurrentNode(const int initialPos, const std::map<int, int> *bigramMap,
            const uint8_t *bigramFilter, Correction *correction, int *newCount,
            int *newChildPosition, int *nextSiblingPosition, WordsPriorityQueuePool *queuePool,
            const int currentWordIndex) const;
    int getMostFrequentWordLike(const int startInputIndex, const int inputSize,
            Correction *correction, unsigned short *word) const;
    int getMostFrequentWordLikeInner(const uint16_t *const inWord, const int inputSize,
            short unsigned int *outWord) const;
    int getSubStringSuggestion(
            ProximityInfo *proximityInfo, const int *xcoordinates, const int *ycoordinates,
            const int *codes, const bool useFullEditDistance, Correction *correction,
            WordsPriorityQueuePool *queuePool, const int inputSize,
            const bool hasAutoCorrectionCandidate, const int currentWordIndex,
            const int inputWordStartPos, const int inputWordLength,
            const int outputWordStartPos, const bool isSpaceProximity, int *freqArray,
            int *wordLengthArray, unsigned short *outputWord, int *outputWordLength) const;
    void getMultiWordsSuggestionRec(ProximityInfo *proximityInfo,
            const int *xcoordinates, const int *ycoordinates, const int *codes,
            const bool useFullEditDistance, const int inputSize,
            Correction *correction, WordsPriorityQueuePool *queuePool,
            const bool hasAutoCorrectionCandidate, const int startPos, const int startWordIndex,
            const int outputWordLength, int *freqArray, int *wordLengthArray,
            unsigned short *outputWord) const;

    const uint8_t *const DICT_ROOT;
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
};
} // namespace latinime
#endif // LATINIME_UNIGRAM_DICTIONARY_H
