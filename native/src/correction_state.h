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
    CorrectionState(const int typedLetterMultiplier, const int fullWordMultiplier);
    void initCorrectionState(const ProximityInfo *pi, const int inputLength);
    void setCorrectionParams(const int skipPos, const int excessivePos, const int transposedPos,
            const int spaceProximityPos, const int missingSpacePos);
    void initDepth();
    void checkState();
    void goUpTree(const int matchCount);
    void slideTree(const int matchCount);
    void goDownTree(int *matchedCount);
    void charMatched();
    virtual ~CorrectionState();
    int getSkipPos() const {
        return mSkipPos;
    }
    int getExcessivePos() const {
        return mExcessivePos;
    }
    int getTransposedPos() const {
        return mTransposedPos;
    }
    int getSpaceProximityPos() const {
        return mSpaceProximityPos;
    }
    int getMissingSpacePos() const {
        return mMissingSpacePos;
    }
    int getFreqForSplitTwoWords(const int firstFreq, const int secondFreq);
    int getFinalFreq(const int inputIndex, const int outputIndex, const int freq);

private:

    const int TYPED_LETTER_MULTIPLIER;
    const int FULL_WORD_MULTIPLIER;

    const ProximityInfo *mProximityInfo;
    int mInputLength;
    int mSkipPos;
    int mExcessivePos;
    int mTransposedPos;
    int mSpaceProximityPos;
    int mMissingSpacePos;

    int mMatchedCharCount;

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
