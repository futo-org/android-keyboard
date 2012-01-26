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
#include <string.h>

#define LOG_TAG "LatinIME: unigram_dictionary.cpp"

#include "char_utils.h"
#include "dictionary.h"
#include "unigram_dictionary.h"

#include "binary_format.h"
#include "terminal_attributes.h"

namespace latinime {

const UnigramDictionary::digraph_t UnigramDictionary::GERMAN_UMLAUT_DIGRAPHS[] =
        { { 'a', 'e' },
        { 'o', 'e' },
        { 'u', 'e' } };

// TODO: check the header
UnigramDictionary::UnigramDictionary(const uint8_t* const streamStart, int typedLetterMultiplier,
        int fullWordMultiplier, int maxWordLength, int maxWords, int maxProximityChars,
        const bool isLatestDictVersion)
    : DICT_ROOT(streamStart + NEW_DICTIONARY_HEADER_SIZE),
    MAX_WORD_LENGTH(maxWordLength), MAX_WORDS(maxWords),
    MAX_PROXIMITY_CHARS(maxProximityChars), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    TYPED_LETTER_MULTIPLIER(typedLetterMultiplier), FULL_WORD_MULTIPLIER(fullWordMultiplier),
      // TODO : remove this variable.
    ROOT_POS(0),
    BYTES_IN_ONE_CHAR(MAX_PROXIMITY_CHARS * sizeof(int)),
    MAX_UMLAUT_SEARCH_DEPTH(DEFAULT_MAX_UMLAUT_SEARCH_DEPTH) {
    if (DEBUG_DICT) {
        AKLOGI("UnigramDictionary - constructor");
    }
}

UnigramDictionary::~UnigramDictionary() {
}

static inline unsigned int getCodesBufferSize(const int *codes, const int codesSize,
        const int MAX_PROXIMITY_CHARS) {
    return sizeof(*codes) * MAX_PROXIMITY_CHARS * codesSize;
}

// TODO: This needs to take an const unsigned short* and not tinker with its contents
static inline void addWord(
        unsigned short *word, int length, int frequency, WordsPriorityQueue *queue) {
    queue->push(frequency, word, length);
}

bool UnigramDictionary::isDigraph(const int *codes, const int i, const int codesSize) const {

    // There can't be a digraph if we don't have at least 2 characters to examine
    if (i + 2 > codesSize) return false;

    // Search for the first char of some digraph
    int lastDigraphIndex = -1;
    const int thisChar = codes[i * MAX_PROXIMITY_CHARS];
    for (lastDigraphIndex = sizeof(GERMAN_UMLAUT_DIGRAPHS) / sizeof(GERMAN_UMLAUT_DIGRAPHS[0]) - 1;
            lastDigraphIndex >= 0; --lastDigraphIndex) {
        if (thisChar == GERMAN_UMLAUT_DIGRAPHS[lastDigraphIndex].first) break;
    }
    // No match: return early
    if (lastDigraphIndex < 0) return false;

    // It's an interesting digraph if the second char matches too.
    return GERMAN_UMLAUT_DIGRAPHS[lastDigraphIndex].second == codes[(i + 1) * MAX_PROXIMITY_CHARS];
}

// Mostly the same arguments as the non-recursive version, except:
// codes is the original value. It points to the start of the work buffer, and gets passed as is.
// codesSize is the size of the user input (thus, it is the size of codesSrc).
// codesDest is the current point in the work buffer.
// codesSrc is the current point in the user-input, original, content-unmodified buffer.
// codesRemain is the remaining size in codesSrc.
void UnigramDictionary::getWordWithDigraphSuggestionsRec(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codesBuffer,
        const int codesBufferSize, const int flags, const int *codesSrc,
        const int codesRemain, const int currentDepth, int *codesDest, Correction *correction,
        WordsPriorityQueuePool *queuePool) {

    if (currentDepth < MAX_UMLAUT_SEARCH_DEPTH) {
        for (int i = 0; i < codesRemain; ++i) {
            if (isDigraph(codesSrc, i, codesRemain)) {
                // Found a digraph. We will try both spellings. eg. the word is "pruefen"

                // Copy the word up to the first char of the digraph, then continue processing
                // on the remaining part of the word, skipping the second char of the digraph.
                // In our example, copy "pru" and continue running on "fen"
                // Make i the index of the second char of the digraph for simplicity. Forgetting
                // to do that results in an infinite recursion so take care!
                ++i;
                memcpy(codesDest, codesSrc, i * BYTES_IN_ONE_CHAR);
                getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates,
                        codesBuffer, codesBufferSize, flags,
                        codesSrc + (i + 1) * MAX_PROXIMITY_CHARS, codesRemain - i - 1,
                        currentDepth + 1, codesDest + i * MAX_PROXIMITY_CHARS, correction,
                        queuePool);

                // Copy the second char of the digraph in place, then continue processing on
                // the remaining part of the word.
                // In our example, after "pru" in the buffer copy the "e", and continue on "fen"
                memcpy(codesDest + i * MAX_PROXIMITY_CHARS, codesSrc + i * MAX_PROXIMITY_CHARS,
                        BYTES_IN_ONE_CHAR);
                getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates,
                        codesBuffer, codesBufferSize, flags,
                        codesSrc + i * MAX_PROXIMITY_CHARS, codesRemain - i, currentDepth + 1,
                        codesDest + i * MAX_PROXIMITY_CHARS, correction, queuePool);
                return;
            }
        }
    }

    // If we come here, we hit the end of the word: let's check it against the dictionary.
    // In our example, we'll come here once for "prufen" and then once for "pruefen".
    // If the word contains several digraphs, we'll come it for the product of them.
    // eg. if the word is "ueberpruefen" we'll test, in order, against
    // "uberprufen", "uberpruefen", "ueberprufen", "ueberpruefen".
    const unsigned int remainingBytes = BYTES_IN_ONE_CHAR * codesRemain;
    if (0 != remainingBytes)
        memcpy(codesDest, codesSrc, remainingBytes);

    getWordSuggestions(proximityInfo, xcoordinates, ycoordinates, codesBuffer,
            (codesDest - codesBuffer) / MAX_PROXIMITY_CHARS + codesRemain, flags, correction,
            queuePool);
}

