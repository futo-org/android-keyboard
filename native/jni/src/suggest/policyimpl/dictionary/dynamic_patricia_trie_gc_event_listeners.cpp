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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_gc_event_listeners.h"

#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

bool DynamicPatriciaTrieGcEventListeners
        ::TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
                ::onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                        const int *const nodeCodePoints) {
    // PtNode is useless when the PtNode is not a terminal and doesn't have any not useless
    // children.
    bool isUselessPtNode = !node->isTerminal();
    if (node->isTerminal() && mIsDecayingDict) {
        const int newProbability =
                ForgettingCurveUtils::getEncodedProbabilityToSave(node->getProbability(),
                        mHeaderPolicy);
        int writingPos = node->getProbabilityFieldPos();
        // Update probability.
        if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(
                mBuffer, newProbability, &writingPos)) {
            return false;
        }
        if (!ForgettingCurveUtils::isValidEncodedProbability(newProbability)) {
            isUselessPtNode = true;
        }
    }
    if (mChildrenValue > 0) {
        isUselessPtNode = false;
    } else if (node->isTerminal()) {
        // Remove children as all children are useless.
        int writingPos = node->getChildrenPosFieldPos();
        if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(
                mBuffer, NOT_A_DICT_POS /* childrenPosition */, &writingPos)) {
            return false;
        }
    }
    if (isUselessPtNode) {
        // Current PtNode is no longer needed. Mark it as deleted.
        if (!mWritingHelper->markNodeAsDeleted(node)) {
            return false;
        }
    } else {
        mValueStack.back() += 1;
        if (node->isTerminal()) {
            mValidUnigramCount += 1;
        }
    }
    return true;
}

bool DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateBigramProbability
        ::onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints) {
    if (!node->isDeleted()) {
        int pos = node->getBigramsPos();
        if (pos != NOT_A_DICT_POS) {
            int bigramEntryCount = 0;
            if (!mBigramPolicy->updateAllBigramEntriesAndDeleteUselessEntries(&pos,
                    &bigramEntryCount)) {
                return false;
            }
            mValidBigramEntryCount += bigramEntryCount;
        }
    }
    return true;
}

// Writes dummy PtNode array size when the head of PtNode array is read.
bool DynamicPatriciaTrieGcEventListeners::TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
        ::onDescend(const int ptNodeArrayPos) {
    mValidPtNodeCount = 0;
    int writingPos = mBufferToWrite->getTailPosition();
    mDictPositionRelocationMap->mPtNodeArrayPositionRelocationMap.insert(
            DynamicPatriciaTrieWritingHelper::PtNodeArrayPositionRelocationMap::value_type(
                    ptNodeArrayPos, writingPos));
    // Writes dummy PtNode array size because arrays can have a forward link or needles PtNodes.
    // This field will be updated later in onReadingPtNodeArrayTail() with actual PtNode count.
    mPtNodeArraySizeFieldPos = writingPos;
    return DynamicPatriciaTrieWritingUtils::writePtNodeArraySizeAndAdvancePosition(
            mBufferToWrite, 0 /* arraySize */, &writingPos);
}

// Write PtNode array terminal and actual PtNode array size.
bool DynamicPatriciaTrieGcEventListeners::TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
        ::onReadingPtNodeArrayTail() {
    int writingPos = mBufferToWrite->getTailPosition();
    // Write PtNode array terminal.
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(
            mBufferToWrite, NOT_A_DICT_POS /* forwardLinkPos */, &writingPos)) {
        return false;
    }
    // Write actual PtNode array size.
    if (!DynamicPatriciaTrieWritingUtils::writePtNodeArraySizeAndAdvancePosition(
            mBufferToWrite, mValidPtNodeCount, &mPtNodeArraySizeFieldPos)) {
        return false;
    }
    return true;
}

// Write valid PtNode to buffer and memorize mapping from the old position to the new position.
bool DynamicPatriciaTrieGcEventListeners::TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
        ::onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints) {
    if (node->isDeleted()) {
        // Current PtNode is not written in new buffer because it has been deleted.
        mDictPositionRelocationMap->mPtNodePositionRelocationMap.insert(
                DynamicPatriciaTrieWritingHelper::PtNodePositionRelocationMap::value_type(
                        node->getHeadPos(), NOT_A_DICT_POS));
        return true;
    }
    int writingPos = mBufferToWrite->getTailPosition();
    mDictPositionRelocationMap->mPtNodePositionRelocationMap.insert(
            DynamicPatriciaTrieWritingHelper::PtNodePositionRelocationMap::value_type(
                    node->getHeadPos(), writingPos));
    mValidPtNodeCount++;
    // Writes current PtNode.
    return mWritingHelper->writePtNodeToBufferByCopyingPtNodeInfo(mBufferToWrite, node,
            node->getParentPos(), nodeCodePoints, node->getCodePointCount(),
            node->getProbability(), &writingPos);
}

bool DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateAllPositionFields
        ::onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints) {
    // Updates parent position.
    int parentPos = node->getParentPos();
    if (parentPos != NOT_A_DICT_POS) {
        DynamicPatriciaTrieWritingHelper::PtNodePositionRelocationMap::const_iterator it =
                mDictPositionRelocationMap->mPtNodePositionRelocationMap.find(parentPos);
        if (it != mDictPositionRelocationMap->mPtNodePositionRelocationMap.end()) {
            parentPos = it->second;
        }
    }
    int writingPos = node->getHeadPos() + DynamicPatriciaTrieWritingUtils::NODE_FLAG_FIELD_SIZE;
    // Write updated parent offset.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(mBufferToWrite,
            parentPos, node->getHeadPos(), &writingPos)) {
        return false;
    }

    // Updates children position.
    int childrenPos = node->getChildrenPos();
    if (childrenPos != NOT_A_DICT_POS) {
        DynamicPatriciaTrieWritingHelper::PtNodeArrayPositionRelocationMap::const_iterator it =
                mDictPositionRelocationMap->mPtNodeArrayPositionRelocationMap.find(childrenPos);
        if (it != mDictPositionRelocationMap->mPtNodeArrayPositionRelocationMap.end()) {
            childrenPos = it->second;
        }
    }
    writingPos = node->getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBufferToWrite,
            childrenPos, &writingPos)) {
        return false;
    }

    // Updates bigram target PtNode positions in the bigram list.
    int bigramsPos = node->getBigramsPos();
    if (bigramsPos != NOT_A_DICT_POS) {
        int bigramEntryCount;
        if (!mBigramPolicy->updateAllBigramTargetPtNodePositions(&bigramsPos,
                &mDictPositionRelocationMap->mPtNodePositionRelocationMap, &bigramEntryCount)) {
            return false;
        }
        mBigramCount += bigramEntryCount;
    }
    if (node->isTerminal()) {
        mUnigramCount++;
    }

    return true;
}

} // namespace latinime
