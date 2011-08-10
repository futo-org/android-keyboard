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
#include <stdio.h>
#include <string.h>

#define LOG_TAG "LatinIME: correction.cpp"

#include "correction.h"
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
    mSkippedOutputIndex = -1;
}

void Correction::setCorrectionParams(const int skipPos, const int excessivePos,
        const int transposedPos, const int spaceProximityPos, const int missingSpacePos) {
    mSkipPos = skipPos;
    mExcessivePos = excessivePos;
    mTransposedPos = transposedPos;
    mSpaceProximityPos = spaceProximityPos;
    mMissingSpacePos = missingSpacePos;
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

int Correction::getFreqForSplitTwoWords(const int firstFreq, const int secondFreq) {
    return Correction::RankingAlgorithm::calcFreqForSplitTwoWords(firstFreq, secondFreq, this);
}

int Correction::getFinalFreq(const int freq, unsigned short **word, int *wordLength) {
    const int outputIndex = mTerminalOutputIndex;
    const int inputIndex = mTerminalInputIndex;
    *wordLength = outputIndex + 1;
    if (mProximityInfo->sameAsTyped(mWord, outputIndex + 1) || outputIndex < MIN_SUGGEST_DEPTH) {
        return -1;
    }
    *word = mWord;
    const bool sameLength = (mExcessivePos == mInputLength - 1) ? (mInputLength == inputIndex + 2)
            : (mInputLength == inputIndex + 1);
    return Correction::RankingAlgorithm::calculateFinalFreq(
            inputIndex, outputIndex, mMatchedCharCount, freq, sameLength, this);
}

void Correction::initProcessState(const int matchCount, const int inputIndex,
        const int outputIndex, const bool traverseAllNodes, const int diffs) {
    mMatchedCharCount = matchCount;
    mInputIndex = inputIndex;
    mOutputIndex = outputIndex;
    mTraverseAllNodes = traverseAllNodes;
    mDiffs = diffs;
}

void Correction::getProcessState(int *matchedCount, int *inputIndex, int *outputIndex,
        bool *traverseAllNodes, int *diffs) {
    *matchedCount = mMatchedCharCount;
    *inputIndex = mInputIndex;
    *outputIndex = mOutputIndex;
    *traverseAllNodes = mTraverseAllNodes;
    *diffs = mDiffs;
}

void Correction::charMatched() {
    ++mMatchedCharCount;
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
bool Correction::needsToTraverseAll() {
    return mTraverseAllNodes;
}

void Correction::incrementInputIndex() {
    ++mInputIndex;
}

void Correction::incrementOutputIndex() {
    ++mOutputIndex;
}

void Correction::startTraverseAll() {
    mTraverseAllNodes = true;
}

bool Correction::needsToPrune() const {
    return (mOutputIndex - 1 >= (mTransposedPos >= 0 ? mInputLength - 1 : mMaxDepth)
            || mDiffs > mMaxEditDistance);
}

Correction::CorrectionType Correction::processSkipChar(
        const int32_t c, const bool isTerminal) {
    mWord[mOutputIndex] = c;
    if (needsToTraverseAll() && isTerminal) {
        mTerminalInputIndex = mInputIndex;
        mTerminalOutputIndex = mOutputIndex;
        incrementOutputIndex();
        return TRAVERSE_ALL_ON_TERMINAL;
    } else {
        incrementOutputIndex();
        return TRAVERSE_ALL_NOT_ON_TERMINAL;
    }
}

Correction::CorrectionType Correction::processCharAndCalcState(
        const int32_t c, const bool isTerminal) {
    CorrectionType currentStateType = NOT_ON_TERMINAL;
    // This has to be done for each virtual char (this forwards the "inputIndex" which
    // is the index in the user-inputted chars, as read by proximity chars.
    if (mExcessivePos == mOutputIndex && mInputIndex < mInputLength - 1) {
        incrementInputIndex();
    }

    bool skip = false;
    if (mSkipPos >= 0) {
        skip = mSkipPos == mOutputIndex;
    }

    if (mTraverseAllNodes || isQuote(c)) {
        return processSkipChar(c, isTerminal);
    } else {
        int inputIndexForProximity = mInputIndex;

        if (mTransposedPos >= 0) {
            if (mInputIndex == mTransposedPos) {
                ++inputIndexForProximity;
            }
            if (mInputIndex == (mTransposedPos + 1)) {
                --inputIndexForProximity;
            }
        }

        const bool checkProximityChars =
                !(mSkipPos >= 0 || mExcessivePos >= 0 || mTransposedPos >= 0);
        int matchedProximityCharId = mProximityInfo->getMatchedProximityId(
                inputIndexForProximity, c, checkProximityChars);

        const bool unrelated = ProximityInfo::UNRELATED_CHAR == matchedProximityCharId;
        if (unrelated) {
            if (skip) {
                // Skip this letter and continue deeper
                mSkippedOutputIndex = mOutputIndex;
                return processSkipChar(c, isTerminal);
            } else {
                return UNRELATED;
            }
        }

        // No need to skip. Finish traversing and increment skipPos.
        // TODO: Remove this?
        if (skip) {
            mWord[mOutputIndex] = c;
            incrementOutputIndex();
            return TRAVERSE_ALL_NOT_ON_TERMINAL;
        }

        mWord[mOutputIndex] = c;
        // If inputIndex is greater than mInputLength, that means there is no
        // proximity chars. So, we don't need to check proximity.
        if (ProximityInfo::SAME_OR_ACCENTED_OR_CAPITALIZED_CHAR == matchedProximityCharId) {
            charMatched();
        }

        if (ProximityInfo::NEAR_PROXIMITY_CHAR == matchedProximityCharId) {
            incrementDiffs();
        }

        const bool isSameAsUserTypedLength = mInputLength
                == getInputIndex() + 1
                        || (mExcessivePos == mInputLength - 1
                                    && getInputIndex() == mInputLength - 2);
        if (isSameAsUserTypedLength && isTerminal) {
            mTerminalInputIndex = mInputIndex;
            mTerminalOutputIndex = mOutputIndex;
            currentStateType = ON_TERMINAL;
        }
        // Start traversing all nodes after the index exceeds the user typed length
        if (isSameAsUserTypedLength) {
            startTraverseAll();
        }

        // Finally, we are ready to go to the next character, the next "virtual node".
        // We should advance the input index.
        // We do this in this branch of the 'if traverseAllNodes' because we are still matching
        // characters to input; the other branch is not matching them but searching for
        // completions, this is why it does not have to do it.
        incrementInputIndex();
    }

    // Also, the next char is one "virtual node" depth more than this char.
    incrementOutputIndex();

    return currentStateType;
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
            const int tempRetval = temp * multiplier;
            *base = tempRetval >= temp ? tempRetval : S_INT_MAX;
        }
    }
}

