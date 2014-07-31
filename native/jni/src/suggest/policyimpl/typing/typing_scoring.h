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
#include "suggest/core/dictionary/error_type_utils.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {

class DicNode;
class DicTraverseSession;

class TypingScoring : public Scoring {
 public:
    static const TypingScoring *getInstance() { return &sInstance; }

    AK_FORCE_INLINE void getMostProbableString(const DicTraverseSession *const traverseSession,
            const float languageWeight, SuggestionResults *const outSuggestionResults) const {}

    AK_FORCE_INLINE float getAdjustedLanguageWeight(DicTraverseSession *const traverseSession,
            DicNode *const terminals, const int size) const {
        return 1.0f;
    }

    AK_FORCE_INLINE int calculateFinalScore(const float compoundDistance, const int inputSize,
            const ErrorTypeUtils::ErrorType containedErrorTypes, const bool forceCommit,
            const bool boostExactMatches) const {
        const float maxDistance = ScoringParams::DISTANCE_WEIGHT_LANGUAGE
                + static_cast<float>(inputSize) * ScoringParams::TYPING_MAX_OUTPUT_SCORE_PER_INPUT;
        float score = ScoringParams::TYPING_BASE_OUTPUT_SCORE - compoundDistance / maxDistance;
        if (forceCommit) {
            score += ScoringParams::AUTOCORRECT_OUTPUT_THRESHOLD;
        }
        if (boostExactMatches && ErrorTypeUtils::isExactMatch(containedErrorTypes)) {
            score += ScoringParams::EXACT_MATCH_PROMOTION;
            if ((ErrorTypeUtils::MATCH_WITH_CASE_ERROR & containedErrorTypes) != 0) {
                score -= ScoringParams::CASE_ERROR_PENALTY_FOR_EXACT_MATCH;
            }
            if ((ErrorTypeUtils::MATCH_WITH_ACCENT_ERROR & containedErrorTypes) != 0) {
                score -= ScoringParams::ACCENT_ERROR_PENALTY_FOR_EXACT_MATCH;
            }
            if ((ErrorTypeUtils::MATCH_WITH_DIGRAPH & containedErrorTypes) != 0) {
                score -= ScoringParams::DIGRAPH_PENALTY_FOR_EXACT_MATCH;
            }
        }
        return static_cast<int>(score * SUGGEST_INTERFACE_OUTPUT_SCALE);
    }

    AK_FORCE_INLINE float getDoubleLetterDemotionDistanceCost(
            const DicNode *const terminalDicNode) const {
        return 0.0f;
    }

    AK_FORCE_INLINE bool autoCorrectsToMultiWordSuggestionIfTop() const {
        return true;
    }

    AK_FORCE_INLINE bool sameAsTyped(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return traverseSession->getProximityInfoState(0)->sameAsTyped(
                dicNode->getOutputWordBuf(), dicNode->getNodeCodePointCount());
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(TypingScoring);
    static const TypingScoring sInstance;

    TypingScoring() {}
    ~TypingScoring() {}
};
} // namespace latinime
#endif // LATINIME_TYPING_SCORING_H
