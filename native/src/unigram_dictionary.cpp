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

#ifdef NEW_DICTIONARY_FORMAT
#include "binary_format.h"
#endif // NEW_DICTIONARY_FORMAT

namespace latinime {

const UnigramDictionary::digraph_t UnigramDictionary::GERMAN_UMLAUT_DIGRAPHS[] =
        { { 'a', 'e' },
        { 'o', 'e' },
        { 'u', 'e' } };

// TODO: check the header
UnigramDictionary::UnigramDictionary(const uint8_t* const streamStart, int typedLetterMultiplier,
        int fullWordMultiplier, int maxWordLength, int maxWords, int maxProximityChars,
        const bool isLatestDictVersion)
#ifndef NEW_DICTIONARY_FORMAT
    : DICT_ROOT(streamStart),
#else // NEW_DICTIONARY_FORMAT
    : DICT_ROOT(streamStart + NEW_DICTIONARY_HEADER_SIZE),
#endif // NEW_DICTIONARY_FORMAT
    MAX_WORD_LENGTH(maxWordLength), MAX_WORDS(maxWords),
    MAX_PROXIMITY_CHARS(maxProximityChars), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    TYPED_LETTER_MULTIPLIER(typedLetterMultiplier), FULL_WORD_MULTIPLIER(fullWordMultiplier),
#ifndef NEW_DICTIONARY_FORMAT
    ROOT_POS(isLatestDictVersion ? DICTIONARY_HEADER_SIZE : 0),
#else // NEW_DICTIONARY_FORMAT
      // TODO : remove this variable.
    ROOT_POS(0),
#endif // NEW_DICTIONARY_FORMAT
    BYTES_IN_ONE_CHAR(MAX_PROXIMITY_CHARS * sizeof(int)),
    MAX_UMLAUT_SEARCH_DEPTH(DEFAULT_MAX_UMLAUT_SEARCH_DEPTH) {
    if (DEBUG_DICT) {
        LOGI("UnigramDictionary - constructor");
    }
}

UnigramDictionary::~UnigramDictionary() {}

static inline unsigned int getCodesBufferSize(const int* codes, const int codesSize,
        const int MAX_PROXIMITY_CHARS) {
    return sizeof(*codes) * MAX_PROXIMITY_CHARS * codesSize;
}

bool UnigramDictionary::isDigraph(const int* codes, const int i, const int codesSize) const {

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
        const int *xcoordinates, const int* ycoordinates, const int *codesBuffer,
        const int codesBufferSize, const int flags, const int* codesSrc, const int codesRemain,
        const int currentDepth, int* codesDest, unsigned short* outWords, int* frequencies) {

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
                        currentDepth + 1, codesDest + i * MAX_PROXIMITY_CHARS, outWords,
                        frequencies);

                // Copy the second char of the digraph in place, then continue processing on
                // the remaining part of the word.
                // In our example, after "pru" in the buffer copy the "e", and continue on "fen"
                memcpy(codesDest + i * MAX_PROXIMITY_CHARS, codesSrc + i * MAX_PROXIMITY_CHARS,
                        BYTES_IN_ONE_CHAR);
                getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates,
                        codesBuffer, codesBufferSize, flags, codesSrc + i * MAX_PROXIMITY_CHARS,
                        codesRemain - i, currentDepth + 1, codesDest + i * MAX_PROXIMITY_CHARS,
                        outWords, frequencies);
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
            (codesDest - codesBuffer) / MAX_PROXIMITY_CHARS + codesRemain, outWords, frequencies);
}

int UnigramDictionary::getSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
        const int *ycoordinates, const int *codes, const int codesSize, const int flags,
        unsigned short *outWords, int *frequencies) {

    if (REQUIRES_GERMAN_UMLAUT_PROCESSING & flags)
    { // Incrementally tune the word and try all possibilities
        int codesBuffer[getCodesBufferSize(codes, codesSize, MAX_PROXIMITY_CHARS)];
        getWordWithDigraphSuggestionsRec(proximityInfo, xcoordinates, ycoordinates, codesBuffer,
                codesSize, flags, codes, codesSize, 0, codesBuffer, outWords, frequencies);
    } else { // Normal processing
        getWordSuggestions(proximityInfo, xcoordinates, ycoordinates, codes, codesSize,
                outWords, frequencies);
    }

    PROF_START(20);
    // Get the word count
    int suggestedWordsCount = 0;
    while (suggestedWordsCount < MAX_WORDS && mFrequencies[suggestedWordsCount] > 0) {
        suggestedWordsCount++;
    }

    if (DEBUG_DICT) {
        LOGI("Returning %d words", suggestedWordsCount);
        /// Print the returned words
        for (int j = 0; j < suggestedWordsCount; ++j) {
#ifdef FLAG_DBG
            short unsigned int* w = mOutputChars + j * MAX_WORD_LENGTH;
            char s[MAX_WORD_LENGTH];
            for (int i = 0; i <= MAX_WORD_LENGTH; i++) s[i] = w[i];
            LOGI("%s %i", s, mFrequencies[j]);
#endif
        }
        LOGI("Next letters: ");
        for (int k = 0; k < NEXT_LETTERS_SIZE; k++) {
            if (mNextLettersFrequency[k] > 0) {
                LOGI("%c = %d,", k, mNextLettersFrequency[k]);
            }
        }
    }
    PROF_END(20);
    PROF_CLOSE;
    return suggestedWordsCount;
}