inline static int powerIntCapped(const int base, const int n) {
    if (n == 0) return 1;
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

//////////////////////
// RankingAlgorithm //
//////////////////////

int Correction::RankingAlgorithm::calculateFinalFreq(
        const int inputIndex, const int outputIndex,
        const int matchCount, const int freq, const bool sameLength,
        const Correction* correction) {
    const int skipPos = correction->getSkipPos();
    const int excessivePos = correction->getExcessivePos();
    const int transposedPos = correction->getTransposedPos();
    const int inputLength = correction->mInputLength;
    const int typedLetterMultiplier = correction->TYPED_LETTER_MULTIPLIER;
    const int fullWordMultiplier = correction->FULL_WORD_MULTIPLIER;
    const ProximityInfo *proximityInfo = correction->mProximityInfo;
    const int matchWeight = powerIntCapped(typedLetterMultiplier, matchCount);

    // TODO: Demote by edit distance
    int finalFreq = freq * matchWeight;
    if (skipPos >= 0) {
        if (inputLength >= 2) {
            const int demotionRate = WORDS_WITH_MISSING_CHARACTER_DEMOTION_RATE
                    * (10 * inputLength - WORDS_WITH_MISSING_CHARACTER_DEMOTION_START_POS_10X)
                    / (10 * inputLength
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
        if (!proximityInfo->existsAdjacentProximityChars(inputIndex)) {
            // If an excessive character is not adjacent to the left char or the right char,
            // we will demote this word.
            multiplyRate(WORDS_WITH_EXCESSIVE_CHARACTER_OUT_OF_PROXIMITY_DEMOTION_RATE, &finalFreq);
        }
    }
    int lengthFreq = typedLetterMultiplier;
    multiplyIntCapped(powerIntCapped(typedLetterMultiplier, outputIndex), &lengthFreq);
    if ((outputIndex + 1) == matchCount) {
        // Full exact match
        if (outputIndex > 1) {
            if (DEBUG_DICT) {
                LOGI("Found full matched word.");
            }
            multiplyRate(FULL_MATCHED_WORDS_PROMOTION_RATE, &finalFreq);
        }
        if (sameLength && transposedPos < 0 && skipPos < 0 && excessivePos < 0) {
            finalFreq = capped255MultForFullMatchAccentsOrCapitalizationDifference(finalFreq);
        }
    } else if (sameLength && transposedPos < 0 && skipPos < 0 && excessivePos < 0
            && outputIndex > 0) {
        // A word with proximity corrections
        if (DEBUG_DICT) {
            LOGI("Found one proximity correction.");
        }
        multiplyIntCapped(typedLetterMultiplier, &finalFreq);
        multiplyRate(WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE, &finalFreq);
    }
    if (DEBUG_DICT) {
        LOGI("calc: %d, %d", outputIndex, sameLength);
    }
    if (sameLength) multiplyIntCapped(fullWordMultiplier, &finalFreq);
    return finalFreq;
}

int Correction::RankingAlgorithm::calcFreqForSplitTwoWords(
        const int firstFreq, const int secondFreq, const Correction* correction) {
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
    const int secondWordLength = isSpaceProximity
            ? (inputLength - spaceProximityPos - 1)
            : (inputLength - missingSpacePos);
    const int typedLetterMultiplier = correction->TYPED_LETTER_MULTIPLIER;

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

} // namespace latinime