int UnigramDictionary::getSuggestions(ProximityInfo *proximityInfo,
        WordsPriorityQueuePool *queuePool, Correction *correction, const int *xcoordinates,
        const int *ycoordinates, const int *codes, const int codesSize, const int flags,
        unsigned short *outWords, int *frequencies) {

    Correction* masterCorrection = correction;
    if (REQUIRES_GERMAN_UMLAUT_PROCESSING & flags)
    { // Incrementally tune the word and try all possibilities
        int codesBuffer[getCodesBufferSize(codes, codesSize, MAX_PROXIMITY_CHARS)];
        getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates, codesBuffer,
                codesSize, flags, codes, codesSize, 0, codesBuffer, masterCorrection, queuePool);
    } else { // Normal processing
        getWordSuggestions(proximityInfo, xcoordinates, ycoordinates, codes, codesSize, flags,
                masterCorrection, queuePool);
    }

    PROF_START(20);
    if (DEBUG_DICT) {
        double ns = queuePool->getMasterQueue()->getHighestNormalizedScore(
                proximityInfo->getPrimaryInputWord(), codesSize, 0, 0, 0);
        ns += 0;
        AKLOGI("Max normalized score = %f", ns);
    }
    const int suggestedWordsCount =
            queuePool->getMasterQueue()->outputSuggestions(frequencies, outWords);

    if (DEBUG_DICT) {
        double ns = queuePool->getMasterQueue()->getHighestNormalizedScore(
                proximityInfo->getPrimaryInputWord(), codesSize, 0, 0, 0);
        ns += 0;
        AKLOGI("Returning %d words", suggestedWordsCount);
        /// Print the returned words
        for (int j = 0; j < suggestedWordsCount; ++j) {
            short unsigned int* w = outWords + j * MAX_WORD_LENGTH;
            char s[MAX_WORD_LENGTH];
            for (int i = 0; i <= MAX_WORD_LENGTH; i++) s[i] = w[i];
            AKLOGI("%s %i", s, frequencies[j]);
        }
    }
    PROF_END(20);
    PROF_CLOSE;
    return suggestedWordsCount;
}

