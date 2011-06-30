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

#include "basechars.h"
#include "char_utils.h"
#include "dictionary.h"
#include "unigram_dictionary.h"

namespace latinime {

const UnigramDictionary::digraph_t UnigramDictionary::GERMAN_UMLAUT_DIGRAPHS[] =
        { { 'a', 'e' },
        { 'o', 'e' },
        { 'u', 'e' } };

// TODO: check the header
UnigramDictionary::UnigramDictionary(const uint8_t* const streamStart, int typedLetterMultiplier,
        int fullWordMultiplier, int maxWordLength, int maxWords, int maxProximityChars,
        const bool isLatestDictVersion)
    : DICT_ROOT(streamStart),
    MAX_WORD_LENGTH(maxWordLength), MAX_WORDS(maxWords),
    MAX_PROXIMITY_CHARS(maxProximityChars), IS_LATEST_DICT_VERSION(isLatestDictVersion),
    TYPED_LETTER_MULTIPLIER(typedLetterMultiplier), FULL_WORD_MULTIPLIER(fullWordMultiplier),
    ROOT_POS(isLatestDictVersion ? DICTIONARY_HEADER_SIZE : 0),
    BYTES_IN_ONE_CHAR(MAX_PROXIMITY_CHARS * sizeof(*mInputCodes)),
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
void UnigramDictionary::getWordWithDigraphSuggestionsRec(const ProximityInfo *proximityInfo,
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

int UnigramDictionary::getSuggestions(const ProximityInfo *proximityInfo, const int *xcoordinates,
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
            short unsigned int* w = mOutputChars + j * MAX_WORD_LENGTH;
            char s[MAX_WORD_LENGTH];
            for (int i = 0; i <= MAX_WORD_LENGTH; i++) s[i] = w[i];
            LOGI("%s %i", s, mFrequencies[j]);
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

void UnigramDictionary::getWordSuggestions(const ProximityInfo *proximityInfo,
        const int *xcoordinates, const int *ycoordinates, const int *codes, const int codesSize,
        unsigned short *outWords, int *frequencies) {

    PROF_OPEN;
    PROF_START(0);
    initSuggestions(codes, codesSize, outWords, frequencies);
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

void UnigramDictionary::initSuggestions(const int *codes, const int codesSize,
        unsigned short *outWords, int *frequencies) {
    if (DEBUG_DICT) {
        LOGI("initSuggest");
    }
    mFrequencies = frequencies;
    mOutputChars = outWords;
    mInputCodes = codes;
    mInputLength = codesSize;
    mMaxEditDistance = mInputLength < 5 ? 2 : mInputLength / 2;
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
        char s[length + 1];
        for (int i = 0; i <= length; i++) s[i] = word[i];
        LOGI("Found word = %s, freq = %d", s, frequency);
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
            char s[length + 1];
            for (int i = 0; i <= length; i++) s[i] = word[i];
            LOGI("Added word = %s, freq = %d, %d", s, frequency, S_INT_MAX);
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

static inline unsigned short toBaseLowerCase(unsigned short c) {
    if (c < sizeof(BASE_CHARS) / sizeof(BASE_CHARS[0])) {
        c = BASE_CHARS[c];
    }
    if (c >='A' && c <= 'Z') {
        c |= 32;
    } else if (c > 127) {
        c = latin_tolower(c);
    }
    return c;
}

bool UnigramDictionary::sameAsTyped(const unsigned short *word, int length) const {
    if (length != mInputLength) {
        return false;
    }
    const int *inputCodes = mInputCodes;
    while (length--) {
        if ((unsigned int) *inputCodes != (unsigned int) *word) {
            return false;
        }
        inputCodes += MAX_PROXIMITY_CHARS;
        word++;
    }
    return true;
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
        if (!existsAdjacentProximityChars(inputIndex, mInputLength)) {
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
    const unsigned short userTypedChar = getInputCharsAt(inputIndex)[0];
    // Skip the ' or other letter and continue deeper
    return (c == QUOTE && userTypedChar != QUOTE) || skipPos == depth;
}

inline bool UnigramDictionary::existsAdjacentProximityChars(const int inputIndex,
        const int inputLength) const {
    if (inputIndex < 0 || inputIndex >= inputLength) return false;
    const int currentChar = *getInputCharsAt(inputIndex);
    const int leftIndex = inputIndex - 1;
    if (leftIndex >= 0) {
        const int *leftChars = getInputCharsAt(leftIndex);
        int i = 0;
        while (leftChars[i] > 0 && i < MAX_PROXIMITY_CHARS) {
            if (leftChars[i++] == currentChar) return true;
        }
    }
    const int rightIndex = inputIndex + 1;
    if (rightIndex < inputLength) {
        const int *rightChars = getInputCharsAt(rightIndex);
        int i = 0;
        while (rightChars[i] > 0 && i < MAX_PROXIMITY_CHARS) {
            if (rightChars[i++] == currentChar) return true;
        }
    }
    return false;
}

// In the following function, c is the current character of the dictionary word
// currently examined.
// currentChars is an array containing the keys close to the character the
// user actually typed at the same position. We want to see if c is in it: if so,
// then the word contains at that position a character close to what the user
// typed.
// What the user typed is actually the first character of the array.
// Notice : accented characters do not have a proximity list, so they are alone
// in their list. The non-accented version of the character should be considered
// "close", but not the other keys close to the non-accented version.
inline UnigramDictionary::ProximityType UnigramDictionary::getMatchedProximityId(
        const int *currentChars, const unsigned short c, const int skipPos,
        const int excessivePos, const int transposedPos) {
    const unsigned short baseLowerC = toBaseLowerCase(c);

    // The first char in the array is what user typed. If it matches right away,
    // that means the user typed that same char for this pos.
    if (currentChars[0] == baseLowerC || currentChars[0] == c)
        return SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR;

    // If one of those is true, we should not check for close characters at all.
    if (skipPos >= 0 || excessivePos >= 0 || transposedPos >= 0)
        return UNRELATED_CHAR;

    // If the non-accented, lowercased version of that first character matches c,
    // then we have a non-accented version of the accented character the user
    // typed. Treat it as a close char.
    if (toBaseLowerCase(currentChars[0]) == baseLowerC)
        return NEAR_PROXIMITY_CHAR;

    // Not an exact nor an accent-alike match: search the list of close keys
    int j = 1;
    while (currentChars[j] > 0 && j < MAX_PROXIMITY_CHARS) {
        const bool matched = (currentChars[j] == baseLowerC || currentChars[j] == c);
        if (matched) return NEAR_PROXIMITY_CHAR;
        ++j;
    }

    // Was not included, signal this as an unrelated character.
    return UNRELATED_CHAR;
}

inline void UnigramDictionary::onTerminal(unsigned short int* word, const int depth,
        const uint8_t* const root, const uint8_t flags, const int pos,
        const int inputIndex, const int matchWeight, const int skipPos,
        const int excessivePos, const int transposedPos, const int freq, const bool sameLength,
        int* nextLetters, const int nextLettersSize) {

    const bool isSameAsTyped = sameLength ? sameAsTyped(word, depth + 1) : false;
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

#ifndef NEW_DICTIONARY_FORMAT
// TODO: Don't forget to bring inline functions back to over where they are used.

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
                            char s[inputLength + 1];
                            for (int i = 0; i < inputLength; ++i) s[i] = word[i];
                            s[inputLength] = 0;
                            LOGI("New missing space word found: %d > %d (%s), %d, %d",
                                    newFreq, maxFreq, s, inputLength, depth);
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
    const int *currentChars = getInputCharsAt(inputIndex);
    unsigned short c;
    *siblingPos = Dictionary::setDictionaryValues(DICT_ROOT, IS_LATEST_DICT_VERSION, firstChildPos,
            &c, newChildPosition, newTerminal, newFreq);
    const unsigned int inputC = currentChars[0];
    if (DEBUG_DICT) {
        assert(inputC <= U_SHORT_MAX);
    }
    const unsigned short baseLowerC = toBaseLowerCase(c);
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

inline bool UnigramDictionary::processCurrentNode(const int pos, const int depth,
        const int maxDepth, const bool traverseAllNodes, int matchWeight, int inputIndex,
        const int diffs, const int skipPos, const int excessivePos, const int transposedPos,
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
        const int *currentChars = getInputCharsAt(inputIndex);

        if (transposedPos >= 0) {
            if (inputIndex == transposedPos) currentChars += MAX_PROXIMITY_CHARS;
            if (inputIndex == (transposedPos + 1)) currentChars -= MAX_PROXIMITY_CHARS;
        }

        int matchedProximityCharId = getMatchedProximityId(currentChars, c, skipPos, excessivePos,
                transposedPos);
        if (UNRELATED_CHAR == matchedProximityCharId) return false;
        mWord[depth] = c;
        // If inputIndex is greater than mInputLength, that means there is no
        // proximity chars. So, we don't need to check proximity.
        if (SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR == matchedProximityCharId) {
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
        *newDiffs = diffs + ((NEAR_PROXIMITY_CHAR == matchedProximityCharId) ? 1 : 0);
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

inline bool UnigramDictionary::processCurrentNode(const int pos, const int depth,
        const int maxDepth, const bool traverseAllNodes, int matchWeight, int inputIndex,
        const int diffs, const int skipPos, const int excessivePos, const int transposedPos,
        int *nextLetters, const int nextLettersSize, int *newCount, int *newChildPosition,
        bool *newTraverseAllNodes, int *newMatchRate, int *newInputIndex, int *newDiffs,
        int *nextSiblingPosition, int *newOutputIndex) {
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

    const uint8_t flags = 0; // No flags for now

    if (excessivePos == depth && inputIndex < mInputLength - 1) ++inputIndex;

    *nextSiblingPosition = Dictionary::setDictionaryValues(DICT_ROOT, IS_LATEST_DICT_VERSION, pos,
            &c, &childPosition, &terminal, &freq);
    *newOutputIndex = depth + 1;

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
        const int *currentChars = getInputCharsAt(inputIndex);

        if (transposedPos >= 0) {
            if (inputIndex == transposedPos) currentChars += MAX_PROXIMITY_CHARS;
            if (inputIndex == (transposedPos + 1)) currentChars -= MAX_PROXIMITY_CHARS;
        }

        int matchedProximityCharId = getMatchedProximityId(currentChars, c, skipPos, excessivePos,
                transposedPos);
        if (UNRELATED_CHAR == matchedProximityCharId) return false;
        mWord[depth] = c;
        // If inputIndex is greater than mInputLength, that means there is no
        // proximity chars. So, we don't need to check proximity.
        if (SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR == matchedProximityCharId) {
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
        *newDiffs = diffs + ((NEAR_PROXIMITY_CHAR == matchedProximityCharId) ? 1 : 0);
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

#endif // NEW_DICTIONARY_FORMAT

} // namespace latinime