void UnigramDictionary::getWordSuggestions(ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes, const int codesSize,
        unsigned short *outWords, int *frequencies) {

    PROF_OPEN;
    PROF_START(0);
    initSuggestions(
            proximityInfo, xcoordinates, ycoordinates, codes, codesSize, outWords, frequencies);
    if (DEBUG_DICT) assert(codesSize == mInputLength);

    const int MAX_DEPTH = min(mInputLength * MAX_DEPTH_MULTIPLIER, MAX_WORD_LENGTH);
    PROF_END(0);

    PROF_START(1);
    getSuggestionCandidates(-1, -1, -1, mNextLettersFrequency, NEXT_LETTERS_SIZE, MAX_DEPTH);
    PROF_END(1);

    PROF_START(2);
    // Suggestion with missing character
    if (SUGGEST_WORDS_WITH_MISSING_CHARACTER) {
        for (int i = 0; i < codesSize; ++i) {
            if (DEBUG_DICT) {
                LOGI("--- Suggest missing characters %d", i);
            }
            getSuggestionCandidates(i, -1, -1, NULL, 0, MAX_DEPTH);
        }
    }
    PROF_END(2);

    PROF_START(3);
    // Suggestion with excessive character
    if (SUGGEST_WORDS_WITH_EXCESSIVE_CHARACTER
            && mInputLength >= MIN_USER_TYPED_LENGTH_FOR_EXCESSIVE_CHARACTER_SUGGESTION) {
        for (int i = 0; i < codesSize; ++i) {
            if (DEBUG_DICT) {
                LOGI("--- Suggest excessive characters %d", i);
            }
            getSuggestionCandidates(-1, i, -1, NULL, 0, MAX_DEPTH);
        }
    }
    PROF_END(3);

    PROF_START(4);
    // Suggestion with transposed characters
    // Only suggest words that length is mInputLength
    if (SUGGEST_WORDS_WITH_TRANSPOSED_CHARACTERS) {
        for (int i = 0; i < codesSize; ++i) {
            if (DEBUG_DICT) {
                LOGI("--- Suggest transposed characters %d", i);
            }
            getSuggestionCandidates(-1, -1, i, NULL, 0, mInputLength - 1);
        }
    }
    PROF_END(4);

    PROF_START(5);
    // Suggestions with missing space
    if (SUGGEST_WORDS_WITH_MISSING_SPACE_CHARACTER
            && mInputLength >= MIN_USER_TYPED_LENGTH_FOR_MISSING_SPACE_SUGGESTION) {
        for (int i = 1; i < codesSize; ++i) {
            if (DEBUG_DICT) {
                LOGI("--- Suggest missing space characters %d", i);
            }
            getMissingSpaceWords(mInputLength, i);
        }
    }
    PROF_END(5);

    PROF_START(6);
    if (SUGGEST_WORDS_WITH_SPACE_PROXIMITY && proximityInfo) {
        // The first and last "mistyped spaces" are taken care of by excessive character handling
        for (int i = 1; i < codesSize - 1; ++i) {
            if (DEBUG_DICT) {
                LOGI("--- Suggest words with proximity space %d", i);
            }
            const int x = xcoordinates[i];
            const int y = ycoordinates[i];
            if (DEBUG_PROXIMITY_INFO) {
                LOGI("Input[%d] x = %d, y = %d, has space proximity = %d",
                        i, x, y, proximityInfo->hasSpaceProximity(x, y));
            }
            if (proximityInfo->hasSpaceProximity(x, y)) {
                getMistypedSpaceWords(mInputLength, i);
            }
        }
    }
    PROF_END(6);
}

void UnigramDictionary::initSuggestions(ProximityInfo *proximityInfo, const int *xcoordinates,
        const int *ycoordinates, const int *codes, const int codesSize,
        unsigned short *outWords, int *frequencies) {
    if (DEBUG_DICT) {
        LOGI("initSuggest");
    }
    mFrequencies = frequencies;
    mOutputChars = outWords;
    mInputLength = codesSize;
    mMaxEditDistance = mInputLength < 5 ? 2 : mInputLength / 2;
    proximityInfo->setInputParams(codes, codesSize);
    mProximityInfo = proximityInfo;
}

static inline void registerNextLetter(unsigned short c, int *nextLetters, int nextLettersSize) {
    if (c < nextLettersSize) {
        nextLetters[c]++;
    }
}