void UnigramDictionary::getWordSuggestions(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes,
        const int inputLength, const int flags, Correction *correction,
        WordsPriorityQueuePool *queuePool) {

    PROF_OPEN;
    PROF_START(0);
    queuePool->clearAll();
    PROF_END(0);

    PROF_START(1);
    const bool useFullEditDistance = USE_FULL_EDIT_DISTANCE & flags;
    getOneWordSuggestions(proximityInfo, xcoordinates, ycoordinates, codes, useFullEditDistance,
            inputLength, correction, queuePool);
    PROF_END(1);

    PROF_START(2);
    // Note: This line is intentionally left blank
    PROF_END(2);

    PROF_START(3);
    // Note: This line is intentionally left blank
    PROF_END(3);

    PROF_START(4);
    // Note: This line is intentionally left blank
    bool hasAutoCorrectionCandidate = false;
    WordsPriorityQueue* masterQueue = queuePool->getMasterQueue();
    if (masterQueue->size() > 0) {
        double nsForMaster = masterQueue->getHighestNormalizedScore(
                proximityInfo->getPrimaryInputWord(), inputLength, 0, 0, 0);
        hasAutoCorrectionCandidate = (nsForMaster > START_TWO_WORDS_CORRECTION_THRESHOLD);
    }
    PROF_END(4);

    PROF_START(5);
    // Suggestions with missing space
    if (SUGGEST_WORDS_WITH_MISSING_SPACE_CHARACTER
            && inputLength >= MIN_USER_TYPED_LENGTH_FOR_MISSING_SPACE_SUGGESTION) {
        for (int i = 1; i < inputLength; ++i) {
            if (DEBUG_DICT) {
                AKLOGI("--- Suggest missing space characters %d", i);
            }
            getMissingSpaceWords(proximityInfo, xcoordinates, ycoordinates, codes,
                    useFullEditDistance, inputLength, i, correction, queuePool,
                    hasAutoCorrectionCandidate);
        }
    }
    PROF_END(5);

    PROF_START(6);
    if (SUGGEST_WORDS_WITH_SPACE_PROXIMITY && proximityInfo) {
        // The first and last "mistyped spaces" are taken care of by excessive character handling
        for (int i = 1; i < inputLength - 1; ++i) {
            if (DEBUG_DICT) {
                AKLOGI("--- Suggest words with proximity space %d", i);
            }
            const int x = xcoordinates[i];
            const int y = ycoordinates[i];
            if (DEBUG_PROXIMITY_INFO) {
                AKLOGI("Input[%d] x = %d, y = %d, has space proximity = %d",
                        i, x, y, proximityInfo->hasSpaceProximity(x, y));
            }
            if (proximityInfo->hasSpaceProximity(x, y)) {
                getMistypedSpaceWords(proximityInfo, xcoordinates, ycoordinates, codes,
                        useFullEditDistance, inputLength, i, correction, queuePool,
                        hasAutoCorrectionCandidate);
            }
        }
    }
    PROF_END(6);
    if (DEBUG_DICT) {
        queuePool->dumpSubQueue1TopSuggestions();
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            WordsPriorityQueue* queue = queuePool->getSubQueue(FIRST_WORD_INDEX, i);
            if (queue->size() > 0) {
                WordsPriorityQueue::SuggestedWord* sw = queue->top();
                const int score = sw->mScore;
                const unsigned short* word = sw->mWord;
                const int wordLength = sw->mWordLength;
                double ns = Correction::RankingAlgorithm::calcNormalizedScore(
                        proximityInfo->getPrimaryInputWord(), i, word, wordLength, score);
                ns += 0;
                AKLOGI("--- TOP SUB WORDS for %d --- %d %f [%d]", i, score, ns,
                        (ns > TWO_WORDS_CORRECTION_WITH_OTHER_ERROR_THRESHOLD));
                DUMP_WORD(proximityInfo->getPrimaryInputWord(), i);
                DUMP_WORD(word, wordLength);
            }
        }
    }
}

void UnigramDictionary::initSuggestions(ProximityInfo *proximityInfo, const int *xCoordinates,
        const int *yCoordinates, const int *codes, const int inputLength, Correction *correction) {
    if (DEBUG_DICT) {
        AKLOGI("initSuggest");
    }
    proximityInfo->setInputParams(codes, inputLength, xCoordinates, yCoordinates);
    const int maxDepth = min(inputLength * MAX_DEPTH_MULTIPLIER, MAX_WORD_LENGTH);
    correction->initCorrection(proximityInfo, inputLength, maxDepth);
}

static const char QUOTE = '\'';
static const char SPACE = ' ';

void UnigramDictionary::getOneWordSuggestions(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes,
        const bool useFullEditDistance, const int inputLength, Correction *correction,
        WordsPriorityQueuePool *queuePool) {
    initSuggestions(proximityInfo, xcoordinates, ycoordinates, codes, inputLength, correction);
    getSuggestionCandidates(useFullEditDistance, inputLength, correction, queuePool,
            true /* doAutoCompletion */, DEFAULT_MAX_ERRORS, FIRST_WORD_INDEX);
}

void UnigramDictionary::getSuggestionCandidates(const bool useFullEditDistance,
        const int inputLength, Correction *correction, WordsPriorityQueuePool *queuePool,
        const bool doAutoCompletion, const int maxErrors, const int currentWordIndex) {
    // TODO: Remove setCorrectionParams
    correction->setCorrectionParams(0, 0, 0,
            -1 /* spaceProximityPos */, -1 /* missingSpacePos */, useFullEditDistance,
            doAutoCompletion, maxErrors);
    int rootPosition = ROOT_POS;
    // Get the number of children of root, then increment the position
    int childCount = BinaryFormat::getGroupCountAndForwardPointer(DICT_ROOT, &rootPosition);
    int outputIndex = 0;

    correction->initCorrectionState(rootPosition, childCount, (inputLength <= 0));

    // Depth first search
    while (outputIndex >= 0) {
        if (correction->initProcessState(outputIndex)) {
            int siblingPos = correction->getTreeSiblingPos(outputIndex);
            int firstChildPos;

            const bool needsToTraverseChildrenNodes = processCurrentNode(siblingPos,
                    correction, &childCount, &firstChildPos, &siblingPos, queuePool,
                    currentWordIndex);
            // Update next sibling pos
            correction->setTreeSiblingPos(outputIndex, siblingPos);

            if (needsToTraverseChildrenNodes) {
                // Goes to child node
                outputIndex = correction->goDownTree(outputIndex, childCount, firstChildPos);
            }
        } else {
            // Goes to parent sibling node
            outputIndex = correction->getTreeParentIndex(outputIndex);
        }
    }
}

