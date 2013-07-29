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
 * Node for traversing the lexicon trie.
 */
// TODO: Introduce a dictionary node class which has attribute members required to understand the
// dictionary structure.
class DicNodeProperties {
 public:
    AK_FORCE_INLINE DicNodeProperties()
            : mPos(0), mChildrenPos(0), mProbability(0), mNodeCodePoint(0), mIsTerminal(false),
              mHasChildren(false), mIsBlacklistedOrNotAWord(false), mDepth(0), mLeavingDepth(0) {}

    virtual ~DicNodeProperties() {}

    // Should be called only once per DicNode is initialized.
    void init(const int pos, const int childrenPos, const int nodeCodePoint, const int probability,
            const bool isTerminal, const bool hasChildren, const bool isBlacklistedOrNotAWord,
            const uint16_t depth, const uint16_t leavingDepth) {
        mPos = pos;
        mChildrenPos = childrenPos;
        mNodeCodePoint = nodeCodePoint;
        mProbability = probability;
        mIsTerminal = isTerminal;
        mHasChildren = hasChildren;
        mIsBlacklistedOrNotAWord = isBlacklistedOrNotAWord;
        mDepth = depth;
        mLeavingDepth = leavingDepth;
    }

    // Init for copy
    void init(const DicNodeProperties *const nodeProp) {
        mPos = nodeProp->mPos;
        mChildrenPos = nodeProp->mChildrenPos;
        mNodeCodePoint = nodeProp->mNodeCodePoint;
        mProbability = nodeProp->mProbability;
        mIsTerminal = nodeProp->mIsTerminal;
        mHasChildren = nodeProp->mHasChildren;
        mIsBlacklistedOrNotAWord = nodeProp->mIsBlacklistedOrNotAWord;
        mDepth = nodeProp->mDepth;
        mLeavingDepth = nodeProp->mLeavingDepth;
    }

    // Init as passing child
    void init(const DicNodeProperties *const nodeProp, const int codePoint) {
        mPos = nodeProp->mPos;
        mChildrenPos = nodeProp->mChildrenPos;
        mNodeCodePoint = codePoint; // Overwrite the node char of a passing child
        mProbability = nodeProp->mProbability;
        mIsTerminal = nodeProp->mIsTerminal;
        mHasChildren = nodeProp->mHasChildren;
        mIsBlacklistedOrNotAWord = nodeProp->mIsBlacklistedOrNotAWord;
        mDepth = nodeProp->mDepth + 1; // Increment the depth of a passing child
        mLeavingDepth = nodeProp->mLeavingDepth;
    }

    int getPos() const {
        return mPos;
    }

    int getChildrenPos() const {
        return mChildrenPos;
    }

    int getProbability() const {
        return mProbability;
    }

    int getNodeCodePoint() const {
        return mNodeCodePoint;
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
        return mHasChildren || mDepth != mLeavingDepth;
    }

    bool isBlacklistedOrNotAWord() const {
        return mIsBlacklistedOrNotAWord;
    }

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class
    int mPos;
    int mChildrenPos;
    int mProbability;
    int mNodeCodePoint;
    bool mIsTerminal;
    bool mHasChildren;
    bool mIsBlacklistedOrNotAWord;
    uint16_t mDepth;
    uint16_t mLeavingDepth;
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_PROPERTIES_H
