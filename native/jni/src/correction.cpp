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
#include <math.h>
#include <stdio.h>
#include <string.h>

#define LOG_TAG "LatinIME: correction.cpp"

#include "char_utils.h"
#include "correction.h"
#include "defines.h"
#include "dictionary.h"
#include "proximity_info.h"

namespace latinime {

/////////////////////////////
// edit distance funcitons //
/////////////////////////////

inline static void initEditDistance(int *editDistanceTable) {
    for (int i = 0; i <= MAX_WORD_LENGTH_INTERNAL; ++i) {
        editDistanceTable[i] = i;
    }
}

inline static void dumpEditDistance10ForDebug(int *editDistanceTable,
        const int editDistanceTableWidth, const int outputLength) {
    if (DEBUG_DICT) {
        AKLOGI("EditDistanceTable");
        for (int i = 0; i <= 10; ++i) {
            int c[11];
            for (int j = 0; j <= 10; ++j) {
                if (j < editDistanceTableWidth + 1 && i < outputLength + 1) {
                    c[j] = (editDistanceTable + i * (editDistanceTableWidth + 1))[j];
                } else {
                    c[j] = -1;
                }
            }
            AKLOGI("[ %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d ]",
                    c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10]);
        }
    }
}

inline static void calcEditDistanceOneStep(int *editDistanceTable, const unsigned short *input,
        const int inputLength, const unsigned short *output, const int outputLength) {
    // TODO: Make sure that editDistance[0 ~ MAX_WORD_LENGTH_INTERNAL] is not touched.
    // Let dp[i][j] be editDistanceTable[i * (inputLength + 1) + j].
    // Assuming that dp[0][0] ... dp[outputLength - 1][inputLength] are already calculated,
    // and calculate dp[ouputLength][0] ... dp[outputLength][inputLength].
    int *const current = editDistanceTable + outputLength * (inputLength + 1);
    const int *const prev = editDistanceTable + (outputLength - 1) * (inputLength + 1);
    const int *const prevprev =
            outputLength >= 2 ? editDistanceTable + (outputLength - 2) * (inputLength + 1) : 0;
    current[0] = outputLength;
    const uint32_t co = toBaseLowerCase(output[outputLength - 1]);
    const uint32_t prevCO = outputLength >= 2 ? toBaseLowerCase(output[outputLength - 2]) : 0;
    for (int i = 1; i <= inputLength; ++i) {
        const uint32_t ci = toBaseLowerCase(input[i - 1]);
        const uint16_t cost = (ci == co) ? 0 : 1;
        current[i] = min(current[i - 1] + 1, min(prev[i] + 1, prev[i - 1] + cost));
        if (i >= 2 && prevprev && ci == prevCO && co == toBaseLowerCase(input[i - 2])) {
            current[i] = min(current[i], prevprev[i - 2] + 1);
        }
    }
}

inline static int getCurrentEditDistance(int *editDistanceTable, const int editDistanceTableWidth,
        const int outputLength, const int inputLength) {
    if (DEBUG_EDIT_DISTANCE) {
        AKLOGI("getCurrentEditDistance %d, %d", inputLength, outputLength);
    }
    return editDistanceTable[(editDistanceTableWidth + 1) * (outputLength) + inputLength];
}

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
    initEditDistance(mEditDistanceTable);
}

void Correction::resetCorrection() {
    mTotalTraverseCount = 0;
}

