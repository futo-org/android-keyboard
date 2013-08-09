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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_policy.h"

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

namespace latinime {

// To avoid infinite loop caused by invalid or malicious forward links.
const int DynamicPatriciaTriePolicy::MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP = 100000;

void DynamicPatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    DynamicPatriciaTrieNodeReader nodeReader(mBinaryDictionaryInfo);
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    int nextPos = dicNode->getChildrenPos();
    int totalChildCount = 0;
    do {
        const int childCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
                mDictRoot, &nextPos);
        totalChildCount += childCount;
        if (childCount <= 0 || totalChildCount > MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP) {
            // Invalid dictionary.
            AKLOGI("Invalid dictionary. childCount: %d, totalChildCount: %d, MAX: %d",
                    childCount, totalChildCount, MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP);
            ASSERT(false);
            return;
        }
        for (int i = 0; i < childCount; i++) {
            nodeReader.fetchNodeInfoFromBufferAndGetNodeCodePoints(nextPos, MAX_WORD_LENGTH,
                    mergedNodeCodePoints);
            if (!nodeReader.isDeleted() && !nodeFilter->isFilteredOut(mergedNodeCodePoints[0])) {
                // Push child node when the node is not deleted and not filtered out.
                childDicNodes->pushLeavingChild(dicNode, nodeReader.getNodePos(),
                        nodeReader.getChildrenPos(), nodeReader.getProbability(),
                        nodeReader.isTerminal(), nodeReader.hasChildren(),
                        nodeReader.isBlacklisted() || nodeReader.isNotAWord(),
                        nodeReader.getCodePointCount(), mergedNodeCodePoints);
            }
            nextPos = nodeReader.getSiblingNodePos();
        }
        nextPos = DynamicPatriciaTrieReadingUtils::getForwardLinkPosition(mDictRoot, nextPos);
    } while (DynamicPatriciaTrieReadingUtils::isValidForwardLinkPosition(nextPos));
}

int DynamicPatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int nodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        *outUnigramProbability = NOT_A_PROBABILITY;
        return 0;
    }
    // This method traverses parent nodes from the terminal by following parent pointers; thus,
    // node code points are stored in the buffer in the reverse order.
    int reverseCodePoints[maxCodePointCount];
    int mergedNodeCodePoints[maxCodePointCount];
    int codePointCount = 0;

    DynamicPatriciaTrieNodeReader nodeReader(mBinaryDictionaryInfo);
    // First, read terminal node and get its probability.
    nodeReader.fetchNodeInfoFromBufferAndGetNodeCodePoints(nodePos, maxCodePointCount,
            mergedNodeCodePoints);
    // Store terminal node probability.
    *outUnigramProbability = nodeReader.getProbability();
    // Store terminal node code points to buffer in the reverse order.
    for (int i = nodeReader.getCodePointCount() - 1; i >= 0; --i) {
        reverseCodePoints[codePointCount++] = mergedNodeCodePoints[i];
    }
    // Then, follow parent pos toward the root node.
    while (nodeReader.getParentPos() != NOT_A_DICT_POS) {
        // codePointCount must be incremented at least once in each iteration to ensure preventing
        // infinite loop.
        if (nodeReader.isDeleted() || codePointCount > maxCodePointCount
                || nodeReader.getCodePointCount() <= 0) {
            // The nodePos is not a valid terminal node position in the dictionary.
            *outUnigramProbability = NOT_A_PROBABILITY;
            return 0;
        }
        // Read parent node.
        nodeReader.fetchNodeInfoFromBufferAndGetNodeCodePoints(nodeReader.getParentPos(),
                maxCodePointCount, mergedNodeCodePoints);
        // Store node code points to buffer in the reverse order.
        for (int i = nodeReader.getCodePointCount() - 1; i >= 0; --i) {
            reverseCodePoints[codePointCount++] = mergedNodeCodePoints[i];
        }
    }
    // Reverse the stored code points to output them.
    for (int i = 0; i < codePointCount; ++i) {
        outCodePoints[i] = reverseCodePoints[codePointCount - i - 1];
    }
    return codePointCount;
}

