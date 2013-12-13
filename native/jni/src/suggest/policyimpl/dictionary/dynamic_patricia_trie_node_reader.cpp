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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"

#include "suggest/core/policy/dictionary_bigrams_structure_policy.h"
#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

void DynamicPatriciaTrieNodeReader::fetchPtNodeInfoFromBufferAndProcessMovedPtNode(
        const int ptNodePos, const int maxCodePointCount, int *const outCodePoints) {
    if (ptNodePos < 0 || ptNodePos >= mBuffer->getTailPosition()) {
        // Reading invalid position because of bug or broken dictionary.
        AKLOGE("Fetching PtNode info from invalid dictionary position: %d, dictionary size: %d",
                ptNodePos, mBuffer->getTailPosition());
        ASSERT(false);
        invalidatePtNodeInfo();
        return;
    }
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(ptNodePos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    int pos = ptNodePos;
    mHeadPos = ptNodePos;
    if (usesAdditionalBuffer) {
        pos -= mBuffer->getOriginalBufferSize();
    }
    mFlags = PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictBuf, &pos);
    const int parentPosOffset =
            DynamicPatriciaTrieReadingUtils::getParentPtNodePosOffsetAndAdvancePosition(dictBuf,
                    &pos);
    mParentPos = DynamicPatriciaTrieReadingUtils::getParentPtNodePos(parentPosOffset, mHeadPos);
    if (outCodePoints != 0) {
        mCodePointCount = PatriciaTrieReadingUtils::getCharsAndAdvancePosition(
                dictBuf, mFlags, maxCodePointCount, outCodePoints, &pos);
    } else {
        mCodePointCount = PatriciaTrieReadingUtils::skipCharacters(
                dictBuf, mFlags, MAX_WORD_LENGTH, &pos);
    }
    if (isTerminal()) {
        mProbabilityFieldPos = pos;
        if (usesAdditionalBuffer) {
            mProbabilityFieldPos += mBuffer->getOriginalBufferSize();
        }
        mProbability = PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(dictBuf, &pos);
    } else {
        mProbabilityFieldPos = NOT_A_DICT_POS;
        mProbability = NOT_A_PROBABILITY;
    }
    mChildrenPosFieldPos = pos;
    if (usesAdditionalBuffer) {
        mChildrenPosFieldPos += mBuffer->getOriginalBufferSize();
    }
    mChildrenPos = DynamicPatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
            dictBuf, &pos);
    if (usesAdditionalBuffer && mChildrenPos != NOT_A_DICT_POS) {
        mChildrenPos += mBuffer->getOriginalBufferSize();
    }
    if (mSiblingPos == NOT_A_DICT_POS) {
        if (DynamicPatriciaTrieReadingUtils::isMoved(mFlags)) {
            mBigramLinkedNodePos = mChildrenPos;
        } else {
            mBigramLinkedNodePos = NOT_A_DICT_POS;
        }
    }
    if (usesAdditionalBuffer) {
        pos += mBuffer->getOriginalBufferSize();
    }
    if (PatriciaTrieReadingUtils::hasShortcutTargets(mFlags)) {
        mShortcutPos = pos;
        mShortcutsPolicy->skipAllShortcuts(&pos);
    } else {
        mShortcutPos = NOT_A_DICT_POS;
    }
    if (PatriciaTrieReadingUtils::hasBigrams(mFlags)) {
        mBigramPos = pos;
        mBigramsPolicy->skipAllBigrams(&pos);
    } else {
        mBigramPos = NOT_A_DICT_POS;
    }
    // Update siblingPos if needed.
    if (mSiblingPos == NOT_A_DICT_POS) {
        // Sibling position is the tail position of current node.
        mSiblingPos = pos;
    }
    // Read destination node if the read node is a moved node.
    if (DynamicPatriciaTrieReadingUtils::isMoved(mFlags)) {
        // The destination position is stored at the same place as the parent position.
        fetchPtNodeInfoFromBufferAndProcessMovedPtNode(mParentPos, maxCodePointCount,
                outCodePoints);
    }
}

void DynamicPatriciaTrieNodeReader::invalidatePtNodeInfo() {
    mHeadPos = NOT_A_DICT_POS;
    mFlags = 0;
    mParentPos = NOT_A_DICT_POS;
    mCodePointCount = 0;
    mProbabilityFieldPos = NOT_A_DICT_POS;
    mProbability = NOT_A_PROBABILITY;
    mChildrenPosFieldPos = NOT_A_DICT_POS;
    mChildrenPos = NOT_A_DICT_POS;
    mBigramLinkedNodePos = NOT_A_DICT_POS;
    mShortcutPos = NOT_A_DICT_POS;
    mBigramPos = NOT_A_DICT_POS;
    mSiblingPos = NOT_A_DICT_POS;
}

}
