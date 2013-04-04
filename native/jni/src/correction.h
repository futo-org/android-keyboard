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

#ifndef LATINIME_CORRECTION_H
#define LATINIME_CORRECTION_H

#include <cstring> // for memset()

#include "correction_state.h"
#include "defines.h"
#include "proximity_info_state.h"

namespace latinime {

class ProximityInfo;

class Correction {
 public:
    typedef enum {
        TRAVERSE_ALL_ON_TERMINAL,
        TRAVERSE_ALL_NOT_ON_TERMINAL,
        UNRELATED,
        ON_TERMINAL,
        NOT_ON_TERMINAL
    } CorrectionType;

    Correction()
            : mProximityInfo(0), mUseFullEditDistance(false), mDoAutoCompletion(false),
              mMaxEditDistance(0), mMaxDepth(0), mInputSize(0), mSpaceProximityPos(0),
              mMissingSpacePos(0), mTerminalInputIndex(0), mTerminalOutputIndex(0), mMaxErrors(0),
              mTotalTraverseCount(0), mNeedsToTraverseAllNodes(false), mOutputIndex(0),
              mInputIndex(0), mEquivalentCharCount(0), mProximityCount(0), mExcessiveCount(0),
              mTransposedCount(0), mSkippedCount(0), mTransposedPos(0), mExcessivePos(0),
              mSkipPos(0), mLastCharExceeded(false), mMatching(false), mProximityMatching(false),
              mAdditionalProximityMatching(false), mExceeding(false), mTransposing(false),
              mSkipping(false), mProximityInfoState() {
        memset(mWord, 0, sizeof(mWord));
        memset(mDistances, 0, sizeof(mDistances));
        memset(mEditDistanceTable, 0, sizeof(mEditDistanceTable));
        // NOTE: mCorrectionStates is an array of instances.
        // No need to initialize it explicitly here.
    }

    // Non virtual inline destructor -- never inherit this class
    ~Correction() {}
    void resetCorrection();
    void initCorrection(const ProximityInfo *pi, const int inputSize, const int maxDepth);
    void initCorrectionState(const int rootPos, const int childCount, const bool traverseAll);

    // TODO: remove
    void setCorrectionParams(const int skipPos, const int excessivePos, const int transposedPos,
            const int spaceProximityPos, const int missingSpacePos, const bool useFullEditDistance,
            const bool doAutoCompletion, const int maxErrors);
    void checkState() const;
    bool sameAsTyped() const;
    bool initProcessState(const int index);

    int getInputIndex() const;

    bool needsToPrune() const;

    int pushAndGetTotalTraverseCount() {
        return ++mTotalTraverseCount;
    }

    int getFreqForSplitMultipleWords(const int *freqArray, const int *wordLengthArray,
            const int wordCount, const bool isSpaceProximity, const int *word) const;
    int getFinalProbability(const int probability, int **word, int *wordLength);
    int getFinalProbabilityForSubQueue(const int probability, int **word, int *wordLength,
            const int inputSize);

    CorrectionType processCharAndCalcState(const int c, const bool isTerminal);

    /////////////////////////
    // Tree helper methods
    int goDownTree(const int parentIndex, const int childCount, const int firstChildPos);

    inline int getTreeSiblingPos(const int index) const {
        return mCorrectionStates[index].mSiblingPos;
    }

    inline void setTreeSiblingPos(const int index, const int pos) {
        mCorrectionStates[index].mSiblingPos = pos;
    }

    inline int getTreeParentIndex(const int index) const {
        return mCorrectionStates[index].mParentIndex;
    }

    class RankingAlgorithm {
     public:
        static int calculateFinalProbability(const int inputIndex, const int depth,
                const int probability, int *editDistanceTable, const Correction *correction,
                const int inputSize);
        static int calcFreqForSplitMultipleWords(const int *freqArray, const int *wordLengthArray,
                const int wordCount, const Correction *correction, const bool isSpaceProximity,
                const int *word);
        static float calcNormalizedScore(const int *before, const int beforeLength,
                const int *after, const int afterLength, const int score);
        static int editDistance(const int *before, const int beforeLength, const int *after,
                const int afterLength);
     private:
        static const int MAX_INITIAL_SCORE = 255;
    };