void UnigramDictionary::getMissingSpaceWords(ProximityInfo *proximityInfo, const int *xcoordinates,
        const int *ycoordinates, const int *codes, const bool useFullEditDistance,
        const int inputLength, const int missingSpacePos, Correction *correction,
        WordsPriorityQueuePool* queuePool, const bool hasAutoCorrectionCandidate) {
    getSplitTwoWordsSuggestions(proximityInfo, xcoordinates, ycoordinates, codes,
            useFullEditDistance, inputLength, missingSpacePos, -1/* spaceProximityPos */,
            correction, queuePool, hasAutoCorrectionCandidate);
}

void UnigramDictionary::getMistypedSpaceWords(ProximityInfo *proximityInfo, const int *xcoordinates,
        const int *ycoordinates, const int *codes, const bool useFullEditDistance,
        const int inputLength, const int spaceProximityPos, Correction *correction,
        WordsPriorityQueuePool* queuePool, const bool hasAutoCorrectionCandidate) {
    getSplitTwoWordsSuggestions(proximityInfo, xcoordinates, ycoordinates, codes,
            useFullEditDistance, inputLength, -1 /* missingSpacePos */, spaceProximityPos,
            correction, queuePool, hasAutoCorrectionCandidate);
}

inline void UnigramDictionary::onTerminal(const int freq,
        const TerminalAttributes& terminalAttributes, Correction *correction,
        WordsPriorityQueuePool *queuePool, const bool addToMasterQueue,
        const int currentWordIndex) {
    const int inputIndex = correction->getInputIndex();
    const bool addToSubQueue = inputIndex < SUB_QUEUE_MAX_COUNT;

    int wordLength;
    unsigned short* wordPointer;

    if ((currentWordIndex == 1) && addToMasterQueue) {
        WordsPriorityQueue *masterQueue = queuePool->getMasterQueue();
        const int finalFreq = correction->getFinalFreq(freq, &wordPointer, &wordLength);
        if (finalFreq != NOT_A_FREQUENCY) {
            if (!terminalAttributes.isShortcutOnly()) {
                addWord(wordPointer, wordLength, finalFreq, masterQueue);
            }

            // Please note that the shortcut candidates will be added to the master queue only.
            TerminalAttributes::ShortcutIterator iterator =
                    terminalAttributes.getShortcutIterator();
            while (iterator.hasNextShortcutTarget()) {
                // TODO: addWord only supports weak ordering, meaning we have no means
                // to control the order of the shortcuts relative to one another or to the word.
                // We need to either modulate the frequency of each shortcut according
                // to its own shortcut frequency or to make the queue
                // so that the insert order is protected inside the queue for words
                // with the same score.
                uint16_t shortcutTarget[MAX_WORD_LENGTH_INTERNAL];
                const int shortcutTargetStringLength = iterator.getNextShortcutTarget(
                        MAX_WORD_LENGTH_INTERNAL, shortcutTarget);
                addWord(shortcutTarget, shortcutTargetStringLength, finalFreq, masterQueue);
            }
        }
    }

    // We only allow two words + other error correction for words with SUB_QUEUE_MIN_WORD_LENGTH
    // or more length.
    if (inputIndex >= SUB_QUEUE_MIN_WORD_LENGTH && addToSubQueue) {
        WordsPriorityQueue *subQueue;
        subQueue = queuePool->getSubQueue(currentWordIndex, inputIndex);
        if (!subQueue) {
            return;
        }
        const int finalFreq = correction->getFinalFreqForSubQueue(freq, &wordPointer, &wordLength,
                inputIndex);
        addWord(wordPointer, wordLength, finalFreq, subQueue);
    }
}

int UnigramDictionary::getSubStringSuggestion(
        ProximityInfo *proximityInfo, const int *xcoordinates, const int *ycoordinates,
        const int *codes, const bool useFullEditDistance, const Correction *correction,
        WordsPriorityQueuePool* queuePool, const bool hasAutoCorrectionCandidate,
        const int currentWordIndex, const int inputWordStartPos, const int inputWordLength,
        const int outputWordStartPos, unsigned short* outputWord, int *outputWordLength) {
//    under constructiong
//    unsigned short* tempOutputWord = 0;
//    int tempOutputWordLength = 0;
//    int freq = getMostFrequentWordLike(
//            inputWordStartPos, inputWordLength, proximityInfo, mWord);
//    if (freq > 0) {
//        tempOutputWordLength = inputWordLength;
//        tempOutputWord = mWord;
//    } else if (!hasAutoCorrectionCandidate) {
//    }
    return 0;
}

