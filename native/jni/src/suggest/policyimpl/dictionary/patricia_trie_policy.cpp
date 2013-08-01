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
#include "suggest/core/dictionary/binary_dictionary_terminal_attributes_reading_utils.h"
#include "suggest/policyimpl/dictionary/binary_format.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

namespace latinime {

const PatriciaTriePolicy PatriciaTriePolicy::sInstance;

void PatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPos();
    const int childCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
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
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_PROBABILITY;
    }
    const uint8_t *const dictRoot = binaryDictionaryInfo->getDictRoot();
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictRoot, &pos);
    if (!PatriciaTrieReadingUtils::isTerminal(flags)) {
        return NOT_A_PROBABILITY;
    }
    if (PatriciaTrieReadingUtils::isNotAWord(flags)
            || PatriciaTrieReadingUtils::isBlacklisted(flags)) {
        // If this is not a word, or if it's a blacklisted entry, it should behave as
        // having no probability outside of the suggestion process (where it should be used
        // for shortcuts).
        return NOT_A_PROBABILITY;
    }
    PatriciaTrieReadingUtils::skipCharacters(dictRoot, flags, MAX_WORD_LENGTH, &pos);
    return PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(dictRoot, &pos);
}

int PatriciaTriePolicy::getShortcutPositionOfNode(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    const uint8_t *const dictRoot = binaryDictionaryInfo->getDictRoot();
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictRoot, &pos);
    if (!PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        return NOT_A_DICT_POS;
    }
    PatriciaTrieReadingUtils::skipCharacters(dictRoot, flags, MAX_WORD_LENGTH, &pos);
    if (PatriciaTrieReadingUtils::isTerminal(flags)) {
        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(dictRoot, &pos);
    }
    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(dictRoot, flags, &pos);
    }
    return pos;
}

int PatriciaTriePolicy::getBigramsPositionOfNode(
        const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    const uint8_t *const dictRoot = binaryDictionaryInfo->getDictRoot();
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictRoot, &pos);
    if (!PatriciaTrieReadingUtils::hasBigrams(flags)) {
        return NOT_A_DICT_POS;
    }
    PatriciaTrieReadingUtils::skipCharacters(dictRoot, flags, MAX_WORD_LENGTH, &pos);
    if (PatriciaTrieReadingUtils::isTerminal(flags)) {
        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(dictRoot, &pos);
    }
    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(dictRoot, flags, &pos);
    }
    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        BinaryDictionaryTerminalAttributesReadingUtils::skipShortcuts(binaryDictionaryInfo, &pos);
    }
    return pos;
}

int PatriciaTriePolicy::createAndGetLeavingChildNode(const DicNode *const dicNode,
        const int nodePos, const BinaryDictionaryInfo *const binaryDictionaryInfo,
        const NodeFilter *const childrenFilter, DicNodeVector *childDicNodes) const {
    const uint8_t *const dictRoot = binaryDictionaryInfo->getDictRoot();
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictRoot, &pos);
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    const int mergedNodeCodePointCount = PatriciaTrieReadingUtils::getCharsAndAdvancePosition(
            dictRoot, flags, MAX_WORD_LENGTH, mergedNodeCodePoints, &pos);
    const int probability = (PatriciaTrieReadingUtils::isTerminal(flags))?
            PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(dictRoot, &pos)
                    : NOT_A_PROBABILITY;
    const int childrenPos = PatriciaTrieReadingUtils::hasChildrenInFlags(flags) ?
            PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                    dictRoot, flags, &pos) : NOT_A_DICT_POS;
    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        BinaryDictionaryTerminalAttributesReadingUtils::skipShortcuts(binaryDictionaryInfo, &pos);
    }
    if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
        BinaryDictionaryTerminalAttributesReadingUtils::skipExistingBigrams(
                binaryDictionaryInfo, &pos);
    }
    if (!childrenFilter->isFilteredOut(mergedNodeCodePoints[0])) {
        childDicNodes->pushLeavingChild(dicNode, nodePos, childrenPos, probability,
                PatriciaTrieReadingUtils::isTerminal(flags),
                PatriciaTrieReadingUtils::hasChildrenInFlags(flags),
                PatriciaTrieReadingUtils::isBlacklisted(flags) ||
                        PatriciaTrieReadingUtils::isNotAWord(flags),
                mergedNodeCodePointCount, mergedNodeCodePoints);
    }
    return pos;
}

} // namespace latinime
