/*
 * Copyright (C) 2010, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <cstring>

#define LOG_TAG "LatinIME: unigram_dictionary.cpp"

#include "binary_format.h"
#include "char_utils.h"
#include "defines.h"
#include "dictionary.h"
#include "digraph_utils.h"
#include "proximity_info.h"
#include "terminal_attributes.h"
#include "unigram_dictionary.h"
#include "words_priority_queue.h"
#include "words_priority_queue_pool.h"

namespace latinime {

// TODO: check the header
UnigramDictionary::UnigramDictionary(const uint8_t *const streamStart, const unsigned int dictFlags)
        : DICT_ROOT(streamStart), ROOT_POS(0),
          MAX_DIGRAPH_SEARCH_DEPTH(DEFAULT_MAX_DIGRAPH_SEARCH_DEPTH), DICT_FLAGS(dictFlags) {
    if (DEBUG_DICT) {
        AKLOGI("UnigramDictionary - constructor");
    }
}

UnigramDictionary::~UnigramDictionary() {
}

// TODO: This needs to take a const int* and not tinker with its contents
static void addWord(int *word, int length, int probability, WordsPriorityQueue *queue, int type) {
    queue->push(probability, word, length, type);
}

// Return the replacement code point for a digraph, or 0 if none.
int UnigramDictionary::getDigraphReplacement(const int *codes, const int i, const int inputSize,
        const DigraphUtils::digraph_t *const digraphs, const unsigned int digraphsSize) const {

    // There can't be a digraph if we don't have at least 2 characters to examine
    if (i + 2 > inputSize) return false;

    // Search for the first char of some digraph
    int lastDigraphIndex = -1;
    const int thisChar = codes[i];
    for (lastDigraphIndex = digraphsSize - 1; lastDigraphIndex >= 0; --lastDigraphIndex) {
        if (thisChar == digraphs[lastDigraphIndex].first) break;
    }
    // No match: return early
    if (lastDigraphIndex < 0) return 0;

    // It's an interesting digraph if the second char matches too.
    if (digraphs[lastDigraphIndex].second == codes[i + 1]) {
        return digraphs[lastDigraphIndex].compositeGlyph;
    } else {
        return 0;
    }
}

// Mostly the same arguments as the non-recursive version, except:
// codes is the original value. It points to the start of the work buffer, and gets passed as is.
// inputSize is the size of the user input (thus, it is the size of codesSrc).
// codesDest is the current point in the work buffer.
// codesSrc is the current point in the user-input, original, content-unmodified buffer.
// codesRemain is the remaining size in codesSrc.
void UnigramDictionary::getWordWithDigraphSuggestionsRec(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codesBuffer,
        int *xCoordinatesBuffer, int *yCoordinatesBuffer,
        const int codesBufferSize, const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        const bool useFullEditDistance, const int *codesSrc,
        const int codesRemain, const int currentDepth, int *codesDest, Correction *correction,
        WordsPriorityQueuePool *queuePool,
        const DigraphUtils::digraph_t *const digraphs, const unsigned int digraphsSize) const {
    ASSERT(sizeof(codesDest[0]) == sizeof(codesSrc[0]));
    ASSERT(sizeof(xCoordinatesBuffer[0]) == sizeof(xcoordinates[0]));
    ASSERT(sizeof(yCoordinatesBuffer[0]) == sizeof(ycoordinates[0]));

    const int startIndex = static_cast<int>(codesDest - codesBuffer);
    if (currentDepth < MAX_DIGRAPH_SEARCH_DEPTH) {
        for (int i = 0; i < codesRemain; ++i) {
            xCoordinatesBuffer[startIndex + i] = xcoordinates[codesBufferSize - codesRemain + i];
            yCoordinatesBuffer[startIndex + i] = ycoordinates[codesBufferSize - codesRemain + i];
            const int replacementCodePoint =
                    getDigraphReplacement(codesSrc, i, codesRemain, digraphs, digraphsSize);
            if (0 != replacementCodePoint) {
                // Found a digraph. We will try both spellings. eg. the word is "pruefen"

                // Copy the word up to the first char of the digraph, including proximity chars,
                // and overwrite the primary code with the replacement code point. Then, continue
                // processing on the remaining part of the word, skipping the second char of the
                // digraph.
                // In our example, copy "pru", replace "u" with the version with the diaeresis and
                // continue running on "fen".
                // Make i the index of the second char of the digraph for simplicity. Forgetting
                // to do that results in an infinite recursion so take care!
                ++i;
                memcpy(codesDest, codesSrc, i * sizeof(codesDest[0]));
                codesDest[i - 1] = replacementCodePoint;
                getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates,
                        codesBuffer, xCoordinatesBuffer, yCoordinatesBuffer, codesBufferSize,
                        bigramMap, bigramFilter, useFullEditDistance, codesSrc + i + 1,
                        codesRemain - i - 1, currentDepth + 1, codesDest + i, correction,
                        queuePool, digraphs, digraphsSize);

                // Copy the second char of the digraph in place, then continue processing on
                // the remaining part of the word.
                // In our example, after "pru" in the buffer copy the "e", and continue on "fen"
                memcpy(codesDest + i, codesSrc + i, sizeof(codesDest[0]));
                getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates,
                        codesBuffer, xCoordinatesBuffer, yCoordinatesBuffer, codesBufferSize,
                        bigramMap, bigramFilter, useFullEditDistance, codesSrc + i, codesRemain - i,
                        currentDepth + 1, codesDest + i, correction, queuePool, digraphs,
                        digraphsSize);
                return;
            }
        }
    }

    // If we come here, we hit the end of the word: let's check it against the dictionary.
    // In our example, we'll come here once for "prufen" and then once for "pruefen".
    // If the word contains several digraphs, we'll come it for the product of them.
    // eg. if the word is "ueberpruefen" we'll test, in order, against
    // "uberprufen", "uberpruefen", "ueberprufen", "ueberpruefen".
    const unsigned int remainingBytes = sizeof(codesDest[0]) * codesRemain;
    if (0 != remainingBytes) {
        memcpy(codesDest, codesSrc, remainingBytes);
        memcpy(&xCoordinatesBuffer[startIndex], &xcoordinates[codesBufferSize - codesRemain],
                sizeof(xCoordinatesBuffer[0]) * codesRemain);
        memcpy(&yCoordinatesBuffer[startIndex], &ycoordinates[codesBufferSize - codesRemain],
                sizeof(yCoordinatesBuffer[0]) * codesRemain);
    }

    getWordSuggestions(proximityInfo, xCoordinatesBuffer, yCoordinatesBuffer, codesBuffer,
            startIndex + codesRemain, bigramMap, bigramFilter, useFullEditDistance, correction,
            queuePool);
}

// bigramMap contains the association <bigram address> -> <bigram probability>
// bigramFilter is a bloom filter for fast rejection: see functions setInFilter and isInFilter
// in bigram_dictionary.cpp
int UnigramDictionary::getSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
        const int *ycoordinates, const int *inputCodePoints, const int inputSize,
        const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        const bool useFullEditDistance, int *outWords, int *frequencies, int *outputTypes) const {
    WordsPriorityQueuePool queuePool(MAX_RESULTS, SUB_QUEUE_MAX_WORDS);
    queuePool.clearAll();
    Correction masterCorrection;
    masterCorrection.resetCorrection();
    const DigraphUtils::digraph_t *digraphs = 0;
    const int digraphsSize =
            DigraphUtils::getAllDigraphsForDictionaryAndReturnSize(DICT_FLAGS, &digraphs);
    if (digraphsSize > 0)
    { // Incrementally tune the word and try all possibilities
        int codesBuffer[sizeof(*inputCodePoints) * inputSize];
        int xCoordinatesBuffer[inputSize];
        int yCoordinatesBuffer[inputSize];
        getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates, codesBuffer,
                xCoordinatesBuffer, yCoordinatesBuffer, inputSize, bigramMap, bigramFilter,
                useFullEditDistance, inputCodePoints, inputSize, 0, codesBuffer, &masterCorrection,
                &queuePool, digraphs, digraphsSize);
    } else { // Normal processing
        getWordSuggestions(proximityInfo, xcoordinates, ycoordinates, inputCodePoints, inputSize,
                bigramMap, bigramFilter, useFullEditDistance, &masterCorrection, &queuePool);
    }

    PROF_START(20);
    if (DEBUG_DICT) {
        float ns = queuePool.getMasterQueue()->getHighestNormalizedScore(
                masterCorrection.getPrimaryInputWord(), inputSize, 0, 0, 0);
        ns += 0;
        AKLOGI("Max normalized score = %f", ns);
    }
    const int suggestedWordsCount =
            queuePool.getMasterQueue()->outputSuggestions(masterCorrection.getPrimaryInputWord(),
                    inputSize, frequencies, outWords, outputTypes);

    if (DEBUG_DICT) {
        float ns = queuePool.getMasterQueue()->getHighestNormalizedScore(
                masterCorrection.getPrimaryInputWord(), inputSize, 0, 0, 0);
        ns += 0;
        AKLOGI("Returning %d words", suggestedWordsCount);
        /// Print the returned words
        for (int j = 0; j < suggestedWordsCount; ++j) {
            int *w = outWords + j * MAX_WORD_LENGTH;
            char s[MAX_WORD_LENGTH];
            for (int i = 0; i <= MAX_WORD_LENGTH; i++) s[i] = w[i];
            (void)s; // To suppress compiler warning
            AKLOGI("%s %i", s, frequencies[j]);
        }
    }
    PROF_END(20);
    PROF_CLOSE;
    return suggestedWordsCount;
}

void UnigramDictionary::getWordSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
        const int *ycoordinates, const int *inputCodePoints, const int inputSize,
        const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        const bool useFullEditDistance, Correction *correction, WordsPriorityQueuePool *queuePool)
        const {
    PROF_OPEN;
    PROF_START(0);
    PROF_END(0);

    PROF_START(1);
    getOneWordSuggestions(proximityInfo, xcoordinates, ycoordinates, inputCodePoints, bigramMap,
            bigramFilter, useFullEditDistance, inputSize, correction, queuePool);
    PROF_END(1);

    PROF_START(2);
    // Note: This line is intentionally left blank
    PROF_END(2);

    PROF_START(3);
    // Note: This line is intentionally left blank
    PROF_END(3);

    PROF_START(4);
    bool hasAutoCorrectionCandidate = false;
    WordsPriorityQueue *masterQueue = queuePool->getMasterQueue();
    if (masterQueue->size() > 0) {
        float nsForMaster = masterQueue->getHighestNormalizedScore(
                correction->getPrimaryInputWord(), inputSize, 0, 0, 0);
        hasAutoCorrectionCandidate = (nsForMaster > START_TWO_WORDS_CORRECTION_THRESHOLD);
    }
    PROF_END(4);

    PROF_START(5);
    // Multiple word suggestions
    if (SUGGEST_MULTIPLE_WORDS
            && inputSize >= MIN_USER_TYPED_LENGTH_FOR_MULTIPLE_WORD_SUGGESTION) {
        getSplitMultipleWordsSuggestions(proximityInfo, xcoordinates, ycoordinates, inputCodePoints,
                useFullEditDistance, inputSize, correction, queuePool,
                hasAutoCorrectionCandidate);
    }
    PROF_END(5);

    PROF_START(6);
    // Note: This line is intentionally left blank
    PROF_END(6);

    if (DEBUG_DICT) {
        queuePool->dumpSubQueue1TopSuggestions();
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            WordsPriorityQueue *queue = queuePool->getSubQueue(FIRST_WORD_INDEX, i);
            if (queue->size() > 0) {
                WordsPriorityQueue::SuggestedWord *sw = queue->top();
                const int score = sw->mScore;
                const int *word = sw->mWord;
                const int wordLength = sw->mWordLength;
                float ns = Correction::RankingAlgorithm::calcNormalizedScore(
                        correction->getPrimaryInputWord(), i, word, wordLength, score);
                ns += 0;
                AKLOGI("--- TOP SUB WORDS for %d --- %d %f [%d]", i, score, ns,
                        (ns > TWO_WORDS_CORRECTION_WITH_OTHER_ERROR_THRESHOLD));
                DUMP_WORD(correction->getPrimaryInputWord(), i);
                DUMP_WORD(word, wordLength);
            }
        }
    }
}

void UnigramDictionary::initSuggestions(ProximityInfo *proximityInfo, const int *xCoordinates,
        const int *yCoordinates, const int *codes, const int inputSize,
        Correction *correction) const {
    if (DEBUG_DICT) {
        AKLOGI("initSuggest");
        DUMP_WORD(codes, inputSize);
    }
    correction->initInputParams(proximityInfo, codes, inputSize, xCoordinates, yCoordinates);
    const int maxDepth = min(inputSize * MAX_DEPTH_MULTIPLIER, MAX_WORD_LENGTH);
    correction->initCorrection(proximityInfo, inputSize, maxDepth);
}

void UnigramDictionary::getOneWordSuggestions(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes,
        const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        const bool useFullEditDistance, const int inputSize,
        Correction *correction, WordsPriorityQueuePool *queuePool) const {
    initSuggestions(proximityInfo, xcoordinates, ycoordinates, codes, inputSize, correction);
    getSuggestionCandidates(useFullEditDistance, inputSize, bigramMap, bigramFilter, correction,
            queuePool, true /* doAutoCompletion */, DEFAULT_MAX_ERRORS, FIRST_WORD_INDEX);
}

