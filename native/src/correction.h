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

#include <stdint.h>
#include "correction_state.h"

#include "defines.h"

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

    Correction(const int typedLetterMultiplier, const int fullWordMultiplier);
    void initCorrection(
            const ProximityInfo *pi, const int inputLength, const int maxWordLength);
    void initCorrectionState(const int rootPos, const int childCount, const bool traverseAll);

    // TODO: remove
    void setCorrectionParams(const int skipPos, const int excessivePos, const int transposedPos,
            const int spaceProximityPos, const int missingSpacePos, const bool useFullEditDistance);
    void checkState();
    bool initProcessState(const int index);

    int getOutputIndex();
    int getInputIndex();

    virtual ~Correction();
    int getSpaceProximityPos() const {
        return mSpaceProximityPos;
    }
    int getMissingSpacePos() const {
        return mMissingSpacePos;
    }

    int getSkipPos() const {
        return mSkipPos;
    }

    int getExcessivePos() const {
        return mExcessivePos;
    }

    int getTransposedPos() const {
        return mTransposedPos;
    }

    bool needsToPrune() const;

    int getFreqForSplitTwoWords(
            const int firstFreq, const int secondFreq, const unsigned short *word);
    int getFinalFreq(const int freq, unsigned short **word, int* wordLength);

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
private:
    inline void incrementInputIndex();
    inline void incrementOutputIndex();
    inline bool needsToTraverseAllNodes();
    inline void startToTraverseAllNodes();
    inline bool isQuote(const unsigned short c);
    inline CorrectionType processSkipChar(
            const int32_t c, const bool isTerminal, const bool inputIndexIncremented);

    const int TYPED_LETTER_MULTIPLIER;
    const int FULL_WORD_MULTIPLIER;
    const ProximityInfo *mProximityInfo;

    bool mUseFullEditDistance;
    int mMaxEditDistance;
    int mMaxDepth;
    int mInputLength;
    int mSpaceProximityPos;
    int mMissingSpacePos;
    int mTerminalInputIndex;
    int mTerminalOutputIndex;

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
    bool mExceeding;
    bool mTransposing;
    bool mSkipping;

    class RankingAlgorithm {
    public:
        static int calculateFinalFreq(const int inputIndex, const int depth,
                const int freq, int *editDistanceTable, const Correction* correction);
        static int calcFreqForSplitTwoWords(const int firstFreq, const int secondFreq,
                const Correction* correction, const unsigned short *word);
    };
};
} // namespace latinime
#endif // LATINIME_CORRECTION_H
