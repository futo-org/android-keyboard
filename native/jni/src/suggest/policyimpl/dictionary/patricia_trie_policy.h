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

#ifndef LATINIME_PATRICIA_TRIE_POLICY_H
#define LATINIME_PATRICIA_TRIE_POLICY_H

#include "defines.h"
#include "suggest/core/policy/dictionary_structure_policy.h"

namespace latinime {

class PatriciaTriePolicy : public DictionaryStructurePolicy {
 public:
    static AK_FORCE_INLINE const PatriciaTriePolicy *getInstance() {
        return &sInstance;
    }

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    void createAndGetAllChildNodes(const DicNode *const dicNode,
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const NodeFilter *const nodeFilter, DicNodeVector *const childDicNodes) const;

    int getCodePointsAndProbabilityAndReturnCodePointCount(
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int terminalNodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const;

    int getTerminalNodePositionOfWord(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const;

    int getUnigramProbability(const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const int nodePos) const;

 private:
    DISALLOW_COPY_AND_ASSIGN(PatriciaTriePolicy);
    static const PatriciaTriePolicy sInstance;

    PatriciaTriePolicy() {}
    ~PatriciaTriePolicy() {}
};
} // namespace latinime
#endif // LATINIME_PATRICIA_TRIE_POLICY_H
