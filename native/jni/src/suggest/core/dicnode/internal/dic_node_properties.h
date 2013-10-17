/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_DIC_NODE_PROPERTIES_H
#define LATINIME_DIC_NODE_PROPERTIES_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

/**
 * PtNode information related to the DicNode from the lexicon trie.
 */
class DicNodeProperties {
 public:
    AK_FORCE_INLINE DicNodeProperties()
            : mPtNodePos(0), mChildrenPtNodeArrayPos(0), mProbability(0), mDicNodeCodePoint(0),
              mIsTerminal(false), mHasChildrenPtNodes(false), mIsBlacklistedOrNotAWord(false),
              mDepth(0), mLeavingDepth(0) {}

    virtual ~DicNodeProperties() {}

    // Should be called only once per DicNode is initialized.
    void init(const int pos, const int childrenPos, const int nodeCodePoint, const int probability,
            const bool isTerminal, const bool hasChildren, const bool isBlacklistedOrNotAWord,
            const uint16_t depth, const uint16_t leavingDepth) {
        mPtNodePos = pos;
        mChildrenPtNodeArrayPos = childrenPos;
        mDicNodeCodePoint = nodeCodePoint;
        mProbability = probability;
        mIsTerminal = isTerminal;
        mHasChildrenPtNodes = hasChildren;
        mIsBlacklistedOrNotAWord = isBlacklistedOrNotAWord;
        mDepth = depth;
        mLeavingDepth = leavingDepth;
    }

    // Init for copy
    void init(const DicNodeProperties *const dicNodeProp) {
        mPtNodePos = dicNodeProp->mPtNodePos;
        mChildrenPtNodeArrayPos = dicNodeProp->mChildrenPtNodeArrayPos;
        mDicNodeCodePoint = dicNodeProp->mDicNodeCodePoint;
        mProbability = dicNodeProp->mProbability;
        mIsTerminal = dicNodeProp->mIsTerminal;
        mHasChildrenPtNodes = dicNodeProp->mHasChildrenPtNodes;
        mIsBlacklistedOrNotAWord = dicNodeProp->mIsBlacklistedOrNotAWord;
        mDepth = dicNodeProp->mDepth;
        mLeavingDepth = dicNodeProp->mLeavingDepth;
    }

    // Init as passing child
    void init(const DicNodeProperties *const dicNodeProp, const int codePoint) {
        mPtNodePos = dicNodeProp->mPtNodePos;
        mChildrenPtNodeArrayPos = dicNodeProp->mChildrenPtNodeArrayPos;
        mDicNodeCodePoint = codePoint; // Overwrite the node char of a passing child
        mProbability = dicNodeProp->mProbability;
        mIsTerminal = dicNodeProp->mIsTerminal;
        mHasChildrenPtNodes = dicNodeProp->mHasChildrenPtNodes;
        mIsBlacklistedOrNotAWord = dicNodeProp->mIsBlacklistedOrNotAWord;
        mDepth = dicNodeProp->mDepth + 1; // Increment the depth of a passing child
        mLeavingDepth = dicNodeProp->mLeavingDepth;
    }

    int getPtNodePos() const {
        return mPtNodePos;
    }

    int getChildrenPtNodeArrayPos() const {
        return mChildrenPtNodeArrayPos;
    }

    int getProbability() const {
        return mProbability;
    }

    int getDicNodeCodePoint() const {
        return mDicNodeCodePoint;
    }

    uint16_t getDepth() const {
        return mDepth;
    }

    // TODO: Move to output?
    uint16_t getLeavingDepth() const {
        return mLeavingDepth;
    }

    bool isTerminal() const {
        return mIsTerminal;
    }

    bool hasChildren() const {
        return mHasChildrenPtNodes || mDepth != mLeavingDepth;
    }

    bool isBlacklistedOrNotAWord() const {
        return mIsBlacklistedOrNotAWord;
    }

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class
    int mPtNodePos;
    int mChildrenPtNodeArrayPos;
    int mProbability;
    int mDicNodeCodePoint;
    bool mIsTerminal;
    bool mHasChildrenPtNodes;
    bool mIsBlacklistedOrNotAWord;
    uint16_t mDepth;
    uint16_t mLeavingDepth;
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_PROPERTIES_H
