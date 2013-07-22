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
    // TODO: Move children creating methods form DicNodeUtils.
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

} // namespace latinime