// TODO: We need to optimize addWord by using STL or something
// TODO: This needs to take an const unsigned short* and not tinker with its contents
bool UnigramDictionary::addWord(unsigned short *word, int length, int frequency) {
    word[length] = 0;
    if (DEBUG_DICT && DEBUG_SHOW_FOUND_WORD) {
#ifdef FLAG_DBG
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        LOGI("Found word = %s, freq = %d", s, frequency);
#endif
    }
    if (length > MAX_WORD_LENGTH) {
        if (DEBUG_DICT) {
            LOGI("Exceeded max word length.");
        }
        return false;
    }

    // Find the right insertion point
    int insertAt = 0;
    while (insertAt < MAX_WORDS) {
        // TODO: How should we sort words with the same frequency?
        if (frequency > mFrequencies[insertAt]) {
            break;
        }
        insertAt++;
    }
    if (insertAt < MAX_WORDS) {
        if (DEBUG_DICT) {
#ifdef FLAG_DBG
            char s[length + 1];
            for (int i = 0; i <= length; i++) s[i] = word[i];
            LOGI("Added word = %s, freq = %d, %d", s, frequency, S_INT_MAX);
#endif
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
        if (DEBUG_DICT) {
            LOGI("Added word at %d", insertAt);
        }
        return true;
    }
    return false;
}

static const char QUOTE = '\'';
static const char SPACE = ' ';

void UnigramDictionary::getSuggestionCandidates(const int skipPos,
        const int excessivePos, const int transposedPos, int *nextLetters,
        const int nextLettersSize, const int maxDepth) {
    if (DEBUG_DICT) {
        LOGI("getSuggestionCandidates %d", maxDepth);
        assert(transposedPos + 1 < mInputLength);
        assert(excessivePos < mInputLength);
        assert(missingPos < mInputLength);
    }
    int rootPosition = ROOT_POS;
    // Get the number of children of root, then increment the position
    int childCount = Dictionary::getCount(DICT_ROOT, &rootPosition);
    int depth = 0;

    mStackChildCount[0] = childCount;
    mStackTraverseAll[0] = (mInputLength <= 0);
    mStackNodeFreq[0] = 1;
    mStackInputIndex[0] = 0;
    mStackDiffs[0] = 0;
    mStackSiblingPos[0] = rootPosition;
    mStackOutputIndex[0] = 0;

    // Depth first search
    while (depth >= 0) {
        if (mStackChildCount[depth] > 0) {
            --mStackChildCount[depth];
            bool traverseAllNodes = mStackTraverseAll[depth];
            int matchWeight = mStackNodeFreq[depth];
            int inputIndex = mStackInputIndex[depth];
            int diffs = mStackDiffs[depth];
            int siblingPos = mStackSiblingPos[depth];
            int outputIndex = mStackOutputIndex[depth];
            int firstChildPos;
            // depth will never be greater than maxDepth because in that case,
            // needsToTraverseChildrenNodes should be false
            const bool needsToTraverseChildrenNodes = processCurrentNode(siblingPos, outputIndex,
                    maxDepth, traverseAllNodes, matchWeight, inputIndex, diffs, skipPos,
                    excessivePos, transposedPos, nextLetters, nextLettersSize, &childCount,
                    &firstChildPos, &traverseAllNodes, &matchWeight, &inputIndex, &diffs,
                    &siblingPos, &outputIndex);
            // Update next sibling pos
            mStackSiblingPos[depth] = siblingPos;
            if (needsToTraverseChildrenNodes) {
                // Goes to child node
                ++depth;
                mStackChildCount[depth] = childCount;
                mStackTraverseAll[depth] = traverseAllNodes;
                mStackNodeFreq[depth] = matchWeight;
                mStackInputIndex[depth] = inputIndex;
                mStackDiffs[depth] = diffs;
                mStackSiblingPos[depth] = firstChildPos;
                mStackOutputIndex[depth] = outputIndex;
            }
        } else {
            // Goes to parent sibling node
            --depth;
        }
    }
}

static const int TWO_31ST_DIV_255 = S_INT_MAX / 255;
static inline int capped255MultForFullMatchAccentsOrCapitalizationDifference(const int num) {
    return (num < TWO_31ST_DIV_255 ? 255 * num : S_INT_MAX);
}

static const int TWO_31ST_DIV_2 = S_INT_MAX / 2;
inline static void multiplyIntCapped(const int multiplier, int *base) {
    const int temp = *base;
    if (temp != S_INT_MAX) {
        // Branch if multiplier == 2 for the optimization
        if (multiplier == 2) {
            *base = TWO_31ST_DIV_2 >= temp ? temp << 1 : S_INT_MAX;
        } else {
            const int tempRetval = temp * multiplier;
            *base = tempRetval >= temp ? tempRetval : S_INT_MAX;
        }
    }
}

inline static int powerIntCapped(const int base, const int n) {
    if (base == 2) {
        return n < 31 ? 1 << n : S_INT_MAX;
    } else {
        int ret = base;
        for (int i = 1; i < n; ++i) multiplyIntCapped(base, &ret);
        return ret;
    }
}

inline static void multiplyRate(const int rate, int *freq) {
    if (*freq != S_INT_MAX) {
        if (*freq > 1000000) {
            *freq /= 100;
            multiplyIntCapped(rate, freq);
        } else {
            multiplyIntCapped(rate, freq);
            *freq /= 100;
        }
    }
}

inline static int calcFreqForSplitTwoWords(
        const int typedLetterMultiplier, const int firstWordLength, const int secondWordLength,
        const int firstFreq, const int secondFreq, const bool isSpaceProximity) {
    if (firstWordLength == 0 || secondWordLength == 0) {
        return 0;
    }
    const int firstDemotionRate = 100 - 100 / (firstWordLength + 1);
    int tempFirstFreq = firstFreq;
    multiplyRate(firstDemotionRate, &tempFirstFreq);

    const int secondDemotionRate = 100 - 100 / (secondWordLength + 1);
    int tempSecondFreq = secondFreq;
    multiplyRate(secondDemotionRate, &tempSecondFreq);

    const int totalLength = firstWordLength + secondWordLength;

    // Promote pairFreq with multiplying by 2, because the word length is the same as the typed
    // length.
    int totalFreq = tempFirstFreq + tempSecondFreq;

    // This is a workaround to try offsetting the not-enough-demotion which will be done in
    // calcNormalizedScore in Utils.java.
    // In calcNormalizedScore the score will be demoted by (1 - 1 / length)
    // but we demoted only (1 - 1 / (length + 1)) so we will additionally adjust freq by
    // (1 - 1 / length) / (1 - 1 / (length + 1)) = (1 - 1 / (length * length))
    const int normalizedScoreNotEnoughDemotionAdjustment = 100 - 100 / (totalLength * totalLength);
    multiplyRate(normalizedScoreNotEnoughDemotionAdjustment, &totalFreq);

    // At this moment, totalFreq is calculated by the following formula:
    // (firstFreq * (1 - 1 / (firstWordLength + 1)) + secondFreq * (1 - 1 / (secondWordLength + 1)))
    //        * (1 - 1 / totalLength) / (1 - 1 / (totalLength + 1))

    multiplyIntCapped(powerIntCapped(typedLetterMultiplier, totalLength), &totalFreq);

    // This is another workaround to offset the demotion which will be done in
    // calcNormalizedScore in Utils.java.
    // In calcNormalizedScore the score will be demoted by (1 - 1 / length) so we have to promote
    // the same amount because we already have adjusted the synthetic freq of this "missing or
    // mistyped space" suggestion candidate above in this method.
    const int normalizedScoreDemotionRateOffset = (100 + 100 / totalLength);
    multiplyRate(normalizedScoreDemotionRateOffset, &totalFreq);

    if (isSpaceProximity) {
        // A word pair with one space proximity correction
        if (DEBUG_DICT) {
            LOGI("Found a word pair with space proximity correction.");
        }
        multiplyIntCapped(typedLetterMultiplier, &totalFreq);
        multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &totalFreq);
    }

    multiplyRate(WORDS_WITH_MISSING_SPACE_CHARACTER_DEMOTION_RATE, &totalFreq);
    return totalFreq;
}

