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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_POLICY_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_header.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/policyimpl/dictionary/bigram/bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/shortcut_list_policy.h"

namespace latinime {

class DicNode;
class DicNodeVector;

class DynamicPatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    DynamicPatriciaTriePolicy(const uint8_t *const dictBuf)
            : mHeader(dictBuf), mDictRoot(dictBuf + mHeader.getSize()),
              mBigramListPolicy(mDictRoot), mShortcutListPolicy(mDictRoot) {}

    ~DynamicPatriciaTriePolicy() {}

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    void createAndGetAllChildNodes(const DicNode *const dicNode,
            const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const;

    int getCodePointsAndProbabilityAndReturnCodePointCount(
            const int terminalNodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const;

    int getTerminalNodePositionOfWord(const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const;

    int getUnigramProbability(const int nodePos) const;

    int getShortcutPositionOfNode(const int nodePos) const;

    int getBigramsPositionOfNode(const int nodePos) const;

    // TODO: Remove and use policy to access header information.
    const BinaryDictionaryHeader *getHeader() const {
        return &mHeader;
    }

    const DictionaryBigramsStructurePolicy *getBigramsStructurePolicy() const {
        return &mBigramListPolicy;
    }

    const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const {
        return &mShortcutListPolicy;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTriePolicy);
    static const int MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP;

    const BinaryDictionaryHeader mHeader;
    // TODO: Consolidate mDictRoot.
    const uint8_t *const mDictRoot;
    const BigramListPolicy mBigramListPolicy;
    const ShortcutListPolicy mShortcutListPolicy;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_PATRICIA_TRIE_POLICY_H
