/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"

#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

// To avoid infinite loop caused by invalid or malicious forward links.
const int DynamicPatriciaTrieReadingHelper::MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP = 100000;
const int DynamicPatriciaTrieReadingHelper::MAX_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP = 100000;

// Read node array size and process empty node arrays. Nodes and arrays are counted up in this
// method to avoid an infinite loop.
void DynamicPatriciaTrieReadingHelper::nextNodeArray() {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(mPos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        mPos -= mBuffer->getOriginalBufferSize();
    }
    mNodeCount = PatriciaTrieReadingUtils::getPtNodeArraySizeAndAdvancePosition(dictBuf,
            &mPos);
    if (usesAdditionalBuffer) {
        mPos += mBuffer->getOriginalBufferSize();
    }
    // Count up nodes and node arrays to avoid infinite loop.
    mTotalNodeCount += mNodeCount;
    mNodeArrayCount++;
    if (mNodeCount < 0 || mTotalNodeCount > MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP
            || mNodeArrayCount > MAX_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP) {
        // Invalid dictionary.
        AKLOGI("Invalid dictionary. nodeCount: %d, totalNodeCount: %d, MAX_CHILD_COUNT: %d"
                "nodeArrayCount: %d, MAX_NODE_ARRAY_COUNT: %d",
                mNodeCount, mTotalNodeCount, MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP,
                mNodeArrayCount, MAX_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP);
        ASSERT(false);
        mIsError = true;
        mPos = NOT_A_DICT_POS;
        return;
    }
    if (mNodeCount == 0) {
        // Empty node array. Try following forward link.
        followForwardLink();
    }
}

// Follow the forward link and read the next node array if exists.
void DynamicPatriciaTrieReadingHelper::followForwardLink() {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(mPos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        mPos -= mBuffer->getOriginalBufferSize();
    }
    const int forwardLinkPosition =
            DynamicPatriciaTrieReadingUtils::getForwardLinkPosition(dictBuf, mPos);
    if (usesAdditionalBuffer) {
        mPos += mBuffer->getOriginalBufferSize();
    }
    mPosOfLastForwardLinkField = mPos;
    if (DynamicPatriciaTrieReadingUtils::isValidForwardLinkPosition(forwardLinkPosition)) {
        // Follow the forward link.
        mPos += forwardLinkPosition;
        nextNodeArray();
    } else {
        // All node arrays have been read.
        mPos = NOT_A_DICT_POS;
    }
}

} // namespace latinime
