#pragma once

#include "suggest/core/dictionary/error_type_utils.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {
class SwipeScoring : public Scoring {
public:
    static const SwipeScoring *getInstance() { return &sInstance; }

    AK_FORCE_INLINE int calculateFinalScore(const float compoundDistance, const int inputSize,
                                            const ErrorTypeUtils::ErrorType containedErrorTypes, const bool forceCommit,
                                            const bool boostExactMatches, const bool hasProbabilityZero) const override {
        const float maxDistance = ScoringParams::DISTANCE_WEIGHT_LANGUAGE
                                  + static_cast<float>(inputSize) * ScoringParams::TYPING_MAX_OUTPUT_SCORE_PER_INPUT;
        float score = (ScoringParams::TYPING_BASE_OUTPUT_SCORE - compoundDistance / maxDistance);
        if (forceCommit) {
            score += ScoringParams::AUTOCORRECT_OUTPUT_THRESHOLD;
        }
        if (hasProbabilityZero) {
            // Previously, when both legitimate 0-frequency words (such as distracters) and
            // offensive words were encoded in the same way, distracters would never show up
            // when the user blocked offensive words (the default setting, as well as the
            // setting for regression tests).
            //
            // When b/11031090 was fixed and a separate encoding was used for offensive words,
            // 0-frequency words would no longer be blocked when they were an "exact match"
            // (where case mismatches and accent mismatches would be considered an "exact
            // match"). The exact match boosting functionality meant that, for example, when
            // the user typed "mt" they would be suggested the word "Mt", although they most
            // probably meant to type "my".
            //
            // For this reason, we introduced this change, which does the following:
            // * Defines the "perfect match" as a really exact match, with no room for case or
            // accent mismatches
            // * When the target word has probability zero (as "Mt" does, because it is a
            // distracter), ONLY boost its score if it is a perfect match.
            //
            // By doing this, when the user types "mt", the word "Mt" will NOT be boosted, and
            // they will get "my". However, if the user makes an explicit effort to type "Mt",
            // we do boost the word "Mt" so that the user's input is not autocorrected to "My".
            if (boostExactMatches && ErrorTypeUtils::isPerfectMatch(containedErrorTypes)) {
                score += ScoringParams::PERFECT_MATCH_PROMOTION;
            }
        } else {
            if (boostExactMatches && ErrorTypeUtils::isExactMatch(containedErrorTypes)) {
                score += ScoringParams::EXACT_MATCH_PROMOTION;
                if ((ErrorTypeUtils::MATCH_WITH_WRONG_CASE & containedErrorTypes) != 0) {
                    score -= ScoringParams::CASE_ERROR_PENALTY_FOR_EXACT_MATCH;
                }
                if ((ErrorTypeUtils::MATCH_WITH_MISSING_ACCENT & containedErrorTypes) != 0) {
                    score -= ScoringParams::ACCENT_ERROR_PENALTY_FOR_EXACT_MATCH;
                }
                if ((ErrorTypeUtils::MATCH_WITH_DIGRAPH & containedErrorTypes) != 0) {
                    score -= ScoringParams::DIGRAPH_PENALTY_FOR_EXACT_MATCH;
                }
            }
        }
        return static_cast<int>(score * 10.0f);
    }

    AK_FORCE_INLINE void getMostProbableString(const DicTraverseSession *const traverseSession,
                               const float weightOfLangModelVsSpatialModel,
                               SuggestionResults *const outSuggestionResults) const override {
    }

    AK_FORCE_INLINE float getAdjustedWeightOfLangModelVsSpatialModel(
            DicTraverseSession *const traverseSession, DicNode *const terminals,
            const int size) const override {
        return MAX_VALUE_FOR_WEIGHTING;
    }

    AK_FORCE_INLINE float getDoubleLetterDemotionDistanceCost(
            const DicNode *const terminalDicNode) const override {
        return 0.0f;
    }

    AK_FORCE_INLINE bool autoCorrectsToMultiWordSuggestionIfTop() const override {
        return false;
    }

    AK_FORCE_INLINE bool sameAsTyped(const DicTraverseSession *const traverseSession,
                     const DicNode *const dicNode) const override {
        return false;
    }

private:
    DISALLOW_COPY_AND_ASSIGN(SwipeScoring);
    static const SwipeScoring sInstance;

    SwipeScoring() {}
    ~SwipeScoring() {}
};
};