bool UnigramDictionary::getMissingSpaceWords(const int inputLength, const int missingSpacePos) {
    return getSplitTwoWordsSuggestion(
            inputLength, 0, missingSpacePos, missingSpacePos, inputLength - missingSpacePos, false);
}

bool UnigramDictionary::getMistypedSpaceWords(const int inputLength, const int spaceProximityPos) {
    return getSplitTwoWordsSuggestion(
            inputLength, 0, spaceProximityPos, spaceProximityPos + 1,
            inputLength - spaceProximityPos - 1, true);
}

inline int UnigramDictionary::calculateFinalFreq(const int inputIndex, const int depth,
        const int matchWeight, const int skipPos, const int excessivePos, const int transposedPos,
        const int freq, const bool sameLength) const {
    // TODO: Demote by edit distance
    int finalFreq = freq * matchWeight;
    if (skipPos >= 0) {
        if (mInputLength >= 2) {
            const int demotionRate = WORDS_WITH_MISSING_CHARACTER_DEMOTION_RATE
                    * (10 * mInputLength - WORDS_WITH_MISSING_CHARACTER_DEMOTION_START_POS_10X)
                    / (10 * mInputLength
                            - WORDS_WITH_MISSING_CHARACTER_DEMOTION_START_POS_10X + 10);
            if (DEBUG_DICT_FULL) {
                LOGI("Demotion rate for missing character is %d.", demotionRate);
            }
            multiplyRate(demotionRate, &finalFreq);
        } else {
            finalFreq = 0;
        }
    }
    if (transposedPos >= 0) multiplyRate(
            WORDS_WITH_TRANSPOSED_CHARACTERS_DEMOTION_RATE, &finalFreq);
    if (excessivePos >= 0) {
        multiplyRate(WORDS_WITH_EXCESSIVE_CHARACTER_DEMOTION_RATE, &finalFreq);
        if (!mProximityInfo->existsAdjacentProximityChars(inputIndex)) {
            // If an excessive character is not adjacent to the left char or the right char,
            // we will demote this word.
            multiplyRate(WORDS_WITH_EXCESSIVE_CHARACTER_OUT_OF_PROXIMITY_DEMOTION_RATE, &finalFreq);
        }
    }
    int lengthFreq = TYPED_LETTER_MULTIPLIER;
    multiplyIntCapped(powerIntCapped(TYPED_LETTER_MULTIPLIER, depth), &lengthFreq);
    if (lengthFreq == matchWeight) {
        // Full exact match
        if (depth > 1) {
            if (DEBUG_DICT) {
                LOGI("Found full matched word.");
            }
            multiplyRate(FULL_MATCHED_WORDS_PROMOTION_RATE, &finalFreq);
        }
        if (sameLength && transposedPos < 0 && skipPos < 0 && excessivePos < 0) {
            finalFreq = capped255MultForFullMatchAccentsOrCapitalizationDifference(finalFreq);
        }
    } else if (sameLength && transposedPos < 0 && skipPos < 0 && excessivePos < 0 && depth > 0) {
        // A word with proximity corrections
        if (DEBUG_DICT) {
            LOGI("Found one proximity correction.");
        }
        multiplyIntCapped(TYPED_LETTER_MULTIPLIER, &finalFreq);
        multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
    }
    if (DEBUG_DICT) {
        LOGI("calc: %d, %d", depth, sameLength);
    }
    if (sameLength) multiplyIntCapped(FULL_WORD_MULTIPLIER, &finalFreq);
    return finalFreq;
}

inline bool UnigramDictionary::needsToSkipCurrentNode(const unsigned short c,
        const int inputIndex, const int skipPos, const int depth) {
    const unsigned short userTypedChar = mProximityInfo->getPrimaryCharAt(inputIndex);
    // Skip the ' or other letter and continue deeper
    return (c == QUOTE && userTypedChar != QUOTE) || skipPos == depth;
}


inline void UnigramDictionary::onTerminal(unsigned short int* word, const int depth,
        const uint8_t* const root, const uint8_t flags, const int pos,
        const int inputIndex, const int matchWeight, const int skipPos,
        const int excessivePos, const int transposedPos, const int freq, const bool sameLength,
        int* nextLetters, const int nextLettersSize) {

    const bool isSameAsTyped = sameLength ? mProximityInfo->sameAsTyped(word, depth + 1) : false;
    if (isSameAsTyped) return;

    if (depth >= MIN_SUGGEST_DEPTH) {
        const int finalFreq = calculateFinalFreq(inputIndex, depth, matchWeight, skipPos,
                excessivePos, transposedPos, freq, sameLength);
        if (!isSameAsTyped)
            addWord(word, depth + 1, finalFreq);
    }

    if (sameLength && depth >= mInputLength && skipPos < 0) {
        registerNextLetter(word[mInputLength], nextLetters, nextLettersSize);
    }
}

