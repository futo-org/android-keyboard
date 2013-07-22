/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef LATINIME_TYPING_SCORING_H
#define LATINIME_TYPING_SCORING_H

#include "defines.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {

class DicNode;
class DicTraverseSession;

class TypingScoring : public Scoring {
 public:
    static const TypingScoring *getInstance() { return &sInstance; }

    AK_FORCE_INLINE bool getMostProbableString(
            const DicTraverseSession *const traverseSession, const int terminalSize,
            const float languageWeight, int *const outputCodePoints, int *const type,
            int *const freq) const {
        return false;
    }

    AK_FORCE_INLINE void safetyNetForMostProbableString(const int terminalSize,
            const int maxScore, int *const outputCodePoints, int *const frequencies) const {
    }

    AK_FORCE_INLINE void searchWordWithDoubleLetter(DicNode *terminals,
            const int terminalSize, int *doubleLetterTerminalIndex,
            DoubleLetterLevel *doubleLetterLevel) const {
    }

    AK_FORCE_INLINE float getAdjustedLanguageWeight(DicTraverseSession *const traverseSession,
             DicNode *const terminals, const int size) const {
        return 1.0f;
    }

    AK_FORCE_INLINE int calculateFinalScore(const float compoundDistance,
            const int inputSize, const bool forceCommit) const {
        const float maxDistance = ScoringParams::DISTANCE_WEIGHT_LANGUAGE
                + static_cast<float>(inputSize) * ScoringParams::TYPING_MAX_OUTPUT_SCORE_PER_INPUT;
        const float score = ScoringParams::TYPING_BASE_OUTPUT_SCORE
                - compoundDistance / maxDistance
                + (forceCommit ? ScoringParams::AUTOCORRECT_OUTPUT_THRESHOLD : 0.0f);
        return static_cast<int>(score * SUGGEST_INTERFACE_OUTPUT_SCALE);
    }

    AK_FORCE_INLINE float getDoubleLetterDemotionDistanceCost(const int terminalIndex,
            const int doubleLetterTerminalIndex,
            const DoubleLetterLevel doubleLetterLevel) const {
        return 0.0f;
    }

    AK_FORCE_INLINE bool doesAutoCorrectValidWord() const {
        return false;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(TypingScoring);
    static const TypingScoring sInstance;

    TypingScoring() {}
    ~TypingScoring() {}
};
} // namespace latinime
#endif // LATINIME_TYPING_SCORING_H
