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
const size_t DynamicPatriciaTrieReadingHelper::MAX_READING_STATE_STACK_SIZE = MAX_WORD_LENGTH;

// Visits all PtNodes in post-order depth first manner.
// For example, visits c -> b -> y -> x -> a for the following dictionary:
// a _ b _ c
//   \ x _ y
bool DynamicPatriciaTrieReadingHelper::traverseAllPtNodesInPostorderDepthFirstManner(
        TraversingEventListener *const listener) {
    bool alreadyVisitedChildren = false;
    // Descend from the root to the root PtNode array.
    if (!listener->onDescend(getPosOfLastPtNodeArrayHead())) {
        return false;
    }
    while (!isEnd()) {
        if (!alreadyVisitedChildren) {
            if (mNodeReader.hasChildren()) {
                // Move to the first child.
                if (!listener->onDescend(mNodeReader.getChildrenPos())) {
                    return false;
                }
                pushReadingStateToStack();
                readChildNode();
            } else {
                alreadyVisitedChildren = true;
            }
        } else {
            if (!listener->onVisitingPtNode(&mNodeReader, mMergedNodeCodePoints)) {
                return false;
            }
            readNextSiblingNode();
            if (isEnd()) {
                // All PtNodes in current linked PtNode arrays have been visited.
                // Return to the parent.
                if (!listener->onReadingPtNodeArrayTail()) {
                    return false;
                }
                if (mReadingStateStack.size() <= 0) {
                    break;
                }
                if (!listener->onAscend()) {
                    return false;
                }
                popReadingStateFromStack();
                alreadyVisitedChildren = true;
            } else {
                // Process sibling PtNode.
                alreadyVisitedChildren = false;
            }
        }
    }
    // Ascend from the root PtNode array to the root.
    if (!listener->onAscend()) {
        return false;
    }
    return !isError();
}

// Visits all PtNodes in PtNode array level pre-order depth first manner, which is the same order
// that PtNodes are written in the dictionary buffer.
// For example, visits a -> b -> x -> c -> y for the following dictionary:
// a _ b _ c
//   \ x _ y
bool DynamicPatriciaTrieReadingHelper::traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
        TraversingEventListener *const listener) {
    bool alreadyVisitedAllPtNodesInArray = false;
    bool alreadyVisitedChildren = false;
    // Descend from the root to the root PtNode array.
    if (!listener->onDescend(getPosOfLastPtNodeArrayHead())) {
        return false;
    }
    if (isEnd()) {
        // Empty dictionary. Needs to notify the listener of the tail of empty PtNode array.
        if (!listener->onReadingPtNodeArrayTail()) {
            return false;
        }
    }
    pushReadingStateToStack();
    while (!isEnd()) {
        if (alreadyVisitedAllPtNodesInArray) {
            if (alreadyVisitedChildren) {
                // Move to next sibling PtNode's children.
                readNextSiblingNode();
                if (isEnd()) {
                    // Return to the parent PTNode.
                    if (!listener->onAscend()) {
                        return false;
                    }
                    if (mReadingStateStack.size() <= 0) {
                        break;
                    }
                    popReadingStateFromStack();
                    alreadyVisitedChildren = true;
                    alreadyVisitedAllPtNodesInArray = true;
                } else {
                    alreadyVisitedChildren = false;
                }
            } else {
                if (mNodeReader.hasChildren()) {
                    // Move to the first child.
                    if (!listener->onDescend(mNodeReader.getChildrenPos())) {
                        return false;
                    }
                    pushReadingStateToStack();
                    readChildNode();
                    // Push state to return the head of PtNode array.
                    pushReadingStateToStack();
                    alreadyVisitedAllPtNodesInArray = false;
                    alreadyVisitedChildren = false;
                } else {
                    alreadyVisitedChildren = true;
                }
            }
        } else {
            if (!listener->onVisitingPtNode(&mNodeReader, mMergedNodeCodePoints)) {
                return false;
            }
            readNextSiblingNode();
            if (isEnd()) {
                if (!listener->onReadingPtNodeArrayTail()) {
                    return false;
                }
                // Return to the head of current PtNode array.
                popReadingStateFromStack();
                alreadyVisitedAllPtNodesInArray = true;
            }
        }
    }
    popReadingStateFromStack();
    // Ascend from the root PtNode array to the root.
    if (!listener->onAscend()) {
        return false;
    }
    return !isError();
}

