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

void PatriciaTriePolicy::createAndGetAllChildNodes(const DicNode *const dicNode,
        const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPos();
    const int childCount = PatriciaTrieReadingUtils::getGroupCountAndAdvancePosition(
            mDictRoot, &nextPos);
    for (int i = 0; i < childCount; i++) {
        nextPos = createAndGetLeavingChildNode(dicNode, nextPos, nodeFilter, childDicNodes);
    }
}

int PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int nodePos, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    return BinaryFormat::getCodePointsAndProbabilityAndReturnCodePointCount(mDictRoot, nodePos,
            maxCodePointCount, outCodePoints, outUnigramProbability);
}

int PatriciaTriePolicy::getTerminalNodePositionOfWord(const int *const inWord,
        const int length, const bool forceLowerCaseSearch) const {
    return BinaryFormat::getTerminalPosition(mDictRoot, inWord,
            length, forceLowerCaseSearch);
}

int PatriciaTriePolicy::getUnigramProbability(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_PROBABILITY;
    }
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
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
    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
    return PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
}

int PatriciaTriePolicy::getShortcutPositionOfNode(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    if (!PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        return NOT_A_DICT_POS;
    }
    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
    if (PatriciaTrieReadingUtils::isTerminal(flags)) {
        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
    }
    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(mDictRoot, flags, &pos);
    }
    return pos;
}

int PatriciaTriePolicy::getBigramsPositionOfNode(const int nodePos) const {
    if (nodePos == NOT_A_VALID_WORD_POS) {
        return NOT_A_DICT_POS;
    }
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    if (!PatriciaTrieReadingUtils::hasBigrams(flags)) {
        return NOT_A_DICT_POS;
    }
    PatriciaTrieReadingUtils::skipCharacters(mDictRoot, flags, MAX_WORD_LENGTH, &pos);
    if (PatriciaTrieReadingUtils::isTerminal(flags)) {
        PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos);
    }
    if (PatriciaTrieReadingUtils::hasChildrenInFlags(flags)) {
        PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(mDictRoot, flags, &pos);
    }
    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        BinaryDictionaryTerminalAttributesReadingUtils::skipShortcuts(mBinaryDictionaryInfo, &pos);
    }
    return pos;
}

int PatriciaTriePolicy::createAndGetLeavingChildNode(const DicNode *const dicNode,
        const int nodePos,  const NodeFilter *const childrenFilter,
        DicNodeVector *childDicNodes) const {
    int pos = nodePos;
    const PatriciaTrieReadingUtils::NodeFlags flags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(mDictRoot, &pos);
    int mergedNodeCodePoints[MAX_WORD_LENGTH];
    const int mergedNodeCodePointCount = PatriciaTrieReadingUtils::getCharsAndAdvancePosition(
            mDictRoot, flags, MAX_WORD_LENGTH, mergedNodeCodePoints, &pos);
    const int probability = (PatriciaTrieReadingUtils::isTerminal(flags))?
            PatriciaTrieReadingUtils::readProbabilityAndAdvancePosition(mDictRoot, &pos)
                    : NOT_A_PROBABILITY;
    const int childrenPos = PatriciaTrieReadingUtils::hasChildrenInFlags(flags) ?
            PatriciaTrieReadingUtils::readChildrenPositionAndAdvancePosition(
                    mDictRoot, flags, &pos) : NOT_A_DICT_POS;
    if (PatriciaTrieReadingUtils::hasShortcutTargets(flags)) {
        BinaryDictionaryTerminalAttributesReadingUtils::skipShortcuts(mBinaryDictionaryInfo, &pos);
    }
    if (PatriciaTrieReadingUtils::hasBigrams(flags)) {
        BinaryDictionaryTerminalAttributesReadingUtils::skipExistingBigrams(
                mBinaryDictionaryInfo, &pos);
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