bool UnigramDictionary::getSplitTwoWordsSuggestion(const int inputLength,
        const int firstWordStartPos, const int firstWordLength, const int secondWordStartPos,
        const int secondWordLength, const bool isSpaceProximity) {
    if (inputLength >= MAX_WORD_LENGTH) return false;
    if (0 >= firstWordLength || 0 >= secondWordLength || firstWordStartPos >= secondWordStartPos
            || firstWordStartPos < 0 || secondWordStartPos + secondWordLength > inputLength)
        return false;
    const int newWordLength = firstWordLength + secondWordLength + 1;
    // Allocating variable length array on stack
    unsigned short word[newWordLength];
    const int firstFreq = getMostFrequentWordLike(firstWordStartPos, firstWordLength, mWord);
    if (DEBUG_DICT) {
        LOGI("First freq: %d", firstFreq);
    }
    if (firstFreq <= 0) return false;

    for (int i = 0; i < firstWordLength; ++i) {
        word[i] = mWord[i];
    }

    const int secondFreq = getMostFrequentWordLike(secondWordStartPos, secondWordLength, mWord);
    if (DEBUG_DICT) {
        LOGI("Second  freq:  %d", secondFreq);
    }
    if (secondFreq <= 0) return false;

    word[firstWordLength] = SPACE;
    for (int i = (firstWordLength + 1); i < newWordLength; ++i) {
        word[i] = mWord[i - firstWordLength - 1];
    }

    int pairFreq = calcFreqForSplitTwoWords(TYPED_LETTER_MULTIPLIER, firstWordLength,
            secondWordLength, firstFreq, secondFreq, isSpaceProximity);
    if (DEBUG_DICT) {
        LOGI("Split two words:  %d, %d, %d, %d, %d", firstFreq, secondFreq, pairFreq, inputLength,
                TYPED_LETTER_MULTIPLIER);
    }
    addWord(word, newWordLength, pairFreq);
    return true;
}

#ifndef NEW_DICTIONARY_FORMAT
// The following functions will be entirely replaced with new implementations.
void UnigramDictionary::getWordsOld(const int initialPos, const int inputLength, const int skipPos,
        const int excessivePos, const int transposedPos,int *nextLetters,
        const int nextLettersSize) {
    int initialPosition = initialPos;
    const int count = Dictionary::getCount(DICT_ROOT, &initialPosition);
    getWordsRec(count, initialPosition, 0,
            min(inputLength * MAX_DEPTH_MULTIPLIER, MAX_WORD_LENGTH),
            mInputLength <= 0, 1, 0, 0, skipPos, excessivePos, transposedPos, nextLetters,
            nextLettersSize);
}

void UnigramDictionary::getWordsRec(const int childrenCount, const int pos, const int depth,
        const int maxDepth, const bool traverseAllNodes, const int matchWeight,
        const int inputIndex, const int diffs, const int skipPos, const int excessivePos,
        const int transposedPos, int *nextLetters, const int nextLettersSize) {
    int siblingPos = pos;
    for (int i = 0; i < childrenCount; ++i) {
        int newCount;
        int newChildPosition;
        bool newTraverseAllNodes;
        int newMatchRate;
        int newInputIndex;
        int newDiffs;
        int newSiblingPos;
        int newOutputIndex;
        const bool needsToTraverseChildrenNodes = processCurrentNode(siblingPos, depth, maxDepth,
                traverseAllNodes, matchWeight, inputIndex, diffs,
                skipPos, excessivePos, transposedPos,
                nextLetters, nextLettersSize,
                &newCount, &newChildPosition, &newTraverseAllNodes, &newMatchRate,
                &newInputIndex, &newDiffs, &newSiblingPos, &newOutputIndex);
        siblingPos = newSiblingPos;

        if (needsToTraverseChildrenNodes) {
            getWordsRec(newCount, newChildPosition, newOutputIndex, maxDepth, newTraverseAllNodes,
                    newMatchRate, newInputIndex, newDiffs, skipPos, excessivePos, transposedPos,
                    nextLetters, nextLettersSize);
        }
    }
}

