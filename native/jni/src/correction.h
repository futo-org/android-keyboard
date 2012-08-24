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

#include <cassert>
#include <cstring> // for memset()
#include <stdint.h>

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

    virtual ~Correction() {}
    void resetCorrection();
    void initCorrection(
            const ProximityInfo *pi, const int inputSize, const int maxWordLength);
    void initCorrectionState(const int rootPos, const int childCount, const bool traverseAll);

    // TODO: remove
    void setCorrectionParams(const int skipPos, const int excessivePos, const int transposedPos,
            const int spaceProximityPos, const int missingSpacePos, const bool useFullEditDistance,
            const bool doAutoCompletion, const int maxErrors);
    void checkState();
    bool sameAsTyped();
    bool initProcessState(const int index);

    int getInputIndex() const;

    bool needsToPrune() const;

    int pushAndGetTotalTraverseCount() {
        return ++mTotalTraverseCount;
    }

    int getFreqForSplitMultipleWords(
            const int *freqArray, const int *wordLengthArray, const int wordCount,
            const bool isSpaceProximity, const unsigned short *word);
    int getFinalProbability(const int probability, unsigned short **word, int *wordLength);
    int getFinalProbabilityForSubQueue(const int probability, unsigned short **word,
            int *wordLength, const int inputSize);

    CorrectionType processCharAndCalcState(const int32_t c, const bool isTerminal);

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
                const unsigned short *word);
        static float calcNormalizedScore(const unsigned short *before, const int beforeLength,
                const unsigned short *after, const int afterLength, const int score);
        static int editDistance(const unsigned short *before,
                const int beforeLength, const unsigned short *after, const int afterLength);
     private:
        static const int CODE_SPACE = ' ';
        static const int MAX_INITIAL_SCORE = 255;
    };

    // proximity info state
    void initInputParams(const ProximityInfo *proximityInfo, const int32_t *inputCodes,
            const int inputSize, const int *xCoordinates, const int *yCoordinates) {
        mProximityInfoState.initInputParams(0, MAX_POINT_TO_KEY_LENGTH,
                proximityInfo, inputCodes, inputSize, xCoordinates, yCoordinates, 0, 0, false);
    }

    const unsigned short *getPrimaryInputWord() const {
        return mProximityInfoState.getPrimaryInputWord();
    }

    unsigned short getPrimaryCharAt(const int index) const {
        return mProximityInfoState.getPrimaryCharAt(index);
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
    inline static void multiplyIntCapped(const int multiplier, int *base) {
        const int temp = *base;
        if (temp != S_INT_MAX) {
            // Branch if multiplier == 2 for the optimization
            if (multiplier < 0) {
                if (DEBUG_DICT) {
                    assert(false);
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
    inline bool isSingleQuote(const unsigned short c);
    inline CorrectionType processSkipChar(
            const int32_t c, const bool isTerminal, const bool inputIndexIncremented);
    inline CorrectionType processUnrelatedCorrectionType();
    inline void addCharToCurrentWord(const int32_t c);
    inline int getFinalProbabilityInternal(const int probability, unsigned short **word,
            int *wordLength, const int inputSize);

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

    uint8_t mTotalTraverseCount;

    // The following arrays are state buffer.
    unsigned short mWord[MAX_WORD_LENGTH_INTERNAL];
    int mDistances[MAX_WORD_LENGTH_INTERNAL];

    // Edit distance calculation requires a buffer with (N+1)^2 length for the input length N.
    // Caveat: Do not create multiple tables per thread as this table eats up RAM a lot.
    int mEditDistanceTable[(MAX_WORD_LENGTH_INTERNAL + 1) * (MAX_WORD_LENGTH_INTERNAL + 1)];

    CorrectionState mCorrectionStates[MAX_WORD_LENGTH_INTERNAL];

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
} // namespace latinime
#endif // LATINIME_CORRECTION_H
