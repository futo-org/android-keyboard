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
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/policyimpl/dictionary/bigram/bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/utils/extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

class DicNode;
class DicNodeVector;

class DynamicPatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    DynamicPatriciaTriePolicy(const MmappedBuffer *const buffer)
            : mBuffer(buffer), mExtendableBuffer(), mHeaderPolicy(mBuffer->getBuffer()),
              mDictRoot(mBuffer->getBuffer() + mHeaderPolicy.getSize()),
              mBigramListPolicy(mDictRoot), mShortcutListPolicy(mDictRoot) {}

    ~DynamicPatriciaTriePolicy() {
        delete mBuffer;
    }

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    void createAndGetAllChildNodes(const DicNode *const dicNode,
            DicNodeVector *const childDicNodes) const;

    int getCodePointsAndProbabilityAndReturnCodePointCount(
            const int terminalNodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const;

    int getTerminalNodePositionOfWord(const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const;

    int getUnigramProbability(const int nodePos) const;

    int getShortcutPositionOfNode(const int nodePos) const;

    int getBigramsPositionOfNode(const int nodePos) const;

    const DictionaryHeaderStructurePolicy *getHeaderStructurePolicy() const {
        return &mHeaderPolicy;
    }

    const DictionaryBigramsStructurePolicy *getBigramsStructurePolicy() const {
        return &mBigramListPolicy;
    }

    const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const {
        return &mShortcutListPolicy;
    }

    bool addUnigramWord(const int *const word, const int length, const int probability);

    bool addBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1, const int probability);

    bool removeBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTriePolicy);
    static const int MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP;

    const MmappedBuffer *const mBuffer;
    const ExtendableBuffer mExtendableBuffer;
    const HeaderPolicy mHeaderPolicy;
    // TODO: Consolidate mDictRoot.
    // CAVEAT!: Be careful about array out of bound access with mDictRoot
    const uint8_t *const mDictRoot;
    const BigramListPolicy mBigramListPolicy;
    const ShortcutListPolicy mShortcutListPolicy;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_PATRICIA_TRIE_POLICY_H