inline int UnigramDictionary::getMostFrequentWordLike(const int startInputIndex,
        const int inputLength, unsigned short *word) {
    int pos = ROOT_POS;
    int count = Dictionary::getCount(DICT_ROOT, &pos);
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
#ifdef FLAG_DBG
                            char s[inputLength + 1];
                            for (int i = 0; i < inputLength; ++i) s[i] = word[i];
                            s[inputLength] = 0;
                            LOGI("New missing space word found: %d > %d (%s), %d, %d",
                                    newFreq, maxFreq, s, inputLength, depth);
#endif
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
    unsigned short c;
    *siblingPos = Dictionary::setDictionaryValues(DICT_ROOT, IS_LATEST_DICT_VERSION, firstChildPos,
            &c, newChildPosition, newTerminal, newFreq);
    const unsigned int inputC = mProximityInfo->getPrimaryCharAt(inputIndex);
    if (DEBUG_DICT) {
        assert(inputC <= U_SHORT_MAX);
    }
    const unsigned short baseLowerC = Dictionary::toBaseLowerCase(c);
    const bool matched = (inputC == baseLowerC || inputC == c);
    const bool hasChild = *newChildPosition != 0;
    if (matched) {
        word[depth] = c;
        if (DEBUG_DICT && DEBUG_NODE) {
            LOGI("Node(%c, %c)<%d>, %d, %d", inputC, c, matched, hasChild, *newFreq);
            if (*newTerminal) {
                LOGI("Terminal %d", *newFreq);
            }
        }
        if (hasChild) {
            *newCount = Dictionary::getCount(DICT_ROOT, newChildPosition);
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

// TODO: use uint32_t instead of unsigned short
bool UnigramDictionary::isValidWord(unsigned short *word, int length) {
    if (IS_LATEST_DICT_VERSION) {
        return (getBigramPosition(DICTIONARY_HEADER_SIZE, word, 0, length) != NOT_VALID_WORD);
    } else {
        return (getBigramPosition(0, word, 0, length) != NOT_VALID_WORD);
    }
}


// Require strict exact match.
int UnigramDictionary::getBigramPosition(int pos, unsigned short *word, int offset,
        int length) const {
    // returns address of bigram data of that word
    // return -99 if not found

    int count = Dictionary::getCount(DICT_ROOT, &pos);
    unsigned short currentChar = (unsigned short) word[offset];
    for (int j = 0; j < count; j++) {
        unsigned short c = Dictionary::getChar(DICT_ROOT, &pos);
        int terminal = Dictionary::getTerminal(DICT_ROOT, &pos);
        int childPos = Dictionary::getAddress(DICT_ROOT, &pos);
        if (c == currentChar) {
            if (offset == length - 1) {
                if (terminal) {
                    return (pos+1);
                }
            } else {
                if (childPos != 0) {
                    int t = getBigramPosition(childPos, word, offset + 1, length);
                    if (t > 0) {
                        return t;
                    }
                }
            }
        }
        if (terminal) {
            Dictionary::getFreq(DICT_ROOT, IS_LATEST_DICT_VERSION, &pos);
        }
        // There could be two instances of each alphabet - upper and lower case. So continue
        // looking ...
    }
    return NOT_VALID_WORD;
}

// The following functions will be modified.
inline bool UnigramDictionary::processCurrentNode(const int initialPos, const int initialDepth,
        const int maxDepth, const bool initialTraverseAllNodes, int matchWeight, int inputIndex,
        const int initialDiffs, const int skipPos, const int excessivePos, const int transposedPos,
        int *nextLetters, const int nextLettersSize, int *newCount, int *newChildPosition,
        bool *newTraverseAllNodes, int *newMatchRate, int *newInputIndex, int *newDiffs,
        int *nextSiblingPosition, int *nextOutputIndex) {
    if (DEBUG_DICT) {
        int inputCount = 0;
        if (skipPos >= 0) ++inputCount;
        if (excessivePos >= 0) ++inputCount;
        if (transposedPos >= 0) ++inputCount;
        assert(inputCount <= 1);
    }
    unsigned short c;
    int childPosition;
    bool terminal;
    int freq;
    bool isSameAsUserTypedLength = false;

    const int pos = initialPos;
    const int depth = initialDepth;
    const int traverseAllNodes = initialTraverseAllNodes;
    const int diffs = initialDiffs;

    const uint8_t flags = 0; // No flags for now

    if (excessivePos == depth && inputIndex < mInputLength - 1) ++inputIndex;

    *nextSiblingPosition = Dictionary::setDictionaryValues(DICT_ROOT, IS_LATEST_DICT_VERSION, pos,
            &c, &childPosition, &terminal, &freq);
    *nextOutputIndex = depth + 1;

    const bool needsToTraverseChildrenNodes = childPosition != 0;

    // If we are only doing traverseAllNodes, no need to look at the typed characters.
    if (traverseAllNodes || needsToSkipCurrentNode(c, inputIndex, skipPos, depth)) {
        mWord[depth] = c;
        if (traverseAllNodes && terminal) {
            onTerminal(mWord, depth, DICT_ROOT, flags, pos, inputIndex, matchWeight, skipPos,
                       excessivePos, transposedPos, freq, false, nextLetters, nextLettersSize);
        }
        if (!needsToTraverseChildrenNodes) return false;
        *newTraverseAllNodes = traverseAllNodes;
        *newMatchRate = matchWeight;
        *newDiffs = diffs;
        *newInputIndex = inputIndex;
    } else {
        int inputIndexForProximity = inputIndex;

        if (transposedPos >= 0) {
            if (inputIndex == transposedPos) ++inputIndexForProximity;
            if (inputIndex == (transposedPos + 1)) --inputIndexForProximity;
        }

        ProximityInfo::ProximityType matchedProximityCharId = mProximityInfo->getMatchedProximityId(
                inputIndexForProximity, c, skipPos, excessivePos, transposedPos);
        if (ProximityInfo::UNRELATED_CHAR == matchedProximityCharId) return false;
        mWord[depth] = c;
        // If inputIndex is greater than mInputLength, that means there is no
        // proximity chars. So, we don't need to check proximity.
        if (ProximityInfo::SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR == matchedProximityCharId) {
            multiplyIntCapped(TYPED_LETTER_MULTIPLIER, &matchWeight);
        }
        bool isSameAsUserTypedLength = mInputLength == inputIndex + 1
                || (excessivePos == mInputLength - 1 && inputIndex == mInputLength - 2);
        if (isSameAsUserTypedLength && terminal) {
            onTerminal(mWord, depth, DICT_ROOT, flags, pos, inputIndex, matchWeight, skipPos,
                    excessivePos, transposedPos, freq, true, nextLetters, nextLettersSize);
        }
        if (!needsToTraverseChildrenNodes) return false;
        // Start traversing all nodes after the index exceeds the user typed length
        *newTraverseAllNodes = isSameAsUserTypedLength;
        *newMatchRate = matchWeight;
        *newDiffs = diffs
                + ((ProximityInfo::NEAR_PROXIMITY_CHAR == matchedProximityCharId) ? 1 : 0);
        *newInputIndex = inputIndex + 1;
    }
    // Optimization: Prune out words that are too long compared to how much was typed.
    if (depth >= maxDepth || *newDiffs > mMaxEditDistance) {
        return false;
    }

    // If inputIndex is greater than mInputLength, that means there are no proximity chars.
    // TODO: Check if this can be isSameAsUserTypedLength only.
    if (isSameAsUserTypedLength || mInputLength <= *newInputIndex) {
        *newTraverseAllNodes = true;
    }
    // get the count of nodes and increment childAddress.
    *newCount = Dictionary::getCount(DICT_ROOT, &childPosition);
    *newChildPosition = childPosition;
    if (DEBUG_DICT) assert(needsToTraverseChildrenNodes);
    return needsToTraverseChildrenNodes;
}

#else // NEW_DICTIONARY_FORMAT

// Wrapper for getMostFrequentWordLikeInner, which matches it to the previous
// interface.
inline int UnigramDictionary::getMostFrequentWordLike(const int startInputIndex,
        const int inputLength, unsigned short *word) {
    uint16_t inWord[inputLength];

    for (int i = 0; i < inputLength; ++i) {
        inWord[i] = (uint16_t)mProximityInfo->getPrimaryCharAt(startInputIndex + i);
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
    int32_t baseChar = Dictionary::toBaseLowerCase(character);
    const uint16_t wChar = Dictionary::toBaseLowerCase(inWord[startInputIndex]);

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
            baseChar = Dictionary::toBaseLowerCase(character);
            if (Dictionary::toBaseLowerCase(inWord[++inputIndex]) != baseChar) {
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

    mStackChildCount[0] = root[0];
    mStackInputIndex[0] = 0;
    mStackSiblingPos[0] = 1;
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

// This function gets the frequency of the exact matching word in the dictionary.
// If no match is found, it returns NOT_VALID_WORD.
static inline int getFrequency(const uint8_t* const root, const uint16_t* const inWord,
        const int length) {
    int pos = 0;
    int wordPos = 0;

    while (true) {
        // If we already traversed the tree further than the word is long, there means
        // there was no match (or we would have found it).
        if (wordPos > length) return NOT_VALID_WORD;
        int charGroupCount = BinaryFormat::getGroupCountAndForwardPointer(root, &pos);
        const uint16_t wChar = inWord[wordPos];
        while (true) {
            // If there are no more character groups in this node, it means we could not
            // find a matching character for this depth, therefore there is no match.
            if (0 >= charGroupCount) return NOT_VALID_WORD;
            const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
            int32_t character = BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
            if (character == wChar) {
                // This is the correct node. Only one character group may start with the same
                // char within a node, so either we found our match in this node, or there is
                // no match and we can return NOT_VALID_WORD. So we will check all the characters
                // in this character group indeed does match.
                if (UnigramDictionary::FLAG_HAS_MULTIPLE_CHARS & flags) {
                    character = BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
                    while (NOT_A_CHARACTER != character) {
                        ++wordPos;
                        // If we shoot the length of the word we search for, or if we find a single
                        // character that does not match, as explained above, it means the word is
                        // not in the dictionary (by virtue of this chargroup being the only one to
                        // match the word on the first character, but not matching the whole word).
                        if (wordPos > length) return NOT_VALID_WORD;
                        if (inWord[wordPos] != character) return NOT_VALID_WORD;
                        character = BinaryFormat::getCharCodeAndForwardPointer(root, &pos);
                    }
                }
                // If we come here we know that so far, we do match. Either we are on a terminal
                // and we match the length, in which case we found it, or we traverse children.
                // If we don't match the length AND don't have children, then a word in the
                // dictionary fully matches a prefix of the searched word but not the full word.
                ++wordPos;
                if (UnigramDictionary::FLAG_IS_TERMINAL & flags) {
                    if (wordPos == length) {
                        return BinaryFormat::readFrequencyWithoutMovingPointer(root, pos);
                    }
                    pos = BinaryFormat::skipFrequency(UnigramDictionary::FLAG_IS_TERMINAL, pos);
                }
                if (UnigramDictionary::FLAG_GROUP_ADDRESS_TYPE_NOADDRESS
                        == (UnigramDictionary::MASK_GROUP_ADDRESS_TYPE & flags)) {
                    return NOT_VALID_WORD;
                }
                // We have children and we are still shorter than the word we are searching for, so
                // we need to traverse children. Put the pointer on the children position, and
                // break
                pos = BinaryFormat::readChildrenPosition(root, flags, pos);
                break;
            } else {
                // This chargroup does not match, so skip the remaining part and go to the next.
                if (UnigramDictionary::FLAG_HAS_MULTIPLE_CHARS & flags) {
                    pos = BinaryFormat::skipOtherCharacters(root, pos);
                }
                pos = BinaryFormat::skipFrequency(flags, pos);
                pos = BinaryFormat::skipChildrenPosAndAttributes(root, flags, pos);
            }
            --charGroupCount;
        }
    }
}

bool UnigramDictionary::isValidWord(const uint16_t* const inWord, const int length) const {
    return NOT_VALID_WORD != getFrequency(DICT_ROOT, inWord, length);
}

int UnigramDictionary::getBigrams(unsigned short *word, int length, int *codes, int codesSize,
        unsigned short *outWords, int *frequencies, int maxWordLength, int maxBigrams,
        int maxAlternatives) {
    // TODO: add implementation.
    return 0;
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
inline bool UnigramDictionary::processCurrentNode(const int initialPos, const int initialDepth,
        const int maxDepth, const bool initialTraverseAllNodes, int matchWeight, int inputIndex,
        const int initialDiffs, const int skipPos, const int excessivePos, const int transposedPos,
        int *nextLetters, const int nextLettersSize, int *newCount, int *newChildrenPosition,
        bool *newTraverseAllNodes, int *newMatchRate, int *newInputIndex, int *newDiffs,
        int *nextSiblingPosition, int *newOutputIndex) {
    if (DEBUG_DICT) {
        int inputCount = 0;
        if (skipPos >= 0) ++inputCount;
        if (excessivePos >= 0) ++inputCount;
        if (transposedPos >= 0) ++inputCount;
        assert(inputCount <= 1);
    }
    int pos = initialPos;
    int depth = initialDepth;
    int traverseAllNodes = initialTraverseAllNodes;
    int diffs = initialDiffs;

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
        const bool isTerminal = isLastChar && (0 != (FLAG_IS_TERMINAL & flags));
        // If there are more chars in this node, then this virtual node has children.
        // If we are on the last char, this virtual node has children if this node has.
        const bool hasChildren = (!isLastChar) || BinaryFormat::hasChildrenInFlags(flags);

        // This has to be done for each virtual char (this forwards the "inputIndex" which
        // is the index in the user-inputted chars, as read by proximity chars.
        if (excessivePos == depth && inputIndex < mInputLength - 1) ++inputIndex;
        if (traverseAllNodes || needsToSkipCurrentNode(c, inputIndex, skipPos, depth)) {
            mWord[depth] = c;
            if (traverseAllNodes && isTerminal) {
                // The frequency should be here, because we come here only if this is actually
                // a terminal node, and we are on its last char.
                const int freq = BinaryFormat::readFrequencyWithoutMovingPointer(DICT_ROOT, pos);
                onTerminal(mWord, depth, DICT_ROOT, flags, pos, inputIndex, matchWeight, skipPos,
                           excessivePos, transposedPos, freq, false, nextLetters, nextLettersSize);
            }
            if (!hasChildren) {
                // If we don't have children here, that means we finished processing all
                // characters of this node (we are on the last virtual node), AND we are in
                // traverseAllNodes mode, which means we are searching for *completions*. We
                // should skip the frequency if we have a terminal, and report the position
                // of the next sibling. We don't have to return other values because we are
                // returning false, as in "don't traverse children".
                if (isTerminal) pos = BinaryFormat::skipFrequency(flags, pos);
                *nextSiblingPosition =
                        BinaryFormat::skipChildrenPosAndAttributes(DICT_ROOT, flags, pos);
                return false;
            }
        } else {
            int inputIndexForProximity = inputIndex;

            if (transposedPos >= 0) {
                if (inputIndex == transposedPos) ++inputIndexForProximity;
                if (inputIndex == (transposedPos + 1)) --inputIndexForProximity;
            }

            int matchedProximityCharId = mProximityInfo->getMatchedProximityId(
                    inputIndexForProximity, c, skipPos, excessivePos, transposedPos);
            if (ProximityInfo::UNRELATED_CHAR == matchedProximityCharId) {
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
            mWord[depth] = c;
            // If inputIndex is greater than mInputLength, that means there is no
            // proximity chars. So, we don't need to check proximity.
            if (ProximityInfo::SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR == matchedProximityCharId) {
                multiplyIntCapped(TYPED_LETTER_MULTIPLIER, &matchWeight);
            }
            const bool isSameAsUserTypedLength = mInputLength == inputIndex + 1
                    || (excessivePos == mInputLength - 1 && inputIndex == mInputLength - 2);
            if (isSameAsUserTypedLength && isTerminal) {
                const int freq = BinaryFormat::readFrequencyWithoutMovingPointer(DICT_ROOT, pos);
                onTerminal(mWord, depth, DICT_ROOT, flags, pos, inputIndex, matchWeight, skipPos,
                        excessivePos, transposedPos, freq, true, nextLetters, nextLettersSize);
            }
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
            // Start traversing all nodes after the index exceeds the user typed length
            traverseAllNodes = isSameAsUserTypedLength;
            diffs = diffs
                    + ((ProximityInfo::NEAR_PROXIMITY_CHAR == matchedProximityCharId) ? 1 : 0);
            // Finally, we are ready to go to the next character, the next "virtual node".
            // We should advance the input index.
            // We do this in this branch of the 'if traverseAllNodes' because we are still matching
            // characters to input; the other branch is not matching them but searching for
            // completions, this is why it does not have to do it.
            ++inputIndex;
        }
        // Optimization: Prune out words that are too long compared to how much was typed.
        if (depth >= maxDepth || diffs > mMaxEditDistance) {
            // We are giving up parsing this node and its children. Skip the rest of the node,
            // output the sibling position, and return that we don't want to traverse children.
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
        // Also, the next char is one "virtual node" depth more than this char.
        ++depth;
    } while (NOT_A_CHARACTER != c);

    // If inputIndex is greater than mInputLength, that means there are no proximity chars.
    // Here, that's all we are interested in so we don't need to check for isSameAsUserTypedLength.
    if (mInputLength <= *newInputIndex) {
        traverseAllNodes = true;
    }

    // All the output values that are purely computation by this function are held in local
    // variables. Output them to the caller.
    *newTraverseAllNodes = traverseAllNodes;
    *newMatchRate = matchWeight;
    *newDiffs = diffs;
    *newInputIndex = inputIndex;
    *newOutputIndex = depth;

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

#endif // NEW_DICTIONARY_FORMAT

} // namespace latinime