    // proximity info state
    void initInputParams(const ProximityInfo *proximityInfo, const int *inputCodes,
            const int inputSize, const int *xCoordinates, const int *yCoordinates) {
        mProximityInfoState.initInputParams(0, static_cast<float>(MAX_VALUE_FOR_WEIGHTING),
                proximityInfo, inputCodes, inputSize, xCoordinates, yCoordinates, 0, 0, false);
    }

    const int *getPrimaryInputWord() const {
        return mProximityInfoState.getPrimaryInputWord();
    }

    int getPrimaryCodePointAt(const int index) const {
        return mProximityInfoState.getPrimaryCodePointAt(index);
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(Correction);

    /////////////////////////
    // static inline utils //
    /////////////////////////
    static const int TWO_31ST_DIV_255 = S_INT_MAX / 255;
    static inline int capped255MultForFullMatchAccentsOrCapitalizationDifference(const int num) {
        return (num < TWO_31ST_DIV_255 ? 255 * num : S_INT_MAX);
    }

    static const int TWO_31ST_DIV_2 = S_INT_MAX / 2;
    AK_FORCE_INLINE static void multiplyIntCapped(const int multiplier, int *base) {
        const int temp = *base;
        if (temp != S_INT_MAX) {
            // Branch if multiplier == 2 for the optimization
            if (multiplier < 0) {
                if (DEBUG_DICT) {
                    ASSERT(false);
                }
                AKLOGI("--- Invalid multiplier: %d", multiplier);
            } else if (multiplier == 0) {
                *base = 0;
            } else if (multiplier == 2) {
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

    AK_FORCE_INLINE static int powerIntCapped(const int base, const int n) {
        if (n <= 0) return 1;
        if (base == 2) {
            return n < 31 ? 1 << n : S_INT_MAX;
        }
        int ret = base;
        for (int i = 1; i < n; ++i) multiplyIntCapped(base, &ret);
        return ret;
    }

    AK_FORCE_INLINE static void multiplyRate(const int rate, int *freq) {
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

    inline int getSpaceProximityPos() const {
        return mSpaceProximityPos;
    }
    inline int getMissingSpacePos() const {
        return mMissingSpacePos;
    }

    inline int getSkipPos() const {
        return mSkipPos;
    }

    inline int getExcessivePos() const {
        return mExcessivePos;
    }

    inline int getTransposedPos() const {
        return mTransposedPos;
    }

    inline void incrementInputIndex();
    inline void incrementOutputIndex();
    inline void startToTraverseAllNodes();
    inline bool isSingleQuote(const int c);
    inline CorrectionType processSkipChar(const int c, const bool isTerminal,
            const bool inputIndexIncremented);
    inline CorrectionType processUnrelatedCorrectionType();
    inline void addCharToCurrentWord(const int c);
    inline int getFinalProbabilityInternal(const int probability, int **word, int *wordLength,
            const int inputSize);

    static const int TYPED_LETTER_MULTIPLIER = 2;
    static const int FULL_WORD_MULTIPLIER = 2;
    const ProximityInfo *mProximityInfo;

    bool mUseFullEditDistance;
    bool mDoAutoCompletion;
    int mMaxEditDistance;
    int mMaxDepth;
    int mInputSize;
    int mSpaceProximityPos;
    int mMissingSpacePos;
    int mTerminalInputIndex;
    int mTerminalOutputIndex;
    int mMaxErrors;

    int mTotalTraverseCount;

    // The following arrays are state buffer.
    int mWord[MAX_WORD_LENGTH];
    int mDistances[MAX_WORD_LENGTH];

    // Edit distance calculation requires a buffer with (N+1)^2 length for the input length N.
    // Caveat: Do not create multiple tables per thread as this table eats up RAM a lot.
    int mEditDistanceTable[(MAX_WORD_LENGTH + 1) * (MAX_WORD_LENGTH + 1)];

    CorrectionState mCorrectionStates[MAX_WORD_LENGTH];

    // The following member variables are being used as cache values of the correction state.
    bool mNeedsToTraverseAllNodes;
    int mOutputIndex;
    int mInputIndex;

    int mEquivalentCharCount;
    int mProximityCount;
    int mExcessiveCount;
    int mTransposedCount;
    int mSkippedCount;

    int mTransposedPos;
    int mExcessivePos;
    int mSkipPos;

    bool mLastCharExceeded;

    bool mMatching;
    bool mProximityMatching;
    bool mAdditionalProximityMatching;
    bool mExceeding;
    bool mTransposing;
    bool mSkipping;
    ProximityInfoState mProximityInfoState;
};

inline void Correction::incrementInputIndex() {
    ++mInputIndex;
}

AK_FORCE_INLINE void Correction::incrementOutputIndex() {
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

inline void Correction::startToTraverseAllNodes() {
    mNeedsToTraverseAllNodes = true;
}

AK_FORCE_INLINE bool Correction::isSingleQuote(const int c) {
    const int userTypedChar = mProximityInfoState.getPrimaryCodePointAt(mInputIndex);
    return (c == KEYCODE_SINGLE_QUOTE && userTypedChar != KEYCODE_SINGLE_QUOTE);
}

AK_FORCE_INLINE Correction::CorrectionType Correction::processSkipChar(const int c,
        const bool isTerminal, const bool inputIndexIncremented) {
    addCharToCurrentWord(c);
    mTerminalInputIndex = mInputIndex - (inputIndexIncremented ? 1 : 0);
    mTerminalOutputIndex = mOutputIndex;
    incrementOutputIndex();
    if (mNeedsToTraverseAllNodes && isTerminal) {
        return TRAVERSE_ALL_ON_TERMINAL;
    }
    return TRAVERSE_ALL_NOT_ON_TERMINAL;
}

inline Correction::CorrectionType Correction::processUnrelatedCorrectionType() {
    // Needs to set mTerminalInputIndex and mTerminalOutputIndex before returning any CorrectionType
    mTerminalInputIndex = mInputIndex;
    mTerminalOutputIndex = mOutputIndex;
    return UNRELATED;
}

AK_FORCE_INLINE static void calcEditDistanceOneStep(int *editDistanceTable, const int *input,
        const int inputSize, const int *output, const int outputLength) {
    // TODO: Make sure that editDistance[0 ~ MAX_WORD_LENGTH] is not touched.
    // Let dp[i][j] be editDistanceTable[i * (inputSize + 1) + j].
    // Assuming that dp[0][0] ... dp[outputLength - 1][inputSize] are already calculated,
    // and calculate dp[ouputLength][0] ... dp[outputLength][inputSize].
    int *const current = editDistanceTable + outputLength * (inputSize + 1);
    const int *const prev = editDistanceTable + (outputLength - 1) * (inputSize + 1);
    const int *const prevprev =
            outputLength >= 2 ? editDistanceTable + (outputLength - 2) * (inputSize + 1) : 0;
    current[0] = outputLength;
    const int co = toBaseLowerCase(output[outputLength - 1]);
    const int prevCO = outputLength >= 2 ? toBaseLowerCase(output[outputLength - 2]) : 0;
    for (int i = 1; i <= inputSize; ++i) {
        const int ci = toBaseLowerCase(input[i - 1]);
        const int cost = (ci == co) ? 0 : 1;
        current[i] = min(current[i - 1] + 1, min(prev[i] + 1, prev[i - 1] + cost));
        if (i >= 2 && prevprev && ci == prevCO && co == toBaseLowerCase(input[i - 2])) {
            current[i] = min(current[i], prevprev[i - 2] + 1);
        }
    }
}

AK_FORCE_INLINE void Correction::addCharToCurrentWord(const int c) {
    mWord[mOutputIndex] = c;
    const int *primaryInputWord = mProximityInfoState.getPrimaryInputWord();
    calcEditDistanceOneStep(mEditDistanceTable, primaryInputWord, mInputSize, mWord,
            mOutputIndex + 1);
}

inline int Correction::getFinalProbabilityInternal(const int probability, int **word,
        int *wordLength, const int inputSize) {
    const int outputIndex = mTerminalOutputIndex;
    const int inputIndex = mTerminalInputIndex;
    *wordLength = outputIndex + 1;
    *word = mWord;
    int finalProbability= Correction::RankingAlgorithm::calculateFinalProbability(
            inputIndex, outputIndex, probability, mEditDistanceTable, this, inputSize);
    return finalProbability;
}

} // namespace latinime
#endif // LATINIME_CORRECTION_H