void Correction::initCorrection(const ProximityInfo *pi, const int inputLength,
        const int maxDepth) {
    mProximityInfo = pi;
    mInputLength = inputLength;
    mMaxDepth = maxDepth;
    mMaxEditDistance = mInputLength < 5 ? 2 : mInputLength / 2;
    // TODO: This is not supposed to be required.  Check what's going wrong with
    // editDistance[0 ~ MAX_WORD_LENGTH_INTERNAL]
    initEditDistance(mEditDistanceTable);
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
        const bool useFullEditDistance, const bool doAutoCompletion, const int maxErrors) {
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
    mDoAutoCompletion = doAutoCompletion;
    mMaxErrors = maxErrors;
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

int Correction::getFreqForSplitMultipleWords(const int *freqArray, const int *wordLengthArray,
        const int wordCount, const bool isSpaceProximity, const unsigned short *word) {
    return Correction::RankingAlgorithm::calcFreqForSplitMultipleWords(freqArray, wordLengthArray,
            wordCount, this, isSpaceProximity, word);
}

int Correction::getFinalProbability(const int probability, unsigned short **word, int *wordLength) {
    return getFinalProbabilityInternal(probability, word, wordLength, mInputLength);
}

int Correction::getFinalProbabilityForSubQueue(const int probability, unsigned short **word,
        int *wordLength, const int inputLength) {
    return getFinalProbabilityInternal(probability, word, wordLength, inputLength);
}

int Correction::getFinalProbabilityInternal(const int probability, unsigned short **word,
        int *wordLength, const int inputLength) {
    const int outputIndex = mTerminalOutputIndex;
    const int inputIndex = mTerminalInputIndex;
    *wordLength = outputIndex + 1;
    if (outputIndex < MIN_SUGGEST_DEPTH) {
        return NOT_A_PROBABILITY;
    }

    *word = mWord;
    int finalProbability= Correction::RankingAlgorithm::calculateFinalProbability(
            inputIndex, outputIndex, probability, mEditDistanceTable, this, inputLength);
    return finalProbability;
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
    mAdditionalProximityMatching = false;
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
int Correction::getInputIndex() {
    return mInputIndex;
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
    mCorrectionStates[mOutputIndex].mAdditionalProximityMatching = mAdditionalProximityMatching;
    mCorrectionStates[mOutputIndex].mTransposing = mTransposing;
    mCorrectionStates[mOutputIndex].mExceeding = mExceeding;
    mCorrectionStates[mOutputIndex].mSkipping = mSkipping;
}

void Correction::startToTraverseAllNodes() {
    mNeedsToTraverseAllNodes = true;
}

bool Correction::needsToPrune() const {
    // TODO: use edit distance here
    return mOutputIndex - 1 >= mMaxDepth || mProximityCount > mMaxEditDistance
            // Allow one char longer word for missing character
            || (!mDoAutoCompletion && (mOutputIndex > mInputLength));
}

void Correction::addCharToCurrentWord(const int32_t c) {
    mWord[mOutputIndex] = c;
    const unsigned short *primaryInputWord = mProximityInfo->getPrimaryInputWord();
    calcEditDistanceOneStep(mEditDistanceTable, primaryInputWord, mInputLength,
            mWord, mOutputIndex + 1);
}

Correction::CorrectionType Correction::processSkipChar(
        const int32_t c, const bool isTerminal, const bool inputIndexIncremented) {
    addCharToCurrentWord(c);
    mTerminalInputIndex = mInputIndex - (inputIndexIncremented ? 1 : 0);
    mTerminalOutputIndex = mOutputIndex;
    if (mNeedsToTraverseAllNodes && isTerminal) {
        incrementOutputIndex();
        return TRAVERSE_ALL_ON_TERMINAL;
    } else {
        incrementOutputIndex();
        return TRAVERSE_ALL_NOT_ON_TERMINAL;
    }
}

Correction::CorrectionType Correction::processUnrelatedCorrectionType() {
    // Needs to set mTerminalInputIndex and mTerminalOutputIndex before returning any CorrectionType
    mTerminalInputIndex = mInputIndex;
    mTerminalOutputIndex = mOutputIndex;
    return UNRELATED;
}

inline bool isEquivalentChar(ProximityInfo::ProximityType type) {
    return type == ProximityInfo::EQUIVALENT_CHAR;
}

inline bool isProximityCharOrEquivalentChar(ProximityInfo::ProximityType type) {
    return type == ProximityInfo::EQUIVALENT_CHAR
            || type == ProximityInfo::NEAR_PROXIMITY_CHAR;
}

Correction::CorrectionType Correction::processCharAndCalcState(
        const int32_t c, const bool isTerminal) {
    const int correctionCount = (mSkippedCount + mExcessiveCount + mTransposedCount);
    if (correctionCount > mMaxErrors) {
        return processUnrelatedCorrectionType();
    }

    // TODO: Change the limit if we'll allow two or more corrections
    const bool noCorrectionsHappenedSoFar = correctionCount == 0;
    const bool canTryCorrection = noCorrectionsHappenedSoFar;
    int proximityIndex = 0;
    mDistances[mOutputIndex] = NOT_A_DISTANCE;

    // Skip checking this node
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
            if (!isQuote(c)) {
                incrementInputIndex();
                incremented = true;
            }
        }
        return processSkipChar(c, isTerminal, incremented);
    }

    // Check possible corrections.
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
            if (DEBUG_CORRECTION
                    && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                    && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                            || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
                DUMP_WORD(mWord, mOutputIndex);
                AKLOGI("UNRELATED(0): %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                        mTransposedCount, mExcessiveCount, c);
            }
            return processUnrelatedCorrectionType();
        }
    }

    // TODO: Change the limit if we'll allow two or more proximity chars with corrections
    // Work around: When the mMaxErrors is 1, we only allow just one error
    // including proximity correction.
    const bool checkProximityChars = (mMaxErrors > 1)
            ? (noCorrectionsHappenedSoFar || mProximityCount == 0)
            : (noCorrectionsHappenedSoFar && mProximityCount == 0);

    ProximityInfo::ProximityType matchedProximityCharId = secondTransposing
            ? ProximityInfo::EQUIVALENT_CHAR
            : mProximityInfo->getMatchedProximityId(
                    mInputIndex, c, checkProximityChars, &proximityIndex);

    if (ProximityInfo::UNRELATED_CHAR == matchedProximityCharId
            || ProximityInfo::ADDITIONAL_PROXIMITY_CHAR == matchedProximityCharId) {
        if (canTryCorrection && mOutputIndex > 0
                && mCorrectionStates[mOutputIndex].mProximityMatching
                && mCorrectionStates[mOutputIndex].mExceeding
                && isEquivalentChar(mProximityInfo->getMatchedProximityId(
                        mInputIndex, mWord[mOutputIndex - 1], false))) {
            if (DEBUG_CORRECTION
                    && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                    && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                            || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
                AKLOGI("CONVERSION p->e %c", mWord[mOutputIndex - 1]);
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

    if (ProximityInfo::UNRELATED_CHAR == matchedProximityCharId
            || ProximityInfo::ADDITIONAL_PROXIMITY_CHAR == matchedProximityCharId) {
        if (ProximityInfo::ADDITIONAL_PROXIMITY_CHAR == matchedProximityCharId) {
            mAdditionalProximityMatching = true;
        }
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
        } else if (mInputIndex - 1 < mInputLength
                && mSkippedCount > 0
                && mCorrectionStates[mOutputIndex].mSkipping
                && mCorrectionStates[mOutputIndex].mAdditionalProximityMatching
                && isProximityCharOrEquivalentChar(
                        mProximityInfo->getMatchedProximityId(mInputIndex + 1, c, false))) {
            // Conversion s->a
            incrementInputIndex();
            --mSkippedCount;
            mProximityMatching = true;
            ++mProximityCount;
            mDistances[mOutputIndex] = ADDITIONAL_PROXIMITY_CHAR_DISTANCE_INFO;
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
            if (DEBUG_CORRECTION
                    && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                    && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                            || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
                DUMP_WORD(mWord, mOutputIndex);
                if (mTransposing) {
                    AKLOGI("TRANSPOSE: %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                            mTransposedCount, mExcessiveCount, c);
                } else {
                    AKLOGI("EXCEED: %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                            mTransposedCount, mExcessiveCount, c);
                }
            }
        } else if (mSkipping) {
            // 3. Skip correction
            ++mSkippedCount;
            if (DEBUG_CORRECTION
                    && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                    && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                            || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
                AKLOGI("SKIP: %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                        mTransposedCount, mExcessiveCount, c);
            }
            return processSkipChar(c, isTerminal, false);
        } else if (ProximityInfo::ADDITIONAL_PROXIMITY_CHAR == matchedProximityCharId) {
            // As a last resort, use additional proximity characters
            mProximityMatching = true;
            ++mProximityCount;
            mDistances[mOutputIndex] = ADDITIONAL_PROXIMITY_CHAR_DISTANCE_INFO;
            if (DEBUG_CORRECTION
                    && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                    && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                            || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
                AKLOGI("ADDITIONALPROX: %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                        mTransposedCount, mExcessiveCount, c);
            }
        } else {
            if (DEBUG_CORRECTION
                    && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                    && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                            || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
                DUMP_WORD(mWord, mOutputIndex);
                AKLOGI("UNRELATED(1): %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                        mTransposedCount, mExcessiveCount, c);
            }
            return processUnrelatedCorrectionType();
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
        if (DEBUG_CORRECTION
                && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0
                        || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
            AKLOGI("PROX: %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                    mTransposedCount, mExcessiveCount, c);
        }
    }

    addCharToCurrentWord(c);

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
        if (DEBUG_CORRECTION
                && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == mInputLength)
                && (MIN_OUTPUT_INDEX_FOR_DEBUG <= 0 || MIN_OUTPUT_INDEX_FOR_DEBUG < mOutputIndex)) {
            DUMP_WORD(mWord, mOutputIndex);
            AKLOGI("ONTERMINAL(1): %d, %d, %d, %d, %c", mProximityCount, mSkippedCount,
                    mTransposedCount, mExcessiveCount, c);
        }
        return ON_TERMINAL;
    } else {
        mTerminalInputIndex = mInputIndex - 1;
        mTerminalOutputIndex = mOutputIndex - 1;
        return NOT_ON_TERMINAL;
    }
}

Correction::~Correction() {
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
    return isAsciiUpper(toBaseChar(c));
}

//////////////////////
// RankingAlgorithm //
//////////////////////

/* static */
int Correction::RankingAlgorithm::calculateFinalProbability(const int inputIndex,
        const int outputIndex, const int freq, int* editDistanceTable, const Correction* correction,
        const int inputLength) {
    const int excessivePos = correction->getExcessivePos();
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

    const int quoteDiffCount = max(0, getQuoteCount(word, outputLength)
            - getQuoteCount(proximityInfo->getPrimaryInputWord(), inputLength));

    // TODO: Calculate edit distance for transposed and excessive
    int ed = 0;
    if (DEBUG_DICT_FULL) {
        dumpEditDistance10ForDebug(editDistanceTable, correction->mInputLength, outputLength);
    }
    int adjustedProximityMatchedCount = proximityMatchedCount;

    int finalFreq = freq;

    if (DEBUG_CORRECTION_FREQ
            && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == inputLength)) {
        AKLOGI("FinalFreq0: %d", finalFreq);
    }
    // TODO: Optimize this.
    if (transposedCount > 0 || proximityMatchedCount > 0 || skipped || excessiveCount > 0) {
        ed = getCurrentEditDistance(editDistanceTable, correction->mInputLength, outputLength,
                inputLength) - transposedCount;

        const int matchWeight = powerIntCapped(typedLetterMultiplier,
                max(inputLength, outputLength) - ed);
        multiplyIntCapped(matchWeight, &finalFreq);

        // TODO: Demote further if there are two or more excessive chars with longer user input?
        if (inputLength > outputLength) {
            multiplyRate(INPUT_EXCEEDS_OUTPUT_DEMOTION_RATE, &finalFreq);
        }

        ed = max(0, ed - quoteDiffCount);
        adjustedProximityMatchedCount = min(max(0, ed - (outputLength - inputLength)),
                proximityMatchedCount);
        if (transposedCount <= 0) {
            if (ed == 1 && (inputLength == outputLength - 1 || inputLength == outputLength + 1)) {
                // Promote a word with just one skipped or excessive char
                if (sameLength) {
                    multiplyRate(WORDS_WITH_JUST_ONE_CORRECTION_PROMOTION_RATE
                            + WORDS_WITH_JUST_ONE_CORRECTION_PROMOTION_MULTIPLIER * outputLength,
                            &finalFreq);
                } else {
                    multiplyIntCapped(typedLetterMultiplier, &finalFreq);
                }
            } else if (ed == 0) {
                multiplyIntCapped(typedLetterMultiplier, &finalFreq);
                sameLength = true;
            }
        }
    } else {
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
            AKLOGI("Demotion rate for missing character is %d.", demotionRate);
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
            if (DEBUG_DICT_FULL) {
                AKLOGI("Double excessive demotion");
            }
            // If an excessive character is not adjacent to the left char or the right char,
            // we will demote this word.
            multiplyRate(WORDS_WITH_EXCESSIVE_CHARACTER_OUT_OF_PROXIMITY_DEMOTION_RATE, &finalFreq);
        }
    }

    const bool performTouchPositionCorrection =
            CALIBRATE_SCORE_BY_TOUCH_COORDINATES && proximityInfo->touchPositionCorrectionEnabled()
                        && skippedCount == 0 && excessiveCount == 0 && transposedCount == 0;
    // Score calibration by touch coordinates is being done only for pure-fat finger typing error
    // cases.
    int additionalProximityCount = 0;
    // TODO: Remove this constraint.
    if (performTouchPositionCorrection) {
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
                static const float MIN = 0.3f;
                static const float R1 = NEUTRAL_SCORE_SQUARED_RADIUS;
                static const float R2 = HALF_SCORE_SQUARED_RADIUS;
                const float x = (float)squaredDistance
                        / ProximityInfo::NORMALIZED_SQUARED_DISTANCE_SCALING_FACTOR;
                const float factor = max((x < R1)
                    ? (A * (R1 - x) + B * x) / R1
                    : (B * (R2 - x) + C * (x - R1)) / (R2 - R1), MIN);
                // factor is piecewise linear function like:
                // A -_                  .
                //     ^-_               .
                // B      \              .
                //         \_            .
                // C         ------------.
                //                       .
                // 0   R1 R2             .
                multiplyRate((int)(factor * 100), &finalFreq);
            } else if (squaredDistance == PROXIMITY_CHAR_WITHOUT_DISTANCE_INFO) {
                multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
            } else if (squaredDistance == ADDITIONAL_PROXIMITY_CHAR_DISTANCE_INFO) {
                ++additionalProximityCount;
                multiplyRate(WORDS_WITH_ADDITIONAL_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
            }
        }
    } else {
        // Demote additional proximity characters
        for (int i = 0; i < outputLength; ++i) {
            const int squaredDistance = correction->mDistances[i];
            if (squaredDistance == ADDITIONAL_PROXIMITY_CHAR_DISTANCE_INFO) {
                ++additionalProximityCount;
            }
        }
        // Promotion for a word with proximity characters
        for (int i = 0; i < adjustedProximityMatchedCount; ++i) {
            // A word with proximity corrections
            if (DEBUG_DICT_FULL) {
                AKLOGI("Found a proximity correction.");
            }
            multiplyIntCapped(typedLetterMultiplier, &finalFreq);
            if (i < additionalProximityCount) {
                multiplyRate(WORDS_WITH_ADDITIONAL_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
            } else {
                multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
            }
        }
    }

    // If the user types too many(three or more) proximity characters with additional proximity
    // character,do not treat as the same length word.
    if (sameLength && additionalProximityCount > 0 && (adjustedProximityMatchedCount >= 3
            || transposedCount > 0 || skipped || excessiveCount > 0)) {
        sameLength = false;
    }

    const int errorCount = adjustedProximityMatchedCount > 0
            ? adjustedProximityMatchedCount
            : (proximityMatchedCount + transposedCount);
    multiplyRate(
            100 - CORRECTION_COUNT_RATE_DEMOTION_RATE_BASE * errorCount / inputLength, &finalFreq);

    // Promotion for an exactly matched word
    if (ed == 0) {
        // Full exact match
        if (sameLength && transposedCount == 0 && !skipped && excessiveCount == 0
                && quoteDiffCount == 0 && additionalProximityCount == 0) {
            finalFreq = capped255MultForFullMatchAccentsOrCapitalizationDifference(finalFreq);
        }
    }

    // Promote a word with no correction
    if (proximityMatchedCount == 0 && transposedCount == 0 && !skipped && excessiveCount == 0
            && additionalProximityCount == 0) {
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
        AKLOGI("calc: %d, %d", outputLength, sameLength);
    }

    if (DEBUG_CORRECTION_FREQ
            && (INPUTLENGTH_FOR_DEBUG <= 0 || INPUTLENGTH_FOR_DEBUG == inputLength)) {
        DUMP_WORD(proximityInfo->getPrimaryInputWord(), inputLength);
        DUMP_WORD(correction->mWord, outputLength);
        AKLOGI("FinalFreq: [P%d, S%d, T%d, E%d, A%d] %d, %d, %d, %d, %d, %d", proximityMatchedCount,
                skippedCount, transposedCount, excessiveCount, additionalProximityCount,
                outputLength, lastCharExceeded, sameLength, quoteDiffCount, ed, finalFreq);
    }

    return finalFreq;
}

/* static */
int Correction::RankingAlgorithm::calcFreqForSplitMultipleWords(
        const int *freqArray, const int *wordLengthArray, const int wordCount,
        const Correction* correction, const bool isSpaceProximity, const unsigned short *word) {
    const int typedLetterMultiplier = correction->TYPED_LETTER_MULTIPLIER;

    bool firstCapitalizedWordDemotion = false;
    bool secondCapitalizedWordDemotion = false;

    {
        // TODO: Handle multiple capitalized word demotion properly
        const int firstWordLength = wordLengthArray[0];
        const int secondWordLength = wordLengthArray[1];
        if (firstWordLength >= 2) {
            firstCapitalizedWordDemotion = isUpperCase(word[0]);
        }

        if (secondWordLength >= 2) {
            // FIXME: word[firstWordLength + 1] is incorrect.
            secondCapitalizedWordDemotion = isUpperCase(word[firstWordLength + 1]);
        }
    }


    const bool capitalizedWordDemotion =
            firstCapitalizedWordDemotion ^ secondCapitalizedWordDemotion;

    int totalLength = 0;
    int totalFreq = 0;
    for (int i = 0; i < wordCount; ++i){
        const int wordLength = wordLengthArray[i];
        if (wordLength <= 0) {
            return 0;
        }
        totalLength += wordLength;
        const int demotionRate = 100 - TWO_WORDS_CORRECTION_DEMOTION_BASE / (wordLength + 1);
        int tempFirstFreq = freqArray[i];
        multiplyRate(demotionRate, &tempFirstFreq);
        totalFreq += tempFirstFreq;
    }

    if (totalLength <= 0 || totalFreq <= 0) {
        return 0;
    }

    // TODO: Currently totalFreq is adjusted to two word metrix.
    // Promote pairFreq with multiplying by 2, because the word length is the same as the typed
    // length.
    totalFreq = totalFreq * 2 / wordCount;
    if (wordCount > 2) {
        // Safety net for 3+ words -- Caveats: many heuristics and workarounds here.
        int oneLengthCounter = 0;
        int twoLengthCounter = 0;
        for (int i = 0; i < wordCount; ++i) {
            const int wordLength = wordLengthArray[i];
            // TODO: Use bigram instead of this safety net
            if (i < wordCount - 1) {
                const int nextWordLength = wordLengthArray[i + 1];
                if (wordLength == 1 && nextWordLength == 2) {
                    // Safety net to filter 1 length and 2 length sequential words
                    return 0;
                }
            }
            const int freq = freqArray[i];
            // Demote too short weak words
            if (wordLength <= 4 && freq <= SUPPRESS_SHORT_MULTIPLE_WORDS_THRESHOLD_FREQ) {
                multiplyRate(100 * freq / MAX_FREQ, &totalFreq);
            }
            if (wordLength == 1) {
                ++oneLengthCounter;
            } else if (wordLength == 2) {
                ++twoLengthCounter;
            }
            if (oneLengthCounter >= 2 || (oneLengthCounter + twoLengthCounter) >= 4) {
                // Safety net to filter too many short words
                return 0;
            }
        }
        multiplyRate(MULTIPLE_WORDS_DEMOTION_RATE, &totalFreq);
    }

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
            AKLOGI("Found a word pair with space proximity correction.");
        }
        multiplyIntCapped(typedLetterMultiplier, &totalFreq);
        multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &totalFreq);
    }

    if (isSpaceProximity) {
        multiplyRate(WORDS_WITH_MISTYPED_SPACE_DEMOTION_RATE, &totalFreq);
    } else {
        multiplyRate(WORDS_WITH_MISSING_SPACE_CHARACTER_DEMOTION_RATE, &totalFreq);
    }

    if (capitalizedWordDemotion) {
        multiplyRate(TWO_WORDS_CAPITALIZED_DEMOTION_RATE, &totalFreq);
    }

    if (DEBUG_CORRECTION_FREQ) {
        AKLOGI("Multiple words (%d, %d) (%d, %d) %d, %d", freqArray[0], freqArray[1],
                wordLengthArray[0], wordLengthArray[1], capitalizedWordDemotion, totalFreq);
        DUMP_WORD(word, wordLengthArray[0]);
    }

    return totalFreq;
}

/* Damerau-Levenshtein distance */
inline static int editDistanceInternal(
        int* editDistanceTable, const unsigned short* before,
        const int beforeLength, const unsigned short* after, const int afterLength) {
    // dp[li][lo] dp[a][b] = dp[ a * lo + b]
    int* dp = editDistanceTable;
    const int li = beforeLength + 1;
    const int lo = afterLength + 1;
    for (int i = 0; i < li; ++i) {
        dp[lo * i] = i;
    }
    for (int i = 0; i < lo; ++i) {
        dp[i] = i;
    }

    for (int i = 0; i < li - 1; ++i) {
        for (int j = 0; j < lo - 1; ++j) {
            const uint32_t ci = toBaseLowerCase(before[i]);
            const uint32_t co = toBaseLowerCase(after[j]);
            const uint16_t cost = (ci == co) ? 0 : 1;
            dp[(i + 1) * lo + (j + 1)] = min(dp[i * lo + (j + 1)] + 1,
                    min(dp[(i + 1) * lo + j] + 1, dp[i * lo + j] + cost));
            if (i > 0 && j > 0 && ci == toBaseLowerCase(after[j - 1])
                    && co == toBaseLowerCase(before[i - 1])) {
                dp[(i + 1) * lo + (j + 1)] = min(
                        dp[(i + 1) * lo + (j + 1)], dp[(i - 1) * lo + (j - 1)] + cost);
            }
        }
    }

    if (DEBUG_EDIT_DISTANCE) {
        AKLOGI("IN = %d, OUT = %d", beforeLength, afterLength);
        for (int i = 0; i < li; ++i) {
            for (int j = 0; j < lo; ++j) {
                AKLOGI("EDIT[%d][%d], %d", i, j, dp[i * lo + j]);
            }
        }
    }
    return dp[li * lo - 1];
}

int Correction::RankingAlgorithm::editDistance(const unsigned short* before,
        const int beforeLength, const unsigned short* after, const int afterLength) {
    int table[(beforeLength + 1) * (afterLength + 1)];
    return editDistanceInternal(table, before, beforeLength, after, afterLength);
}


// In dictionary.cpp, getSuggestion() method,
// suggestion scores are computed using the below formula.
// original score
//  := pow(mTypedLetterMultiplier (this is defined 2),
//         (the number of matched characters between typed word and suggested word))
//     * (individual word's score which defined in the unigram dictionary,
//         and this score is defined in range [0, 255].)
// Then, the following processing is applied.
//     - If the dictionary word is matched up to the point of the user entry
//       (full match up to min(before.length(), after.length())
//       => Then multiply by FULL_MATCHED_WORDS_PROMOTION_RATE (this is defined 1.2)
//     - If the word is a true full match except for differences in accents or
//       capitalization, then treat it as if the score was 255.
//     - If before.length() == after.length()
//       => multiply by mFullWordMultiplier (this is defined 2))
// So, maximum original score is pow(2, min(before.length(), after.length())) * 255 * 2 * 1.2
// For historical reasons we ignore the 1.2 modifier (because the measure for a good
// autocorrection threshold was done at a time when it didn't exist). This doesn't change
// the result.
// So, we can normalize original score by dividing pow(2, min(b.l(),a.l())) * 255 * 2.

/* static */
float Correction::RankingAlgorithm::calcNormalizedScore(const unsigned short* before,
        const int beforeLength, const unsigned short* after, const int afterLength,
        const int score) {
    if (0 == beforeLength || 0 == afterLength) {
        return 0;
    }
    const int distance = editDistance(before, beforeLength, after, afterLength);
    int spaceCount = 0;
    for (int i = 0; i < afterLength; ++i) {
        if (after[i] == CODE_SPACE) {
            ++spaceCount;
        }
    }

    if (spaceCount == afterLength) {
        return 0;
    }

    const float maxScore = score >= S_INT_MAX ? S_INT_MAX : MAX_INITIAL_SCORE
            * pow((float)TYPED_LETTER_MULTIPLIER,
                    (float)min(beforeLength, afterLength - spaceCount)) * FULL_WORD_MULTIPLIER;

    // add a weight based on edit distance.
    // distance <= max(afterLength, beforeLength) == afterLength,
    // so, 0 <= distance / afterLength <= 1
    const float weight = 1.0 - (float) distance / afterLength;
    return (score / maxScore) * weight;
}

} // namespace latinime
