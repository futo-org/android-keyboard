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

#include "suggest/policyimpl/dictionary/patricia_trie_policy.h"

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

namespace latinime {

const DynamicPatriciaTriePolicy DynamicPatriciaTriePolicy::sInstance;

void DynamicPatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    DynamicPatriciaTrieNodeReader nodeReader(binaryDictionaryInfo);
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    int nextPos = dicNode->getChildrenPos();
    do {
        const int childCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
                binaryDictionaryInfo->getDictRoot(), &nextPos);
        for (int i = 0; i < childCount; i++) {
            nodeReader.fetchNodeInfoFromBufferAndGetNodeCodePoints(nextPos, MAX_WORD_LENGTH,
                    mergedNodeCodePoints);
            if (!nodeReader.isDeleted() && !nodeFilter->isFilteredOut(mergedNodeCodePoints[0])) {
                // Push child note when the node is not deleted and not filtered out.
                childDicNodes->pushLeavingChild(dicNode, nodeReader.getNodePos(),
                        nodeReader.getChildrenPos(), nodeReader.getProbability(),
                        nodeReader.isTerminal(), nodeReader.hasChildren(),
                        nodeReader.isBlacklisted() || nodeReader.isNotAWord(),
                        nodeReader.getCodePointCount(), mergedNodeCodePoints);
            }
            nextPos = nodeReader.getSiblingNodePos();
        }
        nextPos = DynamicPatriciaTrieReadingUtils::getForwardLinkPosition(
                binaryDictionaryInfo->getDictRoot(), nextPos);
    } while(DynamicPatriciaTrieReadingUtils::isValidForwardLinkPosition(nextPos));
}

int DynamicPatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    // TODO: Implement.
    return 0;
}

int DynamicPatriciaTriePolicy::getTerminalNodePositionOfWord(
        const BinaryDictionaryInfo *const binaryDictionaryInfo, const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    // TODO: Implement.
    return NOT_A_DICT_POS;
}

int DynamicPatriciaTriePolicy::getUnigramProbability(
        const BinaryDictionaryInfo *const binaryDictionaryInfo, const int nodePos) const {
    DynamicPatriciaTrieNodeReader nodeReader(binaryDictionaryInfo);
    nodeReader.fetchNodeInfoFromBuffer(nodePos);
    if (nodeReader.isDeleted() || nodeReader.isBlacklisted() || nodeReader.isNotAWord()) {
        return NOT_A_PROBABILITY;
    }
    return nodeReader.getProbability();
}

int DynamicPatriciaTriePolicy::getShortcutPositionOfNode(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos) const {
    DynamicPatriciaTrieNodeReader nodeReader(binaryDictionaryInfo);
    nodeReader.fetchNodeInfoFromBuffer(nodePos);
    if (nodeReader.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return nodeReader.getShortcutPos();
}

int DynamicPatriciaTriePolicy::getBigramsPositionOfNode(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos) const {
    DynamicPatriciaTrieNodeReader nodeReader(binaryDictionaryInfo);
    nodeReader.fetchNodeInfoFromBuffer(nodePos);
    if (nodeReader.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return nodeReader.getBigramsPos();
}

} // namespace latinime
