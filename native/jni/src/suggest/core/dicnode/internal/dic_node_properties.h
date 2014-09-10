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

#include <cstdint>

#include "defines.h"

namespace latinime {

/**
 * PtNode information related to the DicNode from the lexicon trie.
 */
class DicNodeProperties {
 public:
    AK_FORCE_INLINE DicNodeProperties()
            : mChildrenPtNodeArrayPos(NOT_A_DICT_POS), mUnigramProbability(NOT_A_PROBABILITY),
              mDicNodeCodePoint(NOT_A_CODE_POINT), mWordId(NOT_A_WORD_ID), mDepth(0),
              mLeavingDepth(0) {}

    ~DicNodeProperties() {}

    // Should be called only once per DicNode is initialized.
    void init(const int childrenPos, const int nodeCodePoint, const int unigramProbability,
            const int wordId, const uint16_t depth, const uint16_t leavingDepth,
            const int *const prevWordIds) {
        mChildrenPtNodeArrayPos = childrenPos;
        mDicNodeCodePoint = nodeCodePoint;
        mUnigramProbability = unigramProbability;
        mWordId = wordId;
        mDepth = depth;
        mLeavingDepth = leavingDepth;
        memmove(mPrevWordIds, prevWordIds, sizeof(mPrevWordIds));
    }

    // Init for root with prevWordsPtNodePos which is used for n-gram
    void init(const int rootPtNodeArrayPos, const int *const prevWordIds) {
        mChildrenPtNodeArrayPos = rootPtNodeArrayPos;
        mDicNodeCodePoint = NOT_A_CODE_POINT;
        mUnigramProbability = NOT_A_PROBABILITY;
        mWordId = NOT_A_WORD_ID;
        mDepth = 0;
        mLeavingDepth = 0;
        memmove(mPrevWordIds, prevWordIds, sizeof(mPrevWordIds));
    }

    void initByCopy(const DicNodeProperties *const dicNodeProp) {
        mChildrenPtNodeArrayPos = dicNodeProp->mChildrenPtNodeArrayPos;
        mDicNodeCodePoint = dicNodeProp->mDicNodeCodePoint;
        mUnigramProbability = dicNodeProp->mUnigramProbability;
        mWordId = dicNodeProp->mWordId;
        mDepth = dicNodeProp->mDepth;
        mLeavingDepth = dicNodeProp->mLeavingDepth;
        memmove(mPrevWordIds, dicNodeProp->mPrevWordIds, sizeof(mPrevWordIds));
    }

    // Init as passing child
    void init(const DicNodeProperties *const dicNodeProp, const int codePoint) {
        mChildrenPtNodeArrayPos = dicNodeProp->mChildrenPtNodeArrayPos;
        mDicNodeCodePoint = codePoint; // Overwrite the node char of a passing child
        mUnigramProbability = dicNodeProp->mUnigramProbability;
        mWordId = dicNodeProp->mWordId;
        mDepth = dicNodeProp->mDepth + 1; // Increment the depth of a passing child
        mLeavingDepth = dicNodeProp->mLeavingDepth;
        memmove(mPrevWordIds, dicNodeProp->mPrevWordIds, sizeof(mPrevWordIds));
    }

    int getChildrenPtNodeArrayPos() const {
        return mChildrenPtNodeArrayPos;
    }

    int getUnigramProbability() const {
        return mUnigramProbability;
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
        return mWordId != NOT_A_WORD_ID;
    }

    bool hasChildren() const {
        return (mChildrenPtNodeArrayPos != NOT_A_DICT_POS) || mDepth != mLeavingDepth;
    }

    const int *getPrevWordIds() const {
        return mPrevWordIds;
    }

    int getWordId() const {
        return mWordId;
    }

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class
    int mChildrenPtNodeArrayPos;
    // TODO: Remove
    int mUnigramProbability;
    int mDicNodeCodePoint;
    int mWordId;
    uint16_t mDepth;
    uint16_t mLeavingDepth;
    int mPrevWordIds[MAX_PREV_WORD_COUNT_FOR_N_GRAM];
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_PROPERTIES_H
