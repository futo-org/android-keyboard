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

#include "binary_format.h"
#include "defines.h"

namespace latinime {

/**
 * Node for traversing the lexicon trie.
 */
class DicNodeProperties {
 public:
    AK_FORCE_INLINE DicNodeProperties()
            : mPos(0), mFlags(0), mChildrenPos(0), mAttributesPos(0), mSiblingPos(0),
              mChildrenCount(0), mProbability(0), mBigramProbability(0), mNodeCodePoint(0),
              mDepth(0), mLeavingDepth(0), mIsTerminal(false), mHasMultipleChars(false),
              mHasChildren(false) {
    }

    virtual ~DicNodeProperties() {}

    // Should be called only once per DicNode is initialized.
    void init(const int pos, const uint8_t flags, const int childrenPos, const int attributesPos,
            const int siblingPos, const int nodeCodePoint, const int childrenCount,
            const int probability, const int bigramProbability, const bool isTerminal,
            const bool hasMultipleChars, const bool hasChildren, const uint16_t depth,
            const uint16_t terminalDepth) {
        mPos = pos;
        mFlags = flags;
        mChildrenPos = childrenPos;
        mAttributesPos = attributesPos;
        mSiblingPos = siblingPos;
        mNodeCodePoint = nodeCodePoint;
        mChildrenCount = childrenCount;
        mProbability = probability;
        mBigramProbability = bigramProbability;
        mIsTerminal = isTerminal;
        mHasMultipleChars = hasMultipleChars;
        mHasChildren = hasChildren;
        mDepth = depth;
        mLeavingDepth = terminalDepth;
    }

    // Init for copy
    void init(const DicNodeProperties *const nodeProp) {
        mPos = nodeProp->mPos;
        mFlags = nodeProp->mFlags;
        mChildrenPos = nodeProp->mChildrenPos;
        mAttributesPos = nodeProp->mAttributesPos;
        mSiblingPos = nodeProp->mSiblingPos;
        mNodeCodePoint = nodeProp->mNodeCodePoint;
        mChildrenCount = nodeProp->mChildrenCount;
        mProbability = nodeProp->mProbability;
        mBigramProbability = nodeProp->mBigramProbability;
        mIsTerminal = nodeProp->mIsTerminal;
        mHasMultipleChars = nodeProp->mHasMultipleChars;
        mHasChildren = nodeProp->mHasChildren;
        mDepth = nodeProp->mDepth;
        mLeavingDepth = nodeProp->mLeavingDepth;
    }

    // Init as passing child
    void init(const DicNodeProperties *const nodeProp, const int codePoint) {
        mPos = nodeProp->mPos;
        mFlags = nodeProp->mFlags;
        mChildrenPos = nodeProp->mChildrenPos;
        mAttributesPos = nodeProp->mAttributesPos;
        mSiblingPos = nodeProp->mSiblingPos;
        mNodeCodePoint = codePoint; // Overwrite the node char of a passing child
        mChildrenCount = nodeProp->mChildrenCount;
        mProbability = nodeProp->mProbability;
        mBigramProbability = nodeProp->mBigramProbability;
        mIsTerminal = nodeProp->mIsTerminal;
        mHasMultipleChars = nodeProp->mHasMultipleChars;
        mHasChildren = nodeProp->mHasChildren;
        mDepth = nodeProp->mDepth + 1; // Increment the depth of a passing child
        mLeavingDepth = nodeProp->mLeavingDepth;
    }

    int getPos() const {
        return mPos;
    }

    uint8_t getFlags() const {
        return mFlags;
    }

    int getChildrenPos() const {
        return mChildrenPos;
    }

    int getAttributesPos() const {
        return mAttributesPos;
    }

    int getChildrenCount() const {
        return mChildrenCount;
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

    bool hasMultipleChars() const {
        return mHasMultipleChars;
    }

    bool hasChildren() const {
        return mChildrenCount > 0 || mDepth != mLeavingDepth;
    }

    bool hasBlacklistedOrNotAWordFlag() const {
        return BinaryFormat::hasBlacklistedOrNotAWordFlag(mFlags);
    }

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class

    // Not used
    int getSiblingPos() const {
        return mSiblingPos;
    }

    int mPos;
    uint8_t mFlags;
    int mChildrenPos;
    int mAttributesPos;
    int mSiblingPos;
    int mChildrenCount;
    int mProbability;
    int mBigramProbability; // not used for now
    int mNodeCodePoint;
    uint16_t mDepth;
    uint16_t mLeavingDepth;
    bool mIsTerminal;
    bool mHasMultipleChars;
    bool mHasChildren;
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_PROPERTIES_H