void UnigramDictionary::getSplitTwoWordsSuggestions(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes,
        const bool useFullEditDistance, const int inputLength, const int missingSpacePos,
        const int  spaceProximityPos, Correction *correction, WordsPriorityQueuePool* queuePool,
        const bool hasAutoCorrectionCandidate) {
    if (inputLength >= MAX_WORD_LENGTH) return;
    if (DEBUG_DICT) {
        int inputCount = 0;
        if (spaceProximityPos >= 0) ++inputCount;
        if (missingSpacePos >= 0) ++inputCount;
        assert(inputCount <= 1);
        // MAX_PROXIMITY_CHARS_SIZE in ProximityInfo.java should be 16
        assert(MAX_PROXIMITY_CHARS == 16);
    }

    initSuggestions(proximityInfo, xcoordinates, ycoordinates, codes,
            inputLength, correction);
    WordsPriorityQueue *masterQueue = queuePool->getMasterQueue();
    const bool isSpaceProximity = spaceProximityPos >= 0;

    // First word
    const int firstInputWordStartPos = 0;
    const int firstInputWordLength = isSpaceProximity ? spaceProximityPos : missingSpacePos;
    int firstFreq = getMostFrequentWordLike(
            firstInputWordStartPos, firstInputWordLength, proximityInfo, mWord);
    unsigned short* firstOutputWord = 0;
    int firstOutputWordLength = 0;
    if (firstFreq > 0) {
        firstOutputWordLength = firstInputWordLength;
        firstOutputWord = mWord;
    } else if (!hasAutoCorrectionCandidate) {
        WordsPriorityQueue* firstWordQueue = queuePool->getSubQueue(
                FIRST_WORD_INDEX, firstInputWordLength);
        if (!firstWordQueue || firstWordQueue->size() < 1) {
            return;
        }
        int score = 0;
        const double ns = firstWordQueue->getHighestNormalizedScore(
                proximityInfo->getPrimaryInputWord(), firstInputWordLength,
                &firstOutputWord, &score, &firstOutputWordLength);
        if (DEBUG_DICT) {
            AKLOGI("NS1 = %f, Score = %d", ns, score);
        }
        // Two words correction won't be done if the score of the first word doesn't exceed the
        // threshold.
        if (ns < TWO_WORDS_CORRECTION_WITH_OTHER_ERROR_THRESHOLD
                || firstOutputWordLength < SUB_QUEUE_MIN_WORD_LENGTH) {
            return;
        }
        firstFreq = score >> (firstOutputWordLength
                + TWO_WORDS_PLUS_OTHER_ERROR_CORRECTION_DEMOTION_DIVIDER);
    }

    if (DEBUG_DICT) {
        AKLOGI("First freq: %d", firstFreq);
    }

    if (firstFreq <= 0 || firstOutputWordLength <= 0 || MAX_WORD_LENGTH <= firstOutputWordLength) {
        return;
    }

    // Allocating fixed length array on stack
    unsigned short outputWord[MAX_WORD_LENGTH];
    int outputWordLength = 0;

    for (int i = 0; i < firstOutputWordLength; ++i) {
        outputWord[i] = firstOutputWord[i];
    }

    outputWord[firstOutputWordLength] = SPACE;
    outputWordLength = firstOutputWordLength + 1;

    // Second word
    const int secondInputWordLength = isSpaceProximity
            ? (inputLength - spaceProximityPos - 1)
            : (inputLength - missingSpacePos);
    const int secondInputWordStartPos =
            isSpaceProximity ? (spaceProximityPos + 1) : missingSpacePos;
    int secondFreq = getMostFrequentWordLike(
            secondInputWordStartPos, secondInputWordLength, proximityInfo, mWord);
    unsigned short* secondOutputWord = 0;
    int secondOutputWordLength = 0;

    if (secondFreq > 0) {
        secondOutputWordLength = secondInputWordLength;
        secondOutputWord = mWord;
    } else if (!hasAutoCorrectionCandidate) {
        const int offset = secondInputWordStartPos;
        initSuggestions(proximityInfo, &xcoordinates[offset], &ycoordinates[offset],
                codes + offset * MAX_PROXIMITY_CHARS, secondInputWordLength, correction);
        queuePool->clearSubQueue(SECOND_WORD_INDEX);
        getSuggestionCandidates(useFullEditDistance, secondInputWordLength, correction,
                queuePool, false, MAX_ERRORS_FOR_TWO_WORDS, SECOND_WORD_INDEX);
        if (DEBUG_DICT) {
            AKLOGI("Dump second word candidates %d", secondInputWordLength);
            for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
                queuePool->getSubQueue(SECOND_WORD_INDEX, i)->dumpTopWord();
            }
        }
        WordsPriorityQueue* secondWordQueue = queuePool->getSubQueue(
                SECOND_WORD_INDEX, secondInputWordLength);
        if (!secondWordQueue || secondWordQueue->size() < 1) {
            return;
        }
        int score = 0;
        const double ns = secondWordQueue->getHighestNormalizedScore(
                proximityInfo->getPrimaryInputWord(), secondInputWordLength,
                &secondOutputWord, &score, &secondOutputWordLength);
        if (DEBUG_DICT) {
            AKLOGI("NS2 = %f, Score = %d", ns, score);
        }
        // Two words correction won't be done if the score of the first word doesn't exceed the
        // threshold.
        if (ns < TWO_WORDS_CORRECTION_WITH_OTHER_ERROR_THRESHOLD
                || secondOutputWordLength < SUB_QUEUE_MIN_WORD_LENGTH) {
            return;
        }
        secondFreq = score >> (secondOutputWordLength
                + TWO_WORDS_PLUS_OTHER_ERROR_CORRECTION_DEMOTION_DIVIDER);
    }

    if (DEBUG_DICT) {
        DUMP_WORD(secondOutputWord, secondOutputWordLength);
        AKLOGI("Second freq: %d", secondFreq);
    }

    if (secondFreq <= 0 || secondOutputWordLength <= 0
            || MAX_WORD_LENGTH <= (firstOutputWordLength + 1 + secondOutputWordLength)) {
        return;
    }

    for (int i = 0; i < secondOutputWordLength; ++i) {
        outputWord[firstOutputWordLength + 1 + i] = secondOutputWord[i];
    }

    outputWordLength += secondOutputWordLength;

    // TODO: Remove initSuggestions and correction->setCorrectionParams
    initSuggestions(proximityInfo, xcoordinates, ycoordinates, codes, inputLength, correction);

    correction->setCorrectionParams(-1 /* skipPos */, -1 /* excessivePos */,
            -1 /* transposedPos */, spaceProximityPos, missingSpacePos,
            useFullEditDistance, false /* doAutoCompletion */, MAX_ERRORS_FOR_TWO_WORDS);
    const int pairFreq = correction->getFreqForSplitTwoWords(firstFreq, secondFreq, outputWord);
    if (DEBUG_DICT) {
        AKLOGI("Split two words:  %d, %d, %d, %d", firstFreq, secondFreq, pairFreq, inputLength);
    }
    addWord(outputWord, outputWordLength, pairFreq, masterQueue);
    return;
}