// Read node array size and process empty node arrays. Nodes and arrays are counted up in this
// method to avoid an infinite loop.
void DynamicPatriciaTrieReadingHelper::nextPtNodeArray() {
    if (mReadingState.mPos < 0 || mReadingState.mPos >= mBuffer->getTailPosition()) {
        // Reading invalid position because of a bug or a broken dictionary.
        AKLOGE("Reading PtNode array info from invalid dictionary position: %d, dict size: %d",
                mReadingState.mPos, mBuffer->getTailPosition());
        ASSERT(false);
        mIsError = true;
        mReadingState.mPos = NOT_A_DICT_POS;
        return;
    }
    mReadingState.mPosOfLastPtNodeArrayHead = mReadingState.mPos;
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(mReadingState.mPos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        mReadingState.mPos -= mBuffer->getOriginalBufferSize();
    }
    mReadingState.mNodeCount = PatriciaTrieReadingUtils::getPtNodeArraySizeAndAdvancePosition(
            dictBuf, &mReadingState.mPos);
    if (usesAdditionalBuffer) {
        mReadingState.mPos += mBuffer->getOriginalBufferSize();
    }
    // Count up nodes and node arrays to avoid infinite loop.
    mReadingState.mTotalNodeCount += mReadingState.mNodeCount;
    mReadingState.mNodeArrayCount++;
    if (mReadingState.mNodeCount < 0
            || mReadingState.mTotalNodeCount > MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP
            || mReadingState.mNodeArrayCount > MAX_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP) {
        // Invalid dictionary.
        AKLOGI("Invalid dictionary. nodeCount: %d, totalNodeCount: %d, MAX_CHILD_COUNT: %d"
                "nodeArrayCount: %d, MAX_NODE_ARRAY_COUNT: %d",
                mReadingState.mNodeCount, mReadingState.mTotalNodeCount,
                MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP, mReadingState.mNodeArrayCount,
                MAX_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP);
        ASSERT(false);
        mIsError = true;
        mReadingState.mPos = NOT_A_DICT_POS;
        return;
    }
    if (mReadingState.mNodeCount == 0) {
        // Empty node array. Try following forward link.
        followForwardLink();
    }
}

// Follow the forward link and read the next node array if exists.
void DynamicPatriciaTrieReadingHelper::followForwardLink() {
    if (mReadingState.mPos < 0 || mReadingState.mPos >= mBuffer->getTailPosition()) {
        // Reading invalid position because of bug or broken dictionary.
        AKLOGE("Reading forward link from invalid dictionary position: %d, dict size: %d",
                mReadingState.mPos, mBuffer->getTailPosition());
        ASSERT(false);
        mIsError = true;
        mReadingState.mPos = NOT_A_DICT_POS;
        return;
    }
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(mReadingState.mPos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        mReadingState.mPos -= mBuffer->getOriginalBufferSize();
    }
    const int forwardLinkPosition =
            DynamicPatriciaTrieReadingUtils::getForwardLinkPosition(dictBuf, mReadingState.mPos);
    if (usesAdditionalBuffer) {
        mReadingState.mPos += mBuffer->getOriginalBufferSize();
    }
    mReadingState.mPosOfLastForwardLinkField = mReadingState.mPos;
    if (DynamicPatriciaTrieReadingUtils::isValidForwardLinkPosition(forwardLinkPosition)) {
        // Follow the forward link.
        mReadingState.mPos += forwardLinkPosition;
        nextPtNodeArray();
    } else {
        // All node arrays have been read.
        mReadingState.mPos = NOT_A_DICT_POS;
    }
}

} // namespace latinime