void UnigramDictionary::getSuggestionCandidates(const bool useFullEditDistance,
        const int inputSize, const std::map<int, int> *bigramMap, const uint8_t *bigramFilter,
        Correction *correction, WordsPriorityQueuePool *queuePool,
        const bool doAutoCompletion, const int maxErrors, const int currentWordIndex) const {
    uint8_t totalTraverseCount = correction->pushAndGetTotalTraverseCount();
    if (DEBUG_DICT) {
        AKLOGI("Traverse count %d", totalTraverseCount);
    }
    if (totalTraverseCount > MULTIPLE_WORDS_SUGGESTION_MAX_TOTAL_TRAVERSE_COUNT) {
        if (DEBUG_DICT) {
            AKLOGI("Abort traversing %d", totalTraverseCount);
        }
        return;
    }
    // TODO: Remove setCorrectionParams
    correction->setCorrectionParams(0, 0, 0,
            -1 /* spaceProximityPos */, -1 /* missingSpacePos */, useFullEditDistance,
            doAutoCompletion, maxErrors);
    int rootPosition = ROOT_POS;
    // Get the number of children of root, then increment the position
    int childCount = BinaryFormat::getGroupCountAndForwardPointer(DICT_ROOT, &rootPosition);
    int outputIndex = 0;

    correction->initCorrectionState(rootPosition, childCount, (inputSize <= 0));

    // Depth first search
    while (outputIndex >= 0) {
        if (correction->initProcessState(outputIndex)) {
            int siblingPos = correction->getTreeSiblingPos(outputIndex);
            int firstChildPos;

            const bool needsToTraverseChildrenNodes = processCurrentNode(siblingPos,
                    bigramMap, bigramFilter, correction, &childCount, &firstChildPos, &siblingPos,
                    queuePool, currentWordIndex);
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

void UnigramDictionary::onTerminal(const int probability,
        const TerminalAttributes &terminalAttributes, Correction *correction,
        WordsPriorityQueuePool *queuePool, const bool addToMasterQueue,
        const int currentWordIndex) const {
    const int inputIndex = correction->getInputIndex();
    const bool addToSubQueue = inputIndex < SUB_QUEUE_MAX_COUNT;

    int wordLength;
    int *wordPointer;

    if ((currentWordIndex == FIRST_WORD_INDEX) && addToMasterQueue) {
        WordsPriorityQueue *masterQueue = queuePool->getMasterQueue();
        const int finalProbability =
                correction->getFinalProbability(probability, &wordPointer, &wordLength);

        if (0 != finalProbability && !terminalAttributes.isBlacklistedOrNotAWord()) {
            // If the probability is 0, we don't want to add this word. However we still
            // want to add its shortcuts (including a possible whitelist entry) if any.
            // Furthermore, if this is not a word (shortcut only for example) or a blacklisted
            // entry then we never want to suggest this.
            addWord(wordPointer, wordLength, finalProbability, masterQueue,
                    Dictionary::KIND_CORRECTION);
        }

        const int shortcutProbability = finalProbability > 0 ? finalProbability - 1 : 0;
        // Please note that the shortcut candidates will be added to the master queue only.
        TerminalAttributes::ShortcutIterator iterator = terminalAttributes.getShortcutIterator();
        while (iterator.hasNextShortcutTarget()) {
            // TODO: addWord only supports weak ordering, meaning we have no means
            // to control the order of the shortcuts relative to one another or to the word.
            // We need to either modulate the probability of each shortcut according
            // to its own shortcut probability or to make the queue
            // so that the insert order is protected inside the queue for words
            // with the same score. For the moment we use -1 to make sure the shortcut will
            // never be in front of the word.
            int shortcutTarget[MAX_WORD_LENGTH];
            int shortcutFrequency;
            const int shortcutTargetStringLength = iterator.getNextShortcutTarget(
                    MAX_WORD_LENGTH, shortcutTarget, &shortcutFrequency);
            int shortcutScore;
            int kind;
            if (shortcutFrequency == BinaryFormat::WHITELIST_SHORTCUT_PROBABILITY
                    && correction->sameAsTyped()) {
                shortcutScore = S_INT_MAX;
                kind = Dictionary::KIND_WHITELIST;
            } else {
                shortcutScore = shortcutProbability;
                kind = Dictionary::KIND_CORRECTION;
            }
            addWord(shortcutTarget, shortcutTargetStringLength, shortcutScore,
                    masterQueue, kind);
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
        const int finalProbability = correction->getFinalProbabilityForSubQueue(
                probability, &wordPointer, &wordLength, inputIndex);
        addWord(wordPointer, wordLength, finalProbability, subQueue, Dictionary::KIND_CORRECTION);
    }
}

int UnigramDictionary::getSubStringSuggestion(
        ProximityInfo *proximityInfo, const int *xcoordinates, const int *ycoordinates,
        const int *codes, const bool useFullEditDistance, Correction *correction,
        WordsPriorityQueuePool *queuePool, const int inputSize,
        const bool hasAutoCorrectionCandidate, const int currentWordIndex,
        const int inputWordStartPos, const int inputWordLength,
        const int outputWordStartPos, const bool isSpaceProximity, int *freqArray,
        int *wordLengthArray, int *outputWord, int *outputWordLength) const {
    if (inputWordLength > MULTIPLE_WORDS_SUGGESTION_MAX_WORD_LENGTH) {
        return FLAG_MULTIPLE_SUGGEST_ABORT;
    }

    /////////////////////////////////////////////
    // safety net for multiple word suggestion //
    // TODO: Remove this safety net            //
    /////////////////////////////////////////////
    int smallWordCount = 0;
    int singleLetterWordCount = 0;
    if (inputWordLength == 1) {
        ++singleLetterWordCount;
    }
    if (inputWordLength <= 2) {
        // small word == single letter or 2-letter word
        ++smallWordCount;
    }
    for (int i = 0; i < currentWordIndex; ++i) {
        const int length = wordLengthArray[i];
        if (length == 1) {
            ++singleLetterWordCount;
            // Safety net to avoid suggesting sequential single letter words
            if (i < (currentWordIndex - 1)) {
                if (wordLengthArray[i + 1] == 1) {
                    return FLAG_MULTIPLE_SUGGEST_ABORT;
                }
            } else if (inputWordLength == 1) {
                return FLAG_MULTIPLE_SUGGEST_ABORT;
            }
        }
        if (length <= 2) {
            ++smallWordCount;
        }
        // Safety net to avoid suggesting multiple words with many (4 or more, for now) small words
        if (singleLetterWordCount >= 3 || smallWordCount >= 4) {
            return FLAG_MULTIPLE_SUGGEST_ABORT;
        }
    }
    //////////////////////////////////////////////
    // TODO: Remove the safety net above        //
    //////////////////////////////////////////////

    int *tempOutputWord = 0;
    int nextWordLength = 0;
    // TODO: Optimize init suggestion
    initSuggestions(proximityInfo, xcoordinates, ycoordinates, codes,
            inputSize, correction);

    int word[MAX_WORD_LENGTH];
    int freq = getMostProbableWordLike(
            inputWordStartPos, inputWordLength, correction, word);
    if (freq > 0) {
        nextWordLength = inputWordLength;
        tempOutputWord = word;
    } else if (!hasAutoCorrectionCandidate) {
        if (inputWordStartPos > 0) {
            const int offset = inputWordStartPos;
            initSuggestions(proximityInfo, &xcoordinates[offset], &ycoordinates[offset],
                    codes + offset, inputWordLength, correction);
            queuePool->clearSubQueue(currentWordIndex);
            // TODO: pass the bigram list for substring suggestion
            getSuggestionCandidates(useFullEditDistance, inputWordLength,
                    0 /* bigramMap */, 0 /* bigramFilter */, correction, queuePool,
                    false /* doAutoCompletion */, MAX_ERRORS_FOR_TWO_WORDS, currentWordIndex);
            if (DEBUG_DICT) {
                if (currentWordIndex < MULTIPLE_WORDS_SUGGESTION_MAX_WORDS) {
                    AKLOGI("Dump word candidates(%d) %d", currentWordIndex, inputWordLength);
                    for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
                        queuePool->getSubQueue(currentWordIndex, i)->dumpTopWord();
                    }
                }
            }
        }
        WordsPriorityQueue *queue = queuePool->getSubQueue(currentWordIndex, inputWordLength);
        // TODO: Return the correct value depending on doAutoCompletion
        if (!queue || queue->size() <= 0) {
            return FLAG_MULTIPLE_SUGGEST_ABORT;
        }
        int score = 0;
        const float ns = queue->getHighestNormalizedScore(
                correction->getPrimaryInputWord(), inputWordLength,
                &tempOutputWord, &score, &nextWordLength);
        if (DEBUG_DICT) {
            AKLOGI("NS(%d) = %f, Score = %d", currentWordIndex, ns, score);
        }
        // Two words correction won't be done if the score of the first word doesn't exceed the
        // threshold.
        if (ns < TWO_WORDS_CORRECTION_WITH_OTHER_ERROR_THRESHOLD
                || nextWordLength < SUB_QUEUE_MIN_WORD_LENGTH) {
            return FLAG_MULTIPLE_SUGGEST_SKIP;
        }
        freq = score >> (nextWordLength + TWO_WORDS_PLUS_OTHER_ERROR_CORRECTION_DEMOTION_DIVIDER);
    }
    if (DEBUG_DICT) {
        AKLOGI("Freq(%d): %d, length: %d, input length: %d, input start: %d (%d)",
                currentWordIndex, freq, nextWordLength, inputWordLength, inputWordStartPos,
                (currentWordIndex > 0) ? wordLengthArray[0] : 0);
    }
    if (freq <= 0 || nextWordLength <= 0
            || MAX_WORD_LENGTH <= (outputWordStartPos + nextWordLength)) {
        return FLAG_MULTIPLE_SUGGEST_SKIP;
    }
    for (int i = 0; i < nextWordLength; ++i) {
        outputWord[outputWordStartPos + i] = tempOutputWord[i];
    }

    // Put output values
    freqArray[currentWordIndex] = freq;
    // TODO: put output length instead of input length
    wordLengthArray[currentWordIndex] = inputWordLength;
    const int tempOutputWordLength = outputWordStartPos + nextWordLength;
    if (outputWordLength) {
        *outputWordLength = tempOutputWordLength;
    }

    if ((inputWordStartPos + inputWordLength) < inputSize) {
        if (outputWordStartPos + nextWordLength >= MAX_WORD_LENGTH) {
            return FLAG_MULTIPLE_SUGGEST_SKIP;
        }
        outputWord[tempOutputWordLength] = KEYCODE_SPACE;
        if (outputWordLength) {
            ++*outputWordLength;
        }
    } else if (currentWordIndex >= 1) {
        // TODO: Handle 3 or more words
        const int pairFreq = correction->getFreqForSplitMultipleWords(
                freqArray, wordLengthArray, currentWordIndex + 1, isSpaceProximity, outputWord);
        if (DEBUG_DICT) {
            DUMP_WORD(outputWord, tempOutputWordLength);
            for (int i = 0; i < currentWordIndex + 1; ++i) {
                AKLOGI("Split %d,%d words: freq = %d, length = %d", i, currentWordIndex + 1,
                        freqArray[i], wordLengthArray[i]);
            }
            AKLOGI("Split two words: freq = %d, length = %d, %d, isSpace ? %d", pairFreq,
                    inputSize, tempOutputWordLength, isSpaceProximity);
        }
        addWord(outputWord, tempOutputWordLength, pairFreq, queuePool->getMasterQueue(),
                Dictionary::KIND_CORRECTION);
    }
    return FLAG_MULTIPLE_SUGGEST_CONTINUE;
}

void UnigramDictionary::getMultiWordsSuggestionRec(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes,
        const bool useFullEditDistance, const int inputSize, Correction *correction,
        WordsPriorityQueuePool *queuePool, const bool hasAutoCorrectionCandidate,
        const int startInputPos, const int startWordIndex, const int outputWordLength,
        int *freqArray, int *wordLengthArray, int *outputWord) const {
    if (startWordIndex >= (MULTIPLE_WORDS_SUGGESTION_MAX_WORDS - 1)) {
        // Return if the last word index
        return;
    }
    if (startWordIndex >= 1
            && (hasAutoCorrectionCandidate
                    || inputSize < MIN_INPUT_LENGTH_FOR_THREE_OR_MORE_WORDS_CORRECTION)) {
        // Do not suggest 3+ words if already has auto correction candidate
        return;
    }
    for (int i = startInputPos + 1; i < inputSize; ++i) {
        if (DEBUG_CORRECTION_FREQ) {
            AKLOGI("Multi words(%d), start in %d sep %d start out %d",
                    startWordIndex, startInputPos, i, outputWordLength);
            DUMP_WORD(outputWord, outputWordLength);
        }
        int tempOutputWordLength = 0;
        // Current word
        int inputWordStartPos = startInputPos;
        int inputWordLength = i - startInputPos;
        const int suggestionFlag = getSubStringSuggestion(proximityInfo, xcoordinates, ycoordinates,
                codes, useFullEditDistance, correction, queuePool, inputSize,
                hasAutoCorrectionCandidate, startWordIndex, inputWordStartPos, inputWordLength,
                outputWordLength, true /* not used */, freqArray, wordLengthArray, outputWord,
                &tempOutputWordLength);
        if (suggestionFlag == FLAG_MULTIPLE_SUGGEST_ABORT) {
            // TODO: break here
            continue;
        } else if (suggestionFlag == FLAG_MULTIPLE_SUGGEST_SKIP) {
            continue;
        }

        if (DEBUG_CORRECTION_FREQ) {
            AKLOGI("Do missing space correction");
        }
        // Next word
        // Missing space
        inputWordStartPos = i;
        inputWordLength = inputSize - i;
        if (getSubStringSuggestion(proximityInfo, xcoordinates, ycoordinates, codes,
                useFullEditDistance, correction, queuePool, inputSize, hasAutoCorrectionCandidate,
                startWordIndex + 1, inputWordStartPos, inputWordLength, tempOutputWordLength,
                false /* missing space */, freqArray, wordLengthArray, outputWord, 0)
                        != FLAG_MULTIPLE_SUGGEST_CONTINUE) {
            getMultiWordsSuggestionRec(proximityInfo, xcoordinates, ycoordinates, codes,
                    useFullEditDistance, inputSize, correction, queuePool,
                    hasAutoCorrectionCandidate, inputWordStartPos, startWordIndex + 1,
                    tempOutputWordLength, freqArray, wordLengthArray, outputWord);
        }

        // Mistyped space
        ++inputWordStartPos;
        --inputWordLength;

        if (inputWordLength <= 0) {
            continue;
        }

        const int x = xcoordinates[inputWordStartPos - 1];
        const int y = ycoordinates[inputWordStartPos - 1];
        if (!proximityInfo->hasSpaceProximity(x, y)) {
            continue;
        }

        if (DEBUG_CORRECTION_FREQ) {
            AKLOGI("Do mistyped space correction");
        }
        getSubStringSuggestion(proximityInfo, xcoordinates, ycoordinates, codes,
                useFullEditDistance, correction, queuePool, inputSize, hasAutoCorrectionCandidate,
                startWordIndex + 1, inputWordStartPos, inputWordLength, tempOutputWordLength,
                true /* mistyped space */, freqArray, wordLengthArray, outputWord, 0);
    }
}

void UnigramDictionary::getSplitMultipleWordsSuggestions(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes,
        const bool useFullEditDistance, const int inputSize,
        Correction *correction, WordsPriorityQueuePool *queuePool,
        const bool hasAutoCorrectionCandidate) const {
    if (inputSize >= MAX_WORD_LENGTH) return;
    if (DEBUG_DICT) {
        AKLOGI("--- Suggest multiple words");
    }

    // Allocating fixed length array on stack
    int outputWord[MAX_WORD_LENGTH];
    int freqArray[MULTIPLE_WORDS_SUGGESTION_MAX_WORDS];
    int wordLengthArray[MULTIPLE_WORDS_SUGGESTION_MAX_WORDS];
    const int outputWordLength = 0;
    const int startInputPos = 0;
    const int startWordIndex = 0;
    getMultiWordsSuggestionRec(proximityInfo, xcoordinates, ycoordinates, codes,
            useFullEditDistance, inputSize, correction, queuePool, hasAutoCorrectionCandidate,
            startInputPos, startWordIndex, outputWordLength, freqArray, wordLengthArray,
            outputWord);
}

// Wrapper for getMostProbableWordLikeInner, which matches it to the previous
// interface.
int UnigramDictionary::getMostProbableWordLike(const int startInputIndex, const int inputSize,
        Correction *correction, int *word) const {
    int inWord[inputSize];
    for (int i = 0; i < inputSize; ++i) {
        inWord[i] = correction->getPrimaryCodePointAt(startInputIndex + i);
    }
    return getMostProbableWordLikeInner(inWord, inputSize, word);
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
        const uint8_t *const root, const int startPos, const int *const inWord,
        const int startInputIndex, const int inputSize, int *outNewWord, int *outInputIndex,
        int *outPos) {
    const bool hasMultipleChars = (0 != (BinaryFormat::FLAG_HAS_MULTIPLE_CHARS & flags));
    int pos = startPos;
    int codePoint = BinaryFormat::getCodePointAndForwardPointer(root, &pos);
    int baseChar = toBaseLowerCase(codePoint);
    const int wChar = toBaseLowerCase(inWord[startInputIndex]);

    if (baseChar != wChar) {
        *outPos = hasMultipleChars ? BinaryFormat::skipOtherCharacters(root, pos) : pos;
        *outInputIndex = startInputIndex;
        return false;
    }
    int inputIndex = startInputIndex;
    outNewWord[inputIndex] = codePoint;
    if (hasMultipleChars) {
        codePoint = BinaryFormat::getCodePointAndForwardPointer(root, &pos);
        while (NOT_A_CODE_POINT != codePoint) {
            baseChar = toBaseLowerCase(codePoint);
            if (inputIndex + 1 >= inputSize || toBaseLowerCase(inWord[++inputIndex]) != baseChar) {
                *outPos = BinaryFormat::skipOtherCharacters(root, pos);
                *outInputIndex = startInputIndex;
                return false;
            }
            outNewWord[inputIndex] = codePoint;
            codePoint = BinaryFormat::getCodePointAndForwardPointer(root, &pos);
        }
    }
    *outInputIndex = inputIndex + 1;
    *outPos = pos;
    return true;
}

// This function is invoked when a word like the word searched for is found.
// It will compare the probability to the max probability, and if greater, will
// copy the word into the output buffer. In output value maxFreq, it will
// write the new maximum probability if it changed.
static inline void onTerminalWordLike(const int freq, int *newWord, const int length, int *outWord,
        int *maxFreq) {
    if (freq > *maxFreq) {
        for (int q = 0; q < length; ++q) {
            outWord[q] = newWord[q];
        }
        outWord[length] = 0;
        *maxFreq = freq;
    }
}

// Will find the highest probability of the words like the one passed as an argument,
// that is, everything that only differs by case/accents.
int UnigramDictionary::getMostProbableWordLikeInner(const int *const inWord, const int inputSize,
        int *outWord) const {
    int newWord[MAX_WORD_LENGTH];
    int depth = 0;
    int maxFreq = -1;
    const uint8_t *const root = DICT_ROOT;
    int stackChildCount[MAX_WORD_LENGTH];
    int stackInputIndex[MAX_WORD_LENGTH];
    int stackSiblingPos[MAX_WORD_LENGTH];

    int startPos = 0;
    stackChildCount[0] = BinaryFormat::getGroupCountAndForwardPointer(root, &startPos);
    stackInputIndex[0] = 0;
    stackSiblingPos[0] = startPos;
    while (depth >= 0) {
        const int charGroupCount = stackChildCount[depth];
        int pos = stackSiblingPos[depth];
        for (int charGroupIndex = charGroupCount - 1; charGroupIndex >= 0; --charGroupIndex) {
            int inputIndex = stackInputIndex[depth];
            const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
            // Test whether all chars in this group match with the word we are searching for. If so,
            // we want to traverse its children (or if the inputSize match, evaluate its
            // probability). Note that this function will output the position regardless, but will
            // only write into inputIndex if there is a match.
            const bool isAlike = testCharGroupForContinuedLikeness(flags, root, pos, inWord,
                    inputIndex, inputSize, newWord, &inputIndex, &pos);
            if (isAlike && (!(BinaryFormat::FLAG_IS_NOT_A_WORD & flags))
                    && (BinaryFormat::FLAG_IS_TERMINAL & flags) && (inputIndex == inputSize)) {
                const int probability =
                        BinaryFormat::readProbabilityWithoutMovingPointer(root, pos);
                onTerminalWordLike(probability, newWord, inputIndex, outWord, &maxFreq);
            }
            pos = BinaryFormat::skipProbability(flags, pos);
            const int siblingPos = BinaryFormat::skipChildrenPosAndAttributes(root, flags, pos);
            const int childrenNodePos = BinaryFormat::readChildrenPosition(root, flags, pos);
            // If we had a match and the word has children, we want to traverse them. We don't have
            // to traverse words longer than the one we are searching for, since they will not match
            // anyway, so don't traverse unless inputIndex < inputSize.
            if (isAlike && (-1 != childrenNodePos) && (inputIndex < inputSize)) {
                // Save position for this depth, to get back to this once children are done
                stackChildCount[depth] = charGroupIndex;
                stackSiblingPos[depth] = siblingPos;
                // Prepare stack values for next depth
                ++depth;
                int childrenPos = childrenNodePos;
                stackChildCount[depth] =
                        BinaryFormat::getGroupCountAndForwardPointer(root, &childrenPos);
                stackSiblingPos[depth] = childrenPos;
                stackInputIndex[depth] = inputIndex;
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

int UnigramDictionary::getProbability(const int *const inWord, const int length) const {
    const uint8_t *const root = DICT_ROOT;
    int pos = BinaryFormat::getTerminalPosition(root, inWord, length,
            false /* forceLowerCaseSearch */);
    if (NOT_VALID_WORD == pos) {
        return NOT_A_PROBABILITY;
    }
    const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
    if (flags & (BinaryFormat::FLAG_IS_BLACKLISTED | BinaryFormat::FLAG_IS_NOT_A_WORD)) {
        // If this is not a word, or if it's a blacklisted entry, it should behave as
        // having no probability outside of the suggestion process (where it should be used
        // for shortcuts).
        return NOT_A_PROBABILITY;
    }
    const bool hasMultipleChars = (0 != (BinaryFormat::FLAG_HAS_MULTIPLE_CHARS & flags));
    if (hasMultipleChars) {
        pos = BinaryFormat::skipOtherCharacters(root, pos);
    } else {
        BinaryFormat::getCodePointAndForwardPointer(DICT_ROOT, &pos);
    }
    const int unigramProbability = BinaryFormat::readProbabilityWithoutMovingPointer(root, pos);
    return unigramProbability;
}

// TODO: remove this function.
int UnigramDictionary::getBigramPosition(int pos, int *word, int offset, int length) const {
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
bool UnigramDictionary::processCurrentNode(const int initialPos,
        const std::map<int, int> *bigramMap, const uint8_t *bigramFilter, Correction *correction,
        int *newCount, int *newChildrenPosition, int *nextSiblingPosition,
        WordsPriorityQueuePool *queuePool, const int currentWordIndex) const {
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
    const bool hasMultipleChars = (0 != (BinaryFormat::FLAG_HAS_MULTIPLE_CHARS & flags));
    const bool isTerminalNode = (0 != (BinaryFormat::FLAG_IS_TERMINAL & flags));

    bool needsToInvokeOnTerminal = false;

    // This gets only ONE character from the stream. Next there will be:
    // if FLAG_HAS_MULTIPLE CHARS: the other characters of the same node
    // else if FLAG_IS_TERMINAL: the probability
    // else if MASK_GROUP_ADDRESS_TYPE is not NONE: the children address
    // Note that you can't have a node that both is not a terminal and has no children.
    int c = BinaryFormat::getCodePointAndForwardPointer(DICT_ROOT, &pos);
    ASSERT(NOT_A_CODE_POINT != c);

    // We are going to loop through each character and make it look like it's a different
    // node each time. To do that, we will process characters in this node in order until
    // we find the character terminator. This is signalled by getCodePoint* returning
    // NOT_A_CODE_POINT.
    // As a special case, if there is only one character in this node, we must not read the
    // next bytes so we will simulate the NOT_A_CODE_POINT return by testing the flags.
    // This way, each loop run will look like a "virtual node".
    do {
        // We prefetch the next char. If 'c' is the last char of this node, we will have
        // NOT_A_CODE_POINT in the next char. From this we can decide whether this virtual node
        // should behave as a terminal or not and whether we have children.
        const int nextc = hasMultipleChars
                ? BinaryFormat::getCodePointAndForwardPointer(DICT_ROOT, &pos) : NOT_A_CODE_POINT;
        const bool isLastChar = (NOT_A_CODE_POINT == nextc);
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
            // characters in this node, the probability if it's there, read the next sibling
            // position to output it, then return false.
            // We don't have to output other values because we return false, as in
            // "don't traverse children".
            if (!isLastChar) {
                pos = BinaryFormat::skipOtherCharacters(DICT_ROOT, pos);
            }
            pos = BinaryFormat::skipProbability(flags, pos);
            *nextSiblingPosition =
                    BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
            return false;
        }

        // Prepare for the next character. Promote the prefetched char to current char - the loop
        // will take care of prefetching the next. If we finally found our last char, nextc will
        // contain NOT_A_CODE_POINT.
        c = nextc;
    } while (NOT_A_CODE_POINT != c);

    if (isTerminalNode) {
        // The probability should be here, because we come here only if this is actually
        // a terminal node, and we are on its last char.
        const int unigramProbability =
                BinaryFormat::readProbabilityWithoutMovingPointer(DICT_ROOT, pos);
        const int childrenAddressPos = BinaryFormat::skipProbability(flags, pos);
        const int attributesPos = BinaryFormat::skipChildrenPosition(flags, childrenAddressPos);
        TerminalAttributes terminalAttributes(DICT_ROOT, flags, attributesPos);
        // bigramMap contains the bigram frequencies indexed by addresses for fast lookup.
        // bigramFilter is a bloom filter of said frequencies for even faster rejection.
        const int probability = BinaryFormat::getProbability(initialPos, bigramMap, bigramFilter,
                unigramProbability);
        onTerminal(probability, terminalAttributes, correction, queuePool, needsToInvokeOnTerminal,
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
            pos = BinaryFormat::skipProbability(flags, pos);
            *nextSiblingPosition =
                    BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
            return false;
        }

        // Optimization: Prune out words that are too long compared to how much was typed.
        if (correction->needsToPrune()) {
            pos = BinaryFormat::skipProbability(flags, pos);
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
    ASSERT(BinaryFormat::hasChildrenInFlags(flags));

    // If this node was a terminal it still has the probability under the pointer (it may have been
    // read, but not skipped - see readProbabilityWithoutMovingPointer).
    // Next come the children position, then possibly attributes (attributes are bigrams only for
    // now, maybe something related to shortcuts in the future).
    // Once this is read, we still need to output the number of nodes in the immediate children of
    // this node, so we read and output it before returning true, as in "please traverse children".
    pos = BinaryFormat::skipProbability(flags, pos);
    int childrenPos = BinaryFormat::readChildrenPosition(DICT_ROOT, flags, pos);
    *nextSiblingPosition = BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
    *newCount = BinaryFormat::getGroupCountAndForwardPointer(DICT_ROOT, &childrenPos);
    *newChildrenPosition = childrenPos;
    return true;
}
} // namespace latinime