// Wrapper for getMostFrequentWordLikeInner, which matches it to the previous
// interface.
inline int UnigramDictionary::getMostFrequentWordLike(const int startInputIndex,
        const int inputLength, ProximityInfo *proximityInfo, unsigned short *word) {
    uint16_t inWord[inputLength];

    for (int i = 0; i < inputLength; ++i) {
        inWord[i] = (uint16_t)proximityInfo->getPrimaryCharAt(startInputIndex + i);
    }
    return getMostFrequentWordLikeInner(inWord, inputLength, word);
}

// This function will take the position of a character array within a CharGroup,
// and check it actually like-matches the word in inWord starting at startInputIndex,
// that is, it matches it with case and accents squashed.
// The function returns true if there was a full match, false otherwise.
// The function will copy on-the-fly the characters in the CharGroup to outNewWord.
// It will also place the end position of the array in outPos; in outInputIndex,
// it will place the index of the first char AFTER the match if there was a match,
// and the initial position if there was not. It makes sense because if there was
// a match we want to continue searching, but if there was not, we want to go to
// the next CharGroup.
// In and out parameters may point to the same location. This function takes care
// not to use any input parameters after it wrote into its outputs.
static inline bool testCharGroupForContinuedLikeness(const uint8_t flags,
        const uint8_t* const root, const int startPos,
        const uint16_t* const inWord, const int startInputIndex,
        int32_t* outNewWord, int* outInputIndex, int* outPos) {
    const bool hasMultipleChars = (0 != (UnigramDictionary::FLAG_HAS_MULTIPLE_CHARS & flags));
    int pos = startPos;
    int32_t character = BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
    int32_t baseChar = toBaseLowerCase(character);
    const uint16_t wChar = toBaseLowerCase(inWord[startInputIndex]);

    if (baseChar != wChar) {
        *outPos = hasMultipleChars ? BinaryFormat::skipOtherCharacters(root, pos) : pos;
        *outInputIndex = startInputIndex;
        return false;
    }
    int inputIndex = startInputIndex;
    outNewWord[inputIndex] = character;
    if (hasMultipleChars) {
        character = BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
        while (NOT_A_CHARACTER != character) {
            baseChar = toBaseLowerCase(character);
            if (toBaseLowerCase(inWord[++inputIndex]) != baseChar) {
                *outPos = BinaryFormat::skipOtherCharacters(root, pos);
                *outInputIndex = startInputIndex;
                return false;
            }
            outNewWord[inputIndex] = character;
            character = BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
        }
    }
    *outInputIndex = inputIndex + 1;
    *outPos = pos;
    return true;
}

// This function is invoked when a word like the word searched for is found.
// It will compare the frequency to the max frequency, and if greater, will
// copy the word into the output buffer. In output value maxFreq, it will
// write the new maximum frequency if it changed.
static inline void onTerminalWordLike(const int freq, int32_t* newWord, const int length,
        short unsigned int* outWord, int* maxFreq) {
    if (freq > *maxFreq) {
        for (int q = 0; q < length; ++q)
            outWord[q] = newWord[q];
        outWord[length] = 0;
        *maxFreq = freq;
    }
}

