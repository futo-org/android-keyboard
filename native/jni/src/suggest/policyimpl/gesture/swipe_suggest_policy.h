#pragma once

#include "defines.h"
#include "suggest/core/policy/suggest_policy.h"
#include "suggest/policyimpl/gesture/swipe_scoring.h"
#include "suggest/policyimpl/gesture/swipe_traversal.h"
#include "suggest/policyimpl/gesture/swipe_weighting.h"

namespace latinime {

    class Scoring;
    class Traversal;
    class Weighting;

    class SwipeSuggestPolicy : public SuggestPolicy {
    public:
        static const SwipeSuggestPolicy *getInstance() { return &sInstance; }

        SwipeSuggestPolicy() {}
        virtual ~SwipeSuggestPolicy() {}
        AK_FORCE_INLINE const Traversal *getTraversal() const {
            return SwipeTraversal::getInstance();
        }

        AK_FORCE_INLINE const Scoring *getScoring() const {
            return SwipeScoring::getInstance();
        }

        AK_FORCE_INLINE const Weighting *getWeighting() const {
            return SwipeWeighting::getInstance();
        }

    private:
        DISALLOW_COPY_AND_ASSIGN(SwipeSuggestPolicy);
        static const SwipeSuggestPolicy sInstance;
    };
}