int DynamicPatriciaTriePolicy::getTerminalNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    int searchCodePoints[length];
    for (int i = 0; i < length; ++i) {
        searchCodePoints[i] = forceLowerCaseSearch ? CharUtils::toLowerCase(inWord[i]) : inWord[i];
    }
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    int currentLength = 0;
    int pos = getRootPosition();
    DynamicPatriciaTrieNodeReader nodeReader(mBinaryDictionaryInfo);
    while (currentLength <= length) {
        // When foundMatchedNode becomes true, currentLength is increased at least once.
        bool foundMatchedNode = false;
        int totalChildCount = 0;
        do {
            const int childCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
                    mDictRoot, &pos);
            totalChildCount += childCount;
            if (childCount <= 0 || totalChildCount > MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP) {
                // Invalid dictionary.
                AKLOGI("Invalid dictionary. childCount: %d, totalChildCount: %d, MAX: %d",
                        childCount, totalChildCount, MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP);
                ASSERT(false);
                return NOT_A_VALID_WORD_POS;
            }
            for (int i = 0; i < childCount; i++) {
                nodeReader.fetchNodeInfoFromBufferAndGetNodeCodePoints(pos, MAX_WORD_LENGTH,
                        mergedNodeCodePoints);
                if (nodeReader.isDeleted() || nodeReader.getCodePointCount() <= 0) {
                    // Skip deleted or empty node.
                    pos = nodeReader.getSiblingNodePos();
                    continue;
                }
                bool matched = true;
                for (int j = 0; j < nodeReader.getCodePointCount(); ++j) {
                    if (mergedNodeCodePoints[j] != searchCodePoints[currentLength + j]) {
                        // Different code point is found.
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    currentLength += nodeReader.getCodePointCount();
                    if (length == currentLength) {
                        // Terminal position is found.
                        return nodeReader.getNodePos();
                    }
                    if (!nodeReader.hasChildren()) {
                        return NOT_A_VALID_WORD_POS;
                    }
                    foundMatchedNode = true;
                    // Advance to the children nodes.
                    pos = nodeReader.getChildrenPos();
                    break;
                }
                // Try next sibling node.
                pos = nodeReader.getSiblingNodePos();
            }
            if (foundMatchedNode) {
                break;
            }
            // If the matched node is not found in the current node group, try to follow the
            // forward link.
            pos = DynamicPatriciaTrieReadingUtils::getForwardLinkPosition(
                    mDictRoot, pos);
        } while (DynamicPatriciaTrieReadingUtils::isValidForwardLinkPosition(pos));
        if (!foundMatchedNode) {
            // Matched node is not found.
            return NOT_A_VALID_WORD_POS;
        }
    }
    // If we already traversed the tree further than the word is long, there means
    // there was no match (or we would have found it).
    return NOT_A_VALID_WORD_POS;
}

int DynamicPatriciaTriePolicy::getUnigramProbability(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_PROBABILITY;
    }
    DynamicPatriciaTrieNodeReader nodeReader(mBinaryDictionaryInfo);
    nodeReader.fetchNodeInfoFromBuffer(nodePos);
    if (nodeReader.isDeleted() || nodeReader.isBlacklisted() || nodeReader.isNotAWord()) {
        return NOT_A_PROBABILITY;
    }
    return nodeReader.getProbability();
}

int DynamicPatriciaTriePolicy::getShortcutPositionOfNode(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    DynamicPatriciaTrieNodeReader nodeReader(mBinaryDictionaryInfo);
    nodeReader.fetchNodeInfoFromBuffer(nodePos);
    if (nodeReader.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return nodeReader.getShortcutPos();
}

int DynamicPatriciaTriePolicy::getBigramsPositionOfNode(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    DynamicPatriciaTrieNodeReader nodeReader(mBinaryDictionaryInfo);
    nodeReader.fetchNodeInfoFromBuffer(nodePos);
    if (nodeReader.isDeleted()) {
        return NOT_A_DICT_POS;
    }
    return nodeReader.getBigramsPos();
}

} // namespace latinime