// Will find the highest frequency of the words like the one passed as an argument,
// that is, everything that only differs by case/accents.
int UnigramDictionary::getMostFrequentWordLikeInner(const uint16_t * const inWord,
        const int length, short unsigned int* outWord) {
    int32_t newWord[MAX_WORD_LENGTH_INTERNAL];
    int depth = 0;
    int maxFreq = -1;
    const uint8_t* const root = DICT_ROOT;

    int startPos = 0;
    mStackChildCount[0] = BinaryFormat::getGroupCountAndForwardPointer(root, &startPos);
    mStackInputIndex[0] = 0;
    mStackSiblingPos[0] = startPos;
    while (depth >= 0) {
        const int charGroupCount = mStackChildCount[depth];
        int pos = mStackSiblingPos[depth];
        for (int charGroupIndex = charGroupCount - 1; charGroupIndex >= 0; --charGroupIndex) {
            int inputIndex = mStackInputIndex[depth];
            const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
            // Test whether all chars in this group match with the word we are searching for. If so,
            // we want to traverse its children (or if the length match, evaluate its frequency).
            // Note that this function will output the position regardless, but will only write
            // into inputIndex if there is a match.
            const bool isAlike = testCharGroupForContinuedLikeness(flags, root, pos, inWord,
                    inputIndex, newWord, &inputIndex, &pos);
            if (isAlike && (FLAG_IS_TERMINAL & flags) && (inputIndex == length)) {
                const int frequency = BinaryFormat::readFrequencyWithoutMovingPointer(root, pos);
                onTerminalWordLike(frequency, newWord, inputIndex, outWord, &maxFreq);
            }
            pos = BinaryFormat::skipFrequency(flags, pos);
            const int siblingPos = BinaryFormat::skipChildrenPosAndAttributes(root, flags, pos);
            const int childrenNodePos = BinaryFormat::readChildrenPosition(root, flags, pos);
            // If we had a match and the word has children, we want to traverse them. We don't have
            // to traverse words longer than the one we are searching for, since they will not match
            // anyway, so don't traverse unless inputIndex < length.
            if (isAlike && (-1 != childrenNodePos) && (inputIndex < length)) {
                // Save position for this depth, to get back to this once children are done
                mStackChildCount[depth] = charGroupIndex;
                mStackSiblingPos[depth] = siblingPos;
                // Prepare stack values for next depth
                ++depth;
                int childrenPos = childrenNodePos;
                mStackChildCount[depth] =
                        BinaryFormat::getGroupCountAndForwardPointer(root, &childrenPos);
                mStackSiblingPos[depth] = childrenPos;
                mStackInputIndex[depth] = inputIndex;
                pos = childrenPos;
                // Go to the next depth level.
                ++depth;
                break;
            } else {
                // No match, or no children, or word too long to ever match: go the next sibling.
                pos = siblingPos;
            }
        }
        --depth;
    }
    return maxFreq;
}

bool UnigramDictionary::isValidWord(const uint16_t* const inWord, const int length) const {
    return NOT_VALID_WORD != BinaryFormat::getTerminalPosition(DICT_ROOT, inWord, length);
}

// TODO: remove this function.
int UnigramDictionary::getBigramPosition(int pos, unsigned short *word, int offset,
        int length) const {
    return -1;
}

