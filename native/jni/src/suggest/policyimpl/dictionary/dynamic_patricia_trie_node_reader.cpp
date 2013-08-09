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

#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/binary_dictionary_terminal_attributes_reading_utils.h"
#include "suggest/policyimpl/dictionary/bigrams/bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"

namespace latinime {

void DynamicPatriciaTrieNodeReader::fetchNodeInfoFromBufferAndProcessMovedNode(const int nodePos,
        const int maxCodePointCount, int *const outCodePoints) {
    const uint8_t *const dictRoot = mBinaryDictionaryInfo->getDictRoot();
    int pos = nodePos;
    mFlags = PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictRoot, &pos);
    const int parentPos =
            DynamicPatriciaTrieReadingUtils::getParentPosAndAdvancePosition(dictRoot, &pos);
    mParentPos = (parentPos != 0) ? mNodePos + parentPos : NOT_A_DICT_POS;
    if (outCodePoints != 0) {
        mCodePointCount = PatriciaTrieReadingUtils::getCharsAndAdvancePosition(
                dictRoot, mFlags, maxCodePointCount, outCodePoints, &pos);
    } else {
        mCodePointCount = PatriciaTrieReadingUtils::skipCharacters(
                dictRoot, mFlags, MAX_WORD_LENGTH, &pos);
    }
    if (isTerminal()) {
        mProbability = PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(dictRoot, &pos);
    } else {
        mProbability = NOT_A_PROBABILITY;
    }
    if (hasChildren()) {
        mChildrenPos = DynamicPatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                dictRoot, mFlags, &pos);
    } else {
        mChildrenPos = NOT_A_DICT_POS;
    }
    if (PatriciaTrieReadingUtils::hasShortcutTargets(mFlags)) {
        mShortcutPos = pos;
        BinaryDictionaryTerminalAttributesReadingUtils::skipShortcuts(mBinaryDictionaryInfo, &pos);
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
    if (mSiblingPos == NOT_A_VALID_WORD_POS) {
        // Sibling position is the tail position of current node.
        mSiblingPos = pos;
    }
    // Read destination node if the read node is a moved node.
    if (DynamicPatriciaTrieReadingUtils::isMoved(mFlags)) {
        // The destination position is stored at the same place as the parent position.
        fetchNodeInfoFromBufferAndProcessMovedNode(mParentPos, maxCodePointCount, outCodePoints);
    }
}

}
