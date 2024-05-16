#pragma once

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/policy/traversal.h"

namespace latinime {
class SwipeTraversal : public Traversal {
public:
    static const SwipeTraversal *getInstance() { return &sInstance; }

    AK_FORCE_INLINE int getMaxPointerCount() const override {
        return MAX_POINTER_COUNT_G;
    }

    AK_FORCE_INLINE bool allowsErrorCorrections(const DicNode *const dicNode) const override {
        return false;
    }

    AK_FORCE_INLINE bool isOmission(const DicTraverseSession *const traverseSession,
                    const DicNode *const dicNode, const DicNode *const childDicNode,
                    const bool allowsErrorCorrections) const override {
        return false;
    }

    AK_FORCE_INLINE bool isTransition(const DicTraverseSession *const traverseSession,
                    const DicNode *const dicNode) const override {
        return false;//!dicNode->isFirstLetter();
    }

    AK_FORCE_INLINE bool isSpaceSubstitutionTerminal(const DicTraverseSession *const traverseSession,
                                     const DicNode *const dicNode) const override {
        return false;
    }

    AK_FORCE_INLINE bool isSpaceOmissionTerminal(const DicTraverseSession *const traverseSession,
                                 const DicNode *const dicNode) const override {
        return false;
    }

    AK_FORCE_INLINE bool shouldDepthLevelCache(const DicTraverseSession *const traverseSession) const override {
        return false;
    }

    AK_FORCE_INLINE bool shouldNodeLevelCache(const DicTraverseSession *const traverseSession,
                              const DicNode *const dicNode) const override {
        return false;
    }

    AK_FORCE_INLINE bool canDoLookAheadCorrection(const DicTraverseSession *const traverseSession,
                                  const DicNode *const dicNode) const override {
        return false;
    }

    AK_FORCE_INLINE ProximityType getProximityType(const DicTraverseSession *const traverseSession,
                                   const DicNode *const dicNode, const DicNode *const childDicNode) const override {
        return ProximityType::PROXIMITY_CHAR;
    }

    AK_FORCE_INLINE bool needsToTraverseAllUserInput() const override {
        return true;
    }

    AK_FORCE_INLINE float getMaxSpatialDistance() const override {
        return 1.0f;
    }

    AK_FORCE_INLINE int getDefaultExpandDicNodeSize() const override {
        return 40;
    }

    AK_FORCE_INLINE int getMaxCacheSize(const int inputSize, const float weightForLocale) const override {
        return 400;
    }

    AK_FORCE_INLINE int getTerminalCacheSize() const override {
        return MAX_RESULTS;
    }

    AK_FORCE_INLINE bool isPossibleOmissionChildNode(const DicTraverseSession *const traverseSession,
                                     const DicNode *const parentDicNode, const DicNode *const dicNode) const override {
        return false;
    }

    AK_FORCE_INLINE bool isGoodToTraverseNextWord(const DicNode *const dicNode,
                                  const int probability) const override {
        return false;
    }

private:
    DISALLOW_COPY_AND_ASSIGN(SwipeTraversal);
    static const SwipeTraversal sInstance;

    SwipeTraversal() {}
    ~SwipeTraversal() {}
};
};