// ProcessCurrentNode returns a boolean telling whether to traverse children nodes or not.
// If the return value is false, then the caller should read in the output "nextSiblingPosition"
// to find out the address of the next sibling node and pass it to a new call of processCurrentNode.
// It is worthy to note that when false is returned, the output values other than
// nextSiblingPosition are undefined.
// If the return value is true, then the caller must proceed to traverse the children of this
// node. processCurrentNode will output the information about the children: their count in
// newCount, their position in newChildrenPosition, the traverseAllNodes flag in
// newTraverseAllNodes, the match weight into newMatchRate, the input index into newInputIndex, the
// diffs into newDiffs, the sibling position in nextSiblingPosition, and the output index into
// newOutputIndex. Please also note the following caveat: processCurrentNode does not know when
// there aren't any more nodes at this level, it merely returns the address of the first byte after
// the current node in nextSiblingPosition. Thus, the caller must keep count of the nodes at any
// given level, as output into newCount when traversing this level's parent.
inline bool UnigramDictionary::processCurrentNode(const int initialPos,
        Correction *correction, int *newCount,
        int *newChildrenPosition, int *nextSiblingPosition, WordsPriorityQueuePool *queuePool,
        const int currentWordIndex) {
    if (DEBUG_DICT) {
        correction->checkState();
    }
    int pos = initialPos;

    // Flags contain the following information:
    // - Address type (MASK_GROUP_ADDRESS_TYPE) on two bits:
    //   - FLAG_GROUP_ADDRESS_TYPE_{ONE,TWO,THREE}_BYTES means there are children and their address
    //     is on the specified number of bytes.
    //   - FLAG_GROUP_ADDRESS_TYPE_NOADDRESS means there are no children, and therefore no address.
    // - FLAG_HAS_MULTIPLE_CHARS: whether this node has multiple char or not.
    // - FLAG_IS_TERMINAL: whether this node is a terminal or not (it may still have children)
    // - FLAG_HAS_BIGRAMS: whether this node has bigrams or not
    const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(DICT_ROOT, &pos);
    const bool hasMultipleChars = (0 != (FLAG_HAS_MULTIPLE_CHARS & flags));
    const bool isTerminalNode = (0 != (FLAG_IS_TERMINAL & flags));

    bool needsToInvokeOnTerminal = false;

    // This gets only ONE character from the stream. Next there will be:
    // if FLAG_HAS_MULTIPLE CHARS: the other characters of the same node
    // else if FLAG_IS_TERMINAL: the frequency
    // else if MASK_GROUP_ADDRESS_TYPE is not NONE: the children address
    // Note that you can't have a node that both is not a terminal and has no children.
    int32_t c = BinaryFormat::getCharCodeAndForwardPointer(DICT_ROOT, &pos);
    assert(NOT_A_CHARACTER != c);

    // We are going to loop through each character and make it look like it's a different
    // node each time. To do that, we will process characters in this node in order until
    // we find the character terminator. This is signalled by getCharCode* returning
    // NOT_A_CHARACTER.
    // As a special case, if there is only one character in this node, we must not read the
    // next bytes so we will simulate the NOT_A_CHARACTER return by testing the flags.
    // This way, each loop run will look like a "virtual node".
    do {
        // We prefetch the next char. If 'c' is the last char of this node, we will have
        // NOT_A_CHARACTER in the next char. From this we can decide whether this virtual node
        // should behave as a terminal or not and whether we have children.
        const int32_t nextc = hasMultipleChars
                ? BinaryFormat::getCharCodeAndForwardPointer(DICT_ROOT, &pos) : NOT_A_CHARACTER;
        const bool isLastChar = (NOT_A_CHARACTER == nextc);
        // If there are more chars in this nodes, then this virtual node is not a terminal.
        // If we are on the last char, this virtual node is a terminal if this node is.
        const bool isTerminal = isLastChar && isTerminalNode;

        Correction::CorrectionType stateType = correction->processCharAndCalcState(
                c, isTerminal);
        if (stateType == Correction::TRAVERSE_ALL_ON_TERMINAL
                || stateType == Correction::ON_TERMINAL) {
            needsToInvokeOnTerminal = true;
        } else if (stateType == Correction::UNRELATED || correction->needsToPrune()) {
            // We found that this is an unrelated character, so we should give up traversing
            // this node and its children entirely.
            // However we may not be on the last virtual node yet so we skip the remaining
            // characters in this node, the frequency if it's there, read the next sibling
            // position to output it, then return false.
            // We don't have to output other values because we return false, as in
            // "don't traverse children".
            if (!isLastChar) {
                pos = BinaryFormat::skipOtherCharacters(DICT_ROOT, pos);
            }
            pos = BinaryFormat::skipFrequency(flags, pos);
            *nextSiblingPosition =
                    BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
            return false;
        }

        // Prepare for the next character. Promote the prefetched char to current char - the loop
        // will take care of prefetching the next. If we finally found our last char, nextc will
        // contain NOT_A_CHARACTER.
        c = nextc;
    } while (NOT_A_CHARACTER != c);

    if (isTerminalNode) {
        // The frequency should be here, because we come here only if this is actually
        // a terminal node, and we are on its last char.
        const int freq = BinaryFormat::readFrequencyWithoutMovingPointer(DICT_ROOT, pos);
        const int childrenAddressPos = BinaryFormat::skipFrequency(flags, pos);
        const int attributesPos = BinaryFormat::skipChildrenPosition(flags, childrenAddressPos);
        TerminalAttributes terminalAttributes(DICT_ROOT, flags, attributesPos);
        onTerminal(freq, terminalAttributes, correction, queuePool, needsToInvokeOnTerminal,
                currentWordIndex);

        // If there are more chars in this node, then this virtual node has children.
        // If we are on the last char, this virtual node has children if this node has.
        const bool hasChildren = BinaryFormat::hasChildrenInFlags(flags);

        // This character matched the typed character (enough to traverse the node at least)
        // so we just evaluated it. Now we should evaluate this virtual node's children - that
        // is, if it has any. If it has no children, we're done here - so we skip the end of
        // the node, output the siblings position, and return false "don't traverse children".
        // Note that !hasChildren implies isLastChar, so we know we don't have to skip any
        // remaining char in this group for there can't be any.
        if (!hasChildren) {
            pos = BinaryFormat::skipFrequency(flags, pos);
            *nextSiblingPosition =
                    BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
            return false;
        }

        // Optimization: Prune out words that are too long compared to how much was typed.
        if (correction->needsToPrune()) {
            pos = BinaryFormat::skipFrequency(flags, pos);
            *nextSiblingPosition =
                    BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
            if (DEBUG_DICT_FULL) {
                AKLOGI("Traversing was pruned.");
            }
            return false;
        }
    }

    // Now we finished processing this node, and we want to traverse children. If there are no
    // children, we can't come here.
    assert(BinaryFormat::hasChildrenInFlags(flags));

    // If this node was a terminal it still has the frequency under the pointer (it may have been
    // read, but not skipped - see readFrequencyWithoutMovingPointer).
    // Next come the children position, then possibly attributes (attributes are bigrams only for
    // now, maybe something related to shortcuts in the future).
    // Once this is read, we still need to output the number of nodes in the immediate children of
    // this node, so we read and output it before returning true, as in "please traverse children".
    pos = BinaryFormat::skipFrequency(flags, pos);
    int childrenPos = BinaryFormat::readChildrenPosition(DICT_ROOT, flags, pos);
    *nextSiblingPosition = BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
    *newCount = BinaryFormat::getGroupCountAndForwardPointer(DICT_ROOT, &childrenPos);
    *newChildrenPosition = childrenPos;
    return true;
}

} // namespace latinime
