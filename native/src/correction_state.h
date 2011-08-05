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

#ifndef LATINIME_CORRECTION_STATE_H
#define LATINIME_CORRECTION_STATE_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

class ProximityInfo;

class CorrectionState {

public:
    typedef enum {
        TRAVERSE_ALL_ON_TERMINAL,
        TRAVERSE_ALL_NOT_ON_TERMINAL,
        UNRELATED,
        ON_TERMINAL,
        NOT_ON_TERMINAL
    } CorrectionStateType;

    CorrectionState(const int typedLetterMultiplier, const int fullWordMultiplier);
    void initCorrectionState(
            const ProximityInfo *pi, const int inputLength, const int maxWordLength);
    void setCorrectionParams(const int skipPos, const int excessivePos, const int transposedPos,
            const int spaceProximityPos, const int missingSpacePos);
    void checkState();
    void initProcessState(const int matchCount, const int inputIndex, const int outputIndex,
            const bool traverseAllNodes, const int diffs);
    void getProcessState(int *matchedCount, int *inputIndex, int *outputIndex,
            bool *traverseAllNodes, int *diffs);
    int getOutputIndex();
    int getInputIndex();
    bool needsToTraverseAll();

    virtual ~CorrectionState();
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

    int getFreqForSplitTwoWords(const int firstFreq, const int secondFreq);
    int getFinalFreq(const int freq, unsigned short **word, int* wordLength);

    CorrectionStateType processCharAndCalcState(const int32_t c, const bool isTerminal);

    int getDiffs() const {
        return mDiffs;
    }
private:
    void charMatched();
    void incrementInputIndex();
    void incrementOutputIndex();
    void startTraverseAll();

    // TODO: remove

    void incrementDiffs() {
        ++mDiffs;
    }

    const int TYPED_LETTER_MULTIPLIER;
    const int FULL_WORD_MULTIPLIER;

    const ProximityInfo *mProximityInfo;

    int mMaxEditDistance;
    int mMaxDepth;
    int mInputLength;
    int mSkipPos;
    int mExcessivePos;
    int mTransposedPos;
    int mSpaceProximityPos;
    int mMissingSpacePos;

    int mMatchedCharCount;
    int mInputIndex;
    int mOutputIndex;
    int mDiffs;
    bool mTraverseAllNodes;
    CorrectionStateType mCurrentStateType;
    unsigned short mWord[MAX_WORD_LENGTH_INTERNAL];

    inline bool needsToSkipCurrentNode(const unsigned short c);

    class RankingAlgorithm {
    public:
        static int calculateFinalFreq(const int inputIndex, const int depth,
                const int matchCount, const int freq, const bool sameLength,
                const CorrectionState* correctionState);
        static int calcFreqForSplitTwoWords(const int firstFreq, const int secondFreq,
                const CorrectionState* correctionState);
    };
};
} // namespace latinime
#endif // LATINIME_CORRECTION_INFO_H
