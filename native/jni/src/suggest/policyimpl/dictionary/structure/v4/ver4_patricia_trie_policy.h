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

#ifndef LATINIME_VER4_PATRICIA_TRIE_POLICY_H
#define LATINIME_VER4_PATRICIA_TRIE_POLICY_H

#include "defines.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

class DicNode;
class DicNodeVector;

// TODO: Implement.
class Ver4PatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    Ver4PatriciaTriePolicy(const Ver4DictBuffers::Ver4DictBuffersPtr &buffers)
            : mBuffers(buffers),
              mHeaderPolicy(mBuffers.get()->getRawDictBuffer(), FormatUtils::VERSION_4),
              mDictBuffer(mBuffers.get()->getRawDictBuffer() + mHeaderPolicy.getSize(),
                      mBuffers.get()->getRawDictBufferSize() - mHeaderPolicy.getSize(),
                      BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
              mNodeReader(&mDictBuffer, mBuffers.get()->getProbabilityDictContent()) {};

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    void createAndGetAllChildDicNodes(const DicNode *const dicNode,
            DicNodeVector *const childDicNodes) const;

    int getCodePointsAndProbabilityAndReturnCodePointCount(
            const int terminalPtNodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const;

    int getTerminalPtNodePositionOfWord(const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const;

    int getProbability(const int unigramProbability, const int bigramProbability) const;

    int getUnigramProbabilityOfPtNode(const int ptNodePos) const;

    int getShortcutPositionOfPtNode(const int ptNodePos) const;

    int getBigramsPositionOfPtNode(const int ptNodePos) const;

    const DictionaryHeaderStructurePolicy *getHeaderStructurePolicy() const {
        return &mHeaderPolicy;
    }

    const DictionaryBigramsStructurePolicy *getBigramsStructurePolicy() const {
        return 0;
    }

    const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const {
        return 0;
    }

    bool addUnigramWord(const int *const word, const int length, const int probability);

    bool addBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1, const int probability);

    bool removeBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1);

    void flush(const char *const filePath);

    void flushWithGC(const char *const filePath);

    bool needsToRunGC(const bool mindsBlockByGC) const;

    void getProperty(const char *const query, char *const outResult,
            const int maxResultLength);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Ver4PatriciaTriePolicy);

    const Ver4DictBuffers::Ver4DictBuffersPtr mBuffers;
    const HeaderPolicy mHeaderPolicy;
    BufferWithExtendableBuffer mDictBuffer;
    Ver4PatriciaTrieNodeReader mNodeReader;
};
} // namespace latinime
#endif // LATINIME_VER4_PATRICIA_TRIE_POLICY_H
