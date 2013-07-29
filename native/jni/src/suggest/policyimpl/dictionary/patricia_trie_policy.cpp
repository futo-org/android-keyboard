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
#include "suggest/core/dictionary/binary_format.h"

namespace latinime {

const PatriciaTriePolicy PatriciaTriePolicy::sInstance;

void PatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPos();
    const int childCount = BinaryFormat::getGroupCountAndForwardPointer(
            binaryDictionaryInfo->getDictRoot(), &nextPos);
    for (int i = 0; i < childCount; i++) {
        nextPos = createAndGetLeavingChildNode(dicNode, nextPos, binaryDictionaryInfo,
                nodeFilter, childDicNodes);
    }
}

int PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    return BinaryFormat::getCodePointsAndProbabilityAndReturnCodePointCount(
            binaryDictionaryInfo->getDictRoot(), nodePos,
            maxCodePointCount, outCodePoints, outUnigramProbability);
}

int PatriciaTriePolicy::getTerminalNodePositionOfWord(
        const BinaryDictionaryInfo *const binaryDictionaryInfo, const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    return BinaryFormat::getTerminalPosition(binaryDictionaryInfo->getDictRoot(), inWord,
            length, forceLowerCaseSearch);
}

int PatriciaTriePolicy::getUnigramProbability(
        const BinaryDictionaryInfo *const binaryDictionaryInfo, const int nodePos) const {
    const uint8_t *const root = binaryDictionaryInfo->getDictRoot();
    int pos = nodePos;
    const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(root, &pos);
    if (flags & (BinaryFormat::FLAG_IS_BLACKLISTED | BinaryFormat::FLAG_IS_NOT_A_WORD)) {
        // If this is not a word, or if it's a blacklisted entry, it should behave as
        // having no probability outside of the suggestion process (where it should be used
        // for shortcuts).
        return NOT_A_PROBABILITY;
    }
    const bool hasMultipleChars = (0 != (BinaryFormat::FLAG_HAS_MULTIPLE_CHARS & flags));
    if (hasMultipleChars) {
        pos = BinaryFormat::skipOtherCharacters(root, pos);
    } else {
        BinaryFormat::getCodePointAndForwardPointer(root, &pos);
    }
    return BinaryFormat::readProbabilityWithoutMovingPointer(root, pos);
}

int PatriciaTriePolicy::getShortcutPositionOfNode(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos) const {
    return BinaryFormat::getShortcutListPositionForWordPosition(
            binaryDictionaryInfo->getDictRoot(), nodePos);
}

int PatriciaTriePolicy::getBigramsPositionOfNode(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos) const {
    return BinaryFormat::getBigramListPositionForWordPosition(
            binaryDictionaryInfo->getDictRoot(), nodePos);
}

int PatriciaTriePolicy::createAndGetLeavingChildNode(const DicNode *const dicNode, int pos,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const NodeFilter *const childrenFilter, DicNodeVector *childDicNodes) const {
    const int nextPos = pos;
    const uint8_t flags = BinaryFormat::getFlagsAndForwardPointer(
            binaryDictionaryInfo->getDictRoot(), &pos);
    const bool hasMultipleChars = (0 != (BinaryFormat::FLAG_HAS_MULTIPLE_CHARS & flags));
    const bool isTerminal = (0 != (BinaryFormat::FLAG_IS_TERMINAL & flags));
    const bool hasChildren = BinaryFormat::hasChildrenInFlags(flags);
    const bool isBlacklistedOrNotAWord = BinaryFormat::hasBlacklistedOrNotAWordFlag(flags);

    int codePoint = BinaryFormat::getCodePointAndForwardPointer(
            binaryDictionaryInfo->getDictRoot(), &pos);
    ASSERT(NOT_A_CODE_POINT != codePoint);
    // TODO: optimize this
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    uint16_t mergedNodeCodePointCount = 0;
    mergedNodeCodePoints[mergedNodeCodePointCount++] = codePoint;

    do {
        const int nextCodePoint = hasMultipleChars
                ? BinaryFormat::getCodePointAndForwardPointer(
                        binaryDictionaryInfo->getDictRoot(), &pos) : NOT_A_CODE_POINT;
        const bool isLastChar = (NOT_A_CODE_POINT == nextCodePoint);
        if (!isLastChar) {
            mergedNodeCodePoints[mergedNodeCodePointCount++] = nextCodePoint;
        }
        codePoint = nextCodePoint;
    } while (NOT_A_CODE_POINT != codePoint);

    const int probability = isTerminal ? BinaryFormat::readProbabilityWithoutMovingPointer(
            binaryDictionaryInfo->getDictRoot(), pos) : NOT_A_PROBABILITY;
    pos = BinaryFormat::skipProbability(flags, pos);
    int childrenPos = hasChildren ? BinaryFormat::readChildrenPosition(
            binaryDictionaryInfo->getDictRoot(), flags, pos) : NOT_A_DICT_POS;
    const int siblingPos = BinaryFormat::skipChildrenPosAndAttributes(
            binaryDictionaryInfo->getDictRoot(), flags, pos);

    if (childrenFilter->isFilteredOut(mergedNodeCodePoints[0])) {
        return siblingPos;
    }
    childDicNodes->pushLeavingChild(dicNode, nextPos, childrenPos, probability, isTerminal,
            hasChildren, isBlacklistedOrNotAWord, mergedNodeCodePointCount, mergedNodeCodePoints);
    return siblingPos;
}

} // namespace latinime
