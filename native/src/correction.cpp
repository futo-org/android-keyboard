/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <assert.h>
#include <ctype.h>
#include <stdio.h>
#include <string.h>

#define LOG_TAG "LatinIME: correction.cpp"

#include "correction.h"
#include "dictionary.h"
#include "proximity_info.h"

namespace latinime {

//////////////////////
// inline functions //
//////////////////////
static const char QUOTE = '\'';

inline bool Correction::isQuote(const unsigned short c) {
    const unsigned short userTypedChar = mProximityInfo->getPrimaryCharAt(mInputIndex);
    return (c == QUOTE && userTypedChar != QUOTE);
}

////////////////
// Correction //
////////////////

Correction::Correction(const int typedLetterMultiplier, const int fullWordMultiplier)
        : TYPED_LETTER_MULTIPLIER(typedLetterMultiplier), FULL_WORD_MULTIPLIER(fullWordMultiplier) {
}

void Correction::initCorrection(const ProximityInfo *pi, const int inputLength,
        const int maxDepth) {
    mProximityInfo = pi;
    mInputLength = inputLength;
    mMaxDepth = maxDepth;
    mMaxEditDistance = mInputLength < 5 ? 2 : mInputLength / 2;
}

void Correction::initCorrectionState(
        const int rootPos, const int childCount, const bool traverseAll) {
    latinime::initCorrectionState(mCorrectionStates, rootPos, childCount, traverseAll);
    // TODO: remove
    mCorrectionStates[0].mTransposedPos = mTransposedPos;
    mCorrectionStates[0].mExcessivePos = mExcessivePos;
    mCorrectionStates[0].mSkipPos = mSkipPos;
}

void Correction::setCorrectionParams(const int skipPos, const int excessivePos,
        const int transposedPos, const int spaceProximityPos, const int missingSpacePos,
        const bool useFullEditDistance) {
    // TODO: remove
    mTransposedPos = transposedPos;
    mExcessivePos = excessivePos;
    mSkipPos = skipPos;
    // TODO: remove
    mCorrectionStates[0].mTransposedPos = transposedPos;
    mCorrectionStates[0].mExcessivePos = excessivePos;
    mCorrectionStates[0].mSkipPos = skipPos;

    mSpaceProximityPos = spaceProximityPos;
    mMissingSpacePos = missingSpacePos;
    mUseFullEditDistance = useFullEditDistance;
}

void Correction::checkState() {
    if (DEBUG_DICT) {
        int inputCount = 0;
        if (mSkipPos >= 0) ++inputCount;
        if (mExcessivePos >= 0) ++inputCount;
        if (mTransposedPos >= 0) ++inputCount;
        // TODO: remove this assert
        assert(inputCount <= 1);
    }
}

int Correction::getFreqForSplitTwoWords(const int firstFreq, const int secondFreq,
        const unsigned short *word) {
    return Correction::RankingAlgorithm::calcFreqForSplitTwoWords(
            firstFreq, secondFreq, this, word);
}

int Correction::getFinalFreq(const int freq, unsigned short **word, int *wordLength) {
    const int outputIndex = mTerminalOutputIndex;
    const int inputIndex = mTerminalInputIndex;
    *wordLength = outputIndex + 1;
    if (mProximityInfo->sameAsTyped(mWord, outputIndex + 1) || outputIndex < MIN_SUGGEST_DEPTH) {
        return -1;
    }

    *word = mWord;
    return Correction::RankingAlgorithm::calculateFinalFreq(
            inputIndex, outputIndex, freq, mEditDistanceTable, this);
}

bool Correction::initProcessState(const int outputIndex) {
    if (mCorrectionStates[outputIndex].mChildCount <= 0) {
        return false;
    }
    mOutputIndex = outputIndex;
    --(mCorrectionStates[outputIndex].mChildCount);
    mInputIndex = mCorrectionStates[outputIndex].mInputIndex;
    mNeedsToTraverseAllNodes = mCorrectionStates[outputIndex].mNeedsToTraverseAllNodes;

    mEquivalentCharCount = mCorrectionStates[outputIndex].mEquivalentCharCount;
    mProximityCount = mCorrectionStates[outputIndex].mProximityCount;
    mTransposedCount = mCorrectionStates[outputIndex].mTransposedCount;
    mExcessiveCount = mCorrectionStates[outputIndex].mExcessiveCount;
    mSkippedCount = mCorrectionStates[outputIndex].mSkippedCount;
    mLastCharExceeded = mCorrectionStates[outputIndex].mLastCharExceeded;

    mTransposedPos = mCorrectionStates[outputIndex].mTransposedPos;
    mExcessivePos = mCorrectionStates[outputIndex].mExcessivePos;
    mSkipPos = mCorrectionStates[outputIndex].mSkipPos;

    mMatching = false;
    mProximityMatching = false;
    mTransposing = false;
    mExceeding = false;
    mSkipping = false;

    return true;
}

int Correction::goDownTree(
        const int parentIndex, const int childCount, const int firstChildPos) {
    mCorrectionStates[mOutputIndex].mParentIndex = parentIndex;
    mCorrectionStates[mOutputIndex].mChildCount = childCount;
    mCorrectionStates[mOutputIndex].mSiblingPos = firstChildPos;
    return mOutputIndex;
}

// TODO: remove
int Correction::getOutputIndex() {
    return mOutputIndex;
}

// TODO: remove
int Correction::getInputIndex() {
    return mInputIndex;
}

// TODO: remove
bool Correction::needsToTraverseAllNodes() {
    return mNeedsToTraverseAllNodes;
}

void Correction::incrementInputIndex() {
    ++mInputIndex;
}

void Correction::incrementOutputIndex() {
    ++mOutputIndex;
    mCorrectionStates[mOutputIndex].mParentIndex = mCorrectionStates[mOutputIndex - 1].mParentIndex;
    mCorrectionStates[mOutputIndex].mChildCount = mCorrectionStates[mOutputIndex - 1].mChildCount;
    mCorrectionStates[mOutputIndex].mSiblingPos = mCorrectionStates[mOutputIndex - 1].mSiblingPos;
    mCorrectionStates[mOutputIndex].mInputIndex = mInputIndex;
    mCorrectionStates[mOutputIndex].mNeedsToTraverseAllNodes = mNeedsToTraverseAllNodes;

    mCorrectionStates[mOutputIndex].mEquivalentCharCount = mEquivalentCharCount;
    mCorrectionStates[mOutputIndex].mProximityCount = mProximityCount;
    mCorrectionStates[mOutputIndex].mTransposedCount = mTransposedCount;
    mCorrectionStates[mOutputIndex].mExcessiveCount = mExcessiveCount;
    mCorrectionStates[mOutputIndex].mSkippedCount = mSkippedCount;

    mCorrectionStates[mOutputIndex].mSkipPos = mSkipPos;
    mCorrectionStates[mOutputIndex].mTransposedPos = mTransposedPos;
    mCorrectionStates[mOutputIndex].mExcessivePos = mExcessivePos;

    mCorrectionStates[mOutputIndex].mLastCharExceeded = mLastCharExceeded;

    mCorrectionStates[mOutputIndex].mMatching = mMatching;
    mCorrectionStates[mOutputIndex].mProximityMatching = mProximityMatching;
    mCorrectionStates[mOutputIndex].mTransposing = mTransposing;
    mCorrectionStates[mOutputIndex].mExceeding = mExceeding;
    mCorrectionStates[mOutputIndex].mSkipping = mSkipping;
}

void Correction::startToTraverseAllNodes() {
    mNeedsToTraverseAllNodes = true;
}

bool Correction::needsToPrune() const {
    return mOutputIndex - 1 >= mMaxDepth || mProximityCount > mMaxEditDistance;
}

// TODO: inline?
Correction::CorrectionType Correction::processSkipChar(
        const int32_t c, const bool isTerminal, const bool inputIndexIncremented) {
    mWord[mOutputIndex] = c;
    if (needsToTraverseAllNodes() && isTerminal) {
        mTerminalInputIndex = mInputIndex - (inputIndexIncremented ? 1 : 0);
        mTerminalOutputIndex = mOutputIndex;
        incrementOutputIndex();
        return TRAVERSE_ALL_ON_TERMINAL;
    } else {
        incrementOutputIndex();
        return TRAVERSE_ALL_NOT_ON_TERMINAL;
    }
}

inline bool isEquivalentChar(ProximityInfo::ProximityType type) {
    return type == ProximityInfo::EQUIVALENT_CHAR;
}

Correction::CorrectionType Correction::processCharAndCalcState(
        const int32_t c, const bool isTerminal) {
    const int correctionCount = (mSkippedCount + mExcessiveCount + mTransposedCount);
    // TODO: Change the limit if we'll allow two or more corrections
    const bool noCorrectionsHappenedSoFar = correctionCount == 0;
    const bool canTryCorrection = noCorrectionsHappenedSoFar;
    int proximityIndex = 0;
    mDistances[mOutputIndex] = NOT_A_DISTANCE;

    if (mNeedsToTraverseAllNodes || isQuote(c)) {
        bool incremented = false;
        if (mLastCharExceeded && mInputIndex == mInputLength - 1) {
            // TODO: Do not check the proximity if EditDistance exceeds the threshold
            const ProximityInfo::ProximityType matchId =
                    mProximityInfo->getMatchedProximityId(mInputIndex, c, true, &proximityIndex);
            if (isEquivalentChar(matchId)) {
                mLastCharExceeded = false;
                --mExcessiveCount;
                mDistances[mOutputIndex] =
                        mProximityInfo->getNormalizedSquaredDistance(mInputIndex, 0);
            } else if (matchId == ProximityInfo::NEAR_PROXIMITY_CHAR) {
                mLastCharExceeded = false;
                --mExcessiveCount;
                ++mProximityCount;
                mDistances[mOutputIndex] =
                        mProximityInfo->getNormalizedSquaredDistance(mInputIndex, proximityIndex);
            }
            incrementInputIndex();
            incremented = true;
        }
        return processSkipChar(c, isTerminal, incremented);
    }

    if (mExcessivePos >= 0) {
        if (mExcessiveCount == 0 && mExcessivePos < mOutputIndex) {
            mExcessivePos = mOutputIndex;
        }
        if (mExcessivePos < mInputLength - 1) {
            mExceeding = mExcessivePos == mInputIndex && canTryCorrection;
        }
    }

    if (mSkipPos >= 0) {
        if (mSkippedCount == 0 && mSkipPos < mOutputIndex) {
            if (DEBUG_DICT) {
                assert(mSkipPos == mOutputIndex - 1);
            }
            mSkipPos = mOutputIndex;
        }
        mSkipping = mSkipPos == mOutputIndex && canTryCorrection;
    }

    if (mTransposedPos >= 0) {
        if (mTransposedCount == 0 && mTransposedPos < mOutputIndex) {
            mTransposedPos = mOutputIndex;
        }
        if (mTransposedPos < mInputLength - 1) {
            mTransposing = mInputIndex == mTransposedPos && canTryCorrection;
        }
    }

    bool secondTransposing = false;
    if (mTransposedCount % 2 == 1) {
        if (isEquivalentChar(mProximityInfo->getMatchedProximityId(mInputIndex - 1, c, false))) {
            ++mTransposedCount;
            secondTransposing = true;
        } else if (mCorrectionStates[mOutputIndex].mExceeding) {
            --mTransposedCount;
            ++mExcessiveCount;
            --mExcessivePos;
            incrementInputIndex();
        } else {
            --mTransposedCount;
            if (DEBUG_CORRECTION) {
                DUMP_WORD(mWord, mOutputIndex);
                LOGI("UNRELATED(0): %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                        mTransposedCount, mExcessiveCount, c);
            }
            return UNRELATED;
        }
    }

    // TODO: Change the limit if we'll allow two or more proximity chars with corrections
    const bool checkProximityChars = noCorrectionsHappenedSoFar ||  mProximityCount == 0;
    ProximityInfo::ProximityType matchedProximityCharId = secondTransposing
            ? ProximityInfo::EQUIVALENT_CHAR
            : mProximityInfo->getMatchedProximityId(
                    mInputIndex, c, checkProximityChars, &proximityIndex);

    if (ProximityInfo::UNRELATED_CHAR == matchedProximityCharId) {
        if (canTryCorrection && mOutputIndex > 0
                && mCorrectionStates[mOutputIndex].mProximityMatching
                && mCorrectionStates[mOutputIndex].mExceeding
                && isEquivalentChar(mProximityInfo->getMatchedProximityId(
                        mInputIndex, mWord[mOutputIndex - 1], false))) {
            if (DEBUG_CORRECTION) {
                LOGI("CONVERSION p->e %c", mWord[mOutputIndex - 1]);
            }
            // Conversion p->e
            // Example:
            // wearth ->    earth
            // px     -> (E)mmmmm
            ++mExcessiveCount;
            --mProximityCount;
            mExcessivePos = mOutputIndex - 1;
            ++mInputIndex;
            // Here, we are doing something equivalent to matchedProximityCharId,
            // but we already know that "excessive char correction" just happened
            // so that we just need to check "mProximityCount == 0".
            matchedProximityCharId = mProximityInfo->getMatchedProximityId(
                    mInputIndex, c, mProximityCount == 0, &proximityIndex);
        }
    }

    if (ProximityInfo::UNRELATED_CHAR == matchedProximityCharId) {
        // TODO: Optimize
        // As the current char turned out to be an unrelated char,
        // we will try other correction-types. Please note that mCorrectionStates[mOutputIndex]
        // here refers to the previous state.
        if (mInputIndex < mInputLength - 1 && mOutputIndex > 0 && mTransposedCount > 0
                && !mCorrectionStates[mOutputIndex].mTransposing
                && mCorrectionStates[mOutputIndex - 1].mTransposing
                && isEquivalentChar(mProximityInfo->getMatchedProximityId(
                        mInputIndex, mWord[mOutputIndex - 1], false))
                && isEquivalentChar(
                        mProximityInfo->getMatchedProximityId(mInputIndex + 1, c, false))) {
            // Conversion t->e
            // Example:
            // occaisional -> occa   sional
            // mmmmttx     -> mmmm(E)mmmmmm
            mTransposedCount -= 2;
            ++mExcessiveCount;
            ++mInputIndex;
        } else if (mOutputIndex > 0 && mInputIndex > 0 && mTransposedCount > 0
                && !mCorrectionStates[mOutputIndex].mTransposing
                && mCorrectionStates[mOutputIndex - 1].mTransposing
                && isEquivalentChar(
                        mProximityInfo->getMatchedProximityId(mInputIndex - 1, c, false))) {
            // Conversion t->s
            // Example:
            // chcolate -> chocolate
            // mmttx    -> mmsmmmmmm
            mTransposedCount -= 2;
            ++mSkippedCount;
            --mInputIndex;
        } else if (canTryCorrection && mInputIndex > 0
                && mCorrectionStates[mOutputIndex].mProximityMatching
                && mCorrectionStates[mOutputIndex].mSkipping
                && isEquivalentChar(
                        mProximityInfo->getMatchedProximityId(mInputIndex - 1, c, false))) {
            // Conversion p->s
            // Note: This logic tries saving cases like contrst --> contrast -- "a" is one of
            // proximity chars of "s", but it should rather be handled as a skipped char.
            ++mSkippedCount;
            --mProximityCount;
            return processSkipChar(c, isTerminal, false);
        } else if ((mExceeding || mTransposing) && mInputIndex - 1 < mInputLength
                && isEquivalentChar(
                        mProximityInfo->getMatchedProximityId(mInputIndex + 1, c, false))) {
            // 1.2. Excessive or transpose correction
            if (mTransposing) {
                ++mTransposedCount;
            } else {
                ++mExcessiveCount;
                incrementInputIndex();
            }
        } else if (mSkipping) {
            // 3. Skip correction
            ++mSkippedCount;
            return processSkipChar(c, isTerminal, false);
        } else {
            if (DEBUG_CORRECTION) {
                DUMP_WORD(mWord, mOutputIndex);
                LOGI("UNRELATED(1): %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                        mTransposedCount, mExcessiveCount, c);
            }
            return UNRELATED;
        }
    } else if (secondTransposing) {
        // If inputIndex is greater than mInputLength, that means there is no
        // proximity chars. So, we don't need to check proximity.
        mMatching = true;
    } else if (isEquivalentChar(matchedProximityCharId)) {
        mMatching = true;
        ++mEquivalentCharCount;
        mDistances[mOutputIndex] = mProximityInfo->getNormalizedSquaredDistance(mInputIndex, 0);
    } else if (ProximityInfo::NEAR_PROXIMITY_CHAR == matchedProximityCharId) {
        mProximityMatching = true;
        ++mProximityCount;
        mDistances[mOutputIndex] =
                mProximityInfo->getNormalizedSquaredDistance(mInputIndex, proximityIndex);
    }

    mWord[mOutputIndex] = c;

    // 4. Last char excessive correction
    mLastCharExceeded = mExcessiveCount == 0 && mSkippedCount == 0 && mTransposedCount == 0
            && mProximityCount == 0 && (mInputIndex == mInputLength - 2);
    const bool isSameAsUserTypedLength = (mInputLength == mInputIndex + 1) || mLastCharExceeded;
    if (mLastCharExceeded) {
        ++mExcessiveCount;
    }

    // Start traversing all nodes after the index exceeds the user typed length
    if (isSameAsUserTypedLength) {
        startToTraverseAllNodes();
    }

    const bool needsToTryOnTerminalForTheLastPossibleExcessiveChar =
            mExceeding && mInputIndex == mInputLength - 2;

    // Finally, we are ready to go to the next character, the next "virtual node".
    // We should advance the input index.
    // We do this in this branch of the 'if traverseAllNodes' because we are still matching
    // characters to input; the other branch is not matching them but searching for
    // completions, this is why it does not have to do it.
    incrementInputIndex();
    // Also, the next char is one "virtual node" depth more than this char.
    incrementOutputIndex();

    if ((needsToTryOnTerminalForTheLastPossibleExcessiveChar
            || isSameAsUserTypedLength) && isTerminal) {
        mTerminalInputIndex = mInputIndex - 1;
        mTerminalOutputIndex = mOutputIndex - 1;
        if (DEBUG_CORRECTION) {
            DUMP_WORD(mWord, mOutputIndex);
            LOGI("ONTERMINAL(1): %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                    mTransposedCount, mExcessiveCount, c);
        }
        return ON_TERMINAL;
    } else {
        return NOT_ON_TERMINAL;
    }
}

Correction::~Correction() {
}

/////////////////////////
// static inline utils //
/////////////////////////

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
            // TODO: This overflow check gives a wrong answer when, for example,
            //       temp = 2^16 + 1 and multiplier = 2^17 + 1.
            //       Fix this behavior.
            const int tempRetval = temp * multiplier;
            *base = tempRetval >= temp ? tempRetval : S_INT_MAX;
        }
    }
}

inline static int powerIntCapped(const int base, const int n) {
    if (n <= 0) return 1;
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

inline static int getQuoteCount(const unsigned short* word, const int length) {
    int quoteCount = 0;
    for (int i = 0; i < length; ++i) {
        if(word[i] == '\'') {
            ++quoteCount;
        }
    }
    return quoteCount;
}

inline static bool isUpperCase(unsigned short c) {
     if (c < sizeof(BASE_CHARS) / sizeof(BASE_CHARS[0])) {
         c = BASE_CHARS[c];
     }
     if (isupper(c)) {
         return true;
     }
     return false;
}

/* static */
inline static int editDistance(
        int* editDistanceTable, const unsigned short* input,
        const int inputLength, const unsigned short* output, const int outputLength) {
    // dp[li][lo] dp[a][b] = dp[ a * lo + b]
    int* dp = editDistanceTable;
    const int li = inputLength + 1;
    const int lo = outputLength + 1;
    for (int i = 0; i < li; ++i) {
        dp[lo * i] = i;
    }
    for (int i = 0; i < lo; ++i) {
        dp[i] = i;
    }

    for (int i = 0; i < li - 1; ++i) {
        for (int j = 0; j < lo - 1; ++j) {
            const uint32_t ci = Dictionary::toBaseLowerCase(input[i]);
            const uint32_t co = Dictionary::toBaseLowerCase(output[j]);
            const uint16_t cost = (ci == co) ? 0 : 1;
            dp[(i + 1) * lo + (j + 1)] = min(dp[i * lo + (j + 1)] + 1,
                    min(dp[(i + 1) * lo + j] + 1, dp[i * lo + j] + cost));
            if (i > 0 && j > 0 && ci == Dictionary::toBaseLowerCase(output[j - 1])
                    && co == Dictionary::toBaseLowerCase(input[i - 1])) {
                dp[(i + 1) * lo + (j + 1)] = min(
                        dp[(i + 1) * lo + (j + 1)], dp[(i - 1) * lo + (j - 1)] + cost);
            }
        }
    }

    if (DEBUG_EDIT_DISTANCE) {
        LOGI("IN = %d, OUT = %d", inputLength, outputLength);
        for (int i = 0; i < li; ++i) {
            for (int j = 0; j < lo; ++j) {
                LOGI("EDIT[%d][%d], %d", i, j, dp[i * lo + j]);
            }
        }
    }
    return dp[li * lo - 1];
}

//////////////////////
// RankingAlgorithm //
//////////////////////

/* static */
int Correction::RankingAlgorithm::calculateFinalFreq(const int inputIndex, const int outputIndex,
        const int freq, int* editDistanceTable, const Correction* correction) {
    const int excessivePos = correction->getExcessivePos();
    const int inputLength = correction->mInputLength;
    const int typedLetterMultiplier = correction->TYPED_LETTER_MULTIPLIER;
    const int fullWordMultiplier = correction->FULL_WORD_MULTIPLIER;
    const ProximityInfo *proximityInfo = correction->mProximityInfo;
    const int skippedCount = correction->mSkippedCount;
    const int transposedCount = correction->mTransposedCount / 2;
    const int excessiveCount = correction->mExcessiveCount + correction->mTransposedCount % 2;
    const int proximityMatchedCount = correction->mProximityCount;
    const bool lastCharExceeded = correction->mLastCharExceeded;
    const bool useFullEditDistance = correction->mUseFullEditDistance;
    const int outputLength = outputIndex + 1;
    if (skippedCount >= inputLength || inputLength == 0) {
        return -1;
    }

    // TODO: find more robust way
    bool sameLength = lastCharExceeded ? (inputLength == inputIndex + 2)
            : (inputLength == inputIndex + 1);

    // TODO: use mExcessiveCount
    const int matchCount = inputLength - correction->mProximityCount - excessiveCount;

    const unsigned short* word = correction->mWord;
    const bool skipped = skippedCount > 0;

    const int quoteDiffCount = max(0, getQuoteCount(word, outputIndex + 1)
            - getQuoteCount(proximityInfo->getPrimaryInputWord(), inputLength));

    // TODO: Calculate edit distance for transposed and excessive
    int ed = 0;
    int adjustedProximityMatchedCount = proximityMatchedCount;

    int finalFreq = freq;

    // TODO: Optimize this.
    // TODO: Ignoring edit distance for transposed char, for now
    if (transposedCount == 0 && (proximityMatchedCount > 0 || skipped || excessiveCount > 0)) {
        const unsigned short* primaryInputWord = proximityInfo->getPrimaryInputWord();
        ed = editDistance(editDistanceTable, primaryInputWord,
                inputLength, word, outputIndex + 1);
        const int matchWeight = powerIntCapped(typedLetterMultiplier,
                max(inputLength, outputIndex + 1) - ed);
        multiplyIntCapped(matchWeight, &finalFreq);

        // TODO: Demote further if there are two or more excessive chars with longer user input?
        if (inputLength > outputIndex + 1) {
            multiplyRate(INPUT_EXCEEDS_OUTPUT_DEMOTION_RATE, &finalFreq);
        }

        ed = max(0, ed - quoteDiffCount);

        if (ed == 1 && (inputLength == outputIndex || inputLength == outputIndex + 2)) {
            // Promote a word with just one skipped or excessive char
            if (sameLength) {
                multiplyRate(WORDS_WITH_JUST_ONE_CORRECTION_PROMOTION_RATE, &finalFreq);
            } else {
                multiplyIntCapped(typedLetterMultiplier, &finalFreq);
            }
        } else if (ed == 0) {
            multiplyIntCapped(typedLetterMultiplier, &finalFreq);
            sameLength = true;
        }
        adjustedProximityMatchedCount = min(max(0, ed - (outputIndex + 1 - inputLength)),
                proximityMatchedCount);
    } else {
        // TODO: Calculate the edit distance for transposed char
        const int matchWeight = powerIntCapped(typedLetterMultiplier, matchCount);
        multiplyIntCapped(matchWeight, &finalFreq);
    }

    if (proximityInfo->getMatchedProximityId(0, word[0], true)
            == ProximityInfo::UNRELATED_CHAR) {
        multiplyRate(FIRST_CHAR_DIFFERENT_DEMOTION_RATE, &finalFreq);
    }

    ///////////////////////////////////////////////
    // Promotion and Demotion for each correction

    // Demotion for a word with missing character
    if (skipped) {
        const int demotionRate = WORDS_WITH_MISSING_CHARACTER_DEMOTION_RATE
                * (10 * inputLength - WORDS_WITH_MISSING_CHARACTER_DEMOTION_START_POS_10X)
                / (10 * inputLength
                        - WORDS_WITH_MISSING_CHARACTER_DEMOTION_START_POS_10X + 10);
        if (DEBUG_DICT_FULL) {
            LOGI("Demotion rate for missing character is %d.", demotionRate);
        }
        multiplyRate(demotionRate, &finalFreq);
    }

    // Demotion for a word with transposed character
    if (transposedCount > 0) multiplyRate(
            WORDS_WITH_TRANSPOSED_CHARACTERS_DEMOTION_RATE, &finalFreq);

    // Demotion for a word with excessive character
    if (excessiveCount > 0) {
        multiplyRate(WORDS_WITH_EXCESSIVE_CHARACTER_DEMOTION_RATE, &finalFreq);
        if (!lastCharExceeded && !proximityInfo->existsAdjacentProximityChars(excessivePos)) {
            if (DEBUG_CORRECTION_FREQ) {
                LOGI("Double excessive demotion");
            }
            // If an excessive character is not adjacent to the left char or the right char,
            // we will demote this word.
            multiplyRate(WORDS_WITH_EXCESSIVE_CHARACTER_OUT_OF_PROXIMITY_DEMOTION_RATE, &finalFreq);
        }
    }

    // Score calibration by touch coordinates is being done only for pure-fat finger typing error
    // cases.
    // TODO: Remove this constraint.
    if (CALIBRATE_SCORE_BY_TOUCH_COORDINATES && proximityInfo->touchPositionCorrectionEnabled()
            && skippedCount == 0 && excessiveCount == 0 && transposedCount == 0) {
        for (int i = 0; i < outputLength; ++i) {
            const int squaredDistance = correction->mDistances[i];
            if (i < adjustedProximityMatchedCount) {
                multiplyIntCapped(typedLetterMultiplier, &finalFreq);
            }
            if (squaredDistance >= 0) {
                // Promote or demote the score according to the distance from the sweet spot
                static const float A = ZERO_DISTANCE_PROMOTION_RATE / 100.0f;
                static const float B = 1.0f;
                static const float C = 0.5f;
                static const float R1 = NEUTRAL_SCORE_SQUARED_RADIUS;
                static const float R2 = HALF_SCORE_SQUARED_RADIUS;
                const float x = (float)squaredDistance
                        / ProximityInfo::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR;
                const float factor = (x < R1)
                    ? (A * (R1 - x) + B * x) / R1
                    : (B * (R2 - x) + C * (x - R1)) / (R2 - R1);
                // factor is piecewise linear function like:
                // A -_                  .
                //     ^-_               .
                // B      \              .
                //         \             .
                // C        \            .
                //   0   R1 R2
                multiplyRate((int)(factor * 100), &finalFreq);
            } else if (squaredDistance == PROXIMITY_CHAR_WITHOUT_DISTANCE_INFO) {
                multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
            }
        }
    } else {
        // Promotion for a word with proximity characters
        for (int i = 0; i < adjustedProximityMatchedCount; ++i) {
            // A word with proximity corrections
            if (DEBUG_DICT_FULL) {
                LOGI("Found a proximity correction.");
            }
            multiplyIntCapped(typedLetterMultiplier, &finalFreq);
            multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
        }
    }

    const int errorCount = adjustedProximityMatchedCount > 0
            ? adjustedProximityMatchedCount
            : (proximityMatchedCount + transposedCount);
    multiplyRate(
            100 - CORRECTION_COUNT_RATE_DEMOTION_RATE_BASE * errorCount / inputLength, &finalFreq);

    // Promotion for an exactly matched word
    if (ed == 0) {
        // Full exact match
        if (sameLength && transposedCount == 0 && !skipped && excessiveCount == 0) {
            finalFreq = capped255MultForFullMatchAccentsOrCapitalizationDifference(finalFreq);
        }
    }

    // Promote a word with no correction
    if (proximityMatchedCount == 0 && transposedCount == 0 && !skipped && excessiveCount == 0) {
        multiplyRate(FULL_MATCHED_WORDS_PROMOTION_RATE, &finalFreq);
    }

    // TODO: Check excessive count and transposed count
    // TODO: Remove this if possible
    /*
         If the last character of the user input word is the same as the next character
         of the output word, and also all of characters of the user input are matched
         to the output word, we'll promote that word a bit because
         that word can be considered the combination of skipped and matched characters.
         This means that the 'sm' pattern wins over the 'ma' pattern.
         e.g.)
         shel -> shell [mmmma] or [mmmsm]
         hel -> hello [mmmaa] or [mmsma]
         m ... matching
         s ... skipping
         a ... traversing all
         t ... transposing
         e ... exceeding
         p ... proximity matching
     */
    if (matchCount == inputLength && matchCount >= 2 && !skipped
            && word[matchCount] == word[matchCount - 1]) {
        multiplyRate(WORDS_WITH_MATCH_SKIP_PROMOTION_RATE, &finalFreq);
    }

    // TODO: Do not use sameLength?
    if (sameLength) {
        multiplyIntCapped(fullWordMultiplier, &finalFreq);
    }

    if (useFullEditDistance && outputLength > inputLength + 1) {
        const int diff = outputLength - inputLength - 1;
        const int divider = diff < 31 ? 1 << diff : S_INT_MAX;
        finalFreq = divider > finalFreq ? 1 : finalFreq / divider;
    }

    if (DEBUG_DICT_FULL) {
        LOGI("calc: %d, %d", outputIndex, sameLength);
    }

    if (DEBUG_CORRECTION_FREQ) {
        DUMP_WORD(correction->mWord, outputIndex + 1);
        LOGI("FinalFreq: [P%d, S%d, T%d, E%d] %d, %d, %d, %d, %d", proximityMatchedCount,
                skippedCount, transposedCount, excessiveCount, lastCharExceeded, sameLength,
                quoteDiffCount, ed, finalFreq);
    }

    return finalFreq;
}

/* static */
int Correction::RankingAlgorithm::calcFreqForSplitTwoWords(
        const int firstFreq, const int secondFreq, const Correction* correction,
        const unsigned short *word) {
    const int spaceProximityPos = correction->mSpaceProximityPos;
    const int missingSpacePos = correction->mMissingSpacePos;
    if (DEBUG_DICT) {
        int inputCount = 0;
        if (spaceProximityPos >= 0) ++inputCount;
        if (missingSpacePos >= 0) ++inputCount;
        assert(inputCount <= 1);
    }
    const bool isSpaceProximity = spaceProximityPos >= 0;
    const int inputLength = correction->mInputLength;
    const int firstWordLength = isSpaceProximity ? spaceProximityPos : missingSpacePos;
    const int secondWordLength = isSpaceProximity ? (inputLength - spaceProximityPos - 1)
            : (inputLength - missingSpacePos);
    const int typedLetterMultiplier = correction->TYPED_LETTER_MULTIPLIER;

    bool firstCapitalizedWordDemotion = false;
    if (firstWordLength >= 2) {
        firstCapitalizedWordDemotion = isUpperCase(word[0]);
    }

    bool secondCapitalizedWordDemotion = false;
    if (secondWordLength >= 2) {
        secondCapitalizedWordDemotion = isUpperCase(word[firstWordLength + 1]);
    }

    const bool capitalizedWordDemotion =
            firstCapitalizedWordDemotion ^ secondCapitalizedWordDemotion;

    if (DEBUG_DICT_FULL) {
        LOGI("Two words: %c, %c, %d", word[0], word[firstWordLength + 1], capitalizedWordDemotion);
    }

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

    if (capitalizedWordDemotion) {
        multiplyRate(TWO_WORDS_CAPITALIZED_DEMOTION_RATE, &totalFreq);
    }

    return totalFreq;
}

} // namespace latinime
