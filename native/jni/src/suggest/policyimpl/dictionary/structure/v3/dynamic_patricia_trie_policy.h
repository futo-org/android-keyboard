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

#include "defines.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/dynamic_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

class DicNode;
class DicNodeVector;

class DynamicPatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    DynamicPatriciaTriePolicy(const MmappedBuffer::MmappedBufferPtr &mmappedBuffer)
            : mMmappedBuffer(mmappedBuffer),
              mHeaderPolicy(mMmappedBuffer.get()->getBuffer(), FormatUtils::VERSION_3),
              mBufferWithExtendableBuffer(mMmappedBuffer.get()->getBuffer()
                      + mHeaderPolicy.getSize(), mMmappedBuffer.get()->getBufferSize()
                              - mHeaderPolicy.getSize(),
                                      BufferWithExtendableBuffer
                                              ::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
              mShortcutListPolicy(&mBufferWithExtendableBuffer),
              mBigramListPolicy(&mHeaderPolicy, &mBufferWithExtendableBuffer, &mShortcutListPolicy,
                      mHeaderPolicy.isDecayingDict()),
              mNodeReader(&mBufferWithExtendableBuffer, &mBigramListPolicy, &mShortcutListPolicy) {}

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
        return &mBigramListPolicy;
    }

    const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const {
        return &mShortcutListPolicy;
    }

    bool addUnigramWord(const int *const word, const int length, const int probability,
            const int timestamp) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: addUnigramWord() is called for non-updatable dictionary.");
        return false;
    }

    bool addBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1, const int probability, const int timestamp) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: addBigramWords() is called for non-updatable dictionary.");
        return false;
    }

    bool removeBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: removeBigramWords() is called for non-updatable dictionary.");
        return false;
    }

    void flush(const char *const filePath) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: flush() is called for non-updatable dictionary.");
    }

    void flushWithGC(const char *const filePath) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: flushWithGC() is called for non-updatable dictionary.");
    }

    bool needsToRunGC(const bool mindsBlockByGC) const {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: needsToRunGC() is called for non-updatable dictionary.");
        return false;
    }

    void getProperty(const char *const query, const int queryLength, char *const outResult,
            const int maxResultLength) {
        // getProperty is not supported for this class.
        if (maxResultLength > 0) {
            outResult[0] = '\0';
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTriePolicy);

    const MmappedBuffer::MmappedBufferPtr mMmappedBuffer;
    const HeaderPolicy mHeaderPolicy;
    BufferWithExtendableBuffer mBufferWithExtendableBuffer;
    DynamicShortcutListPolicy mShortcutListPolicy;
    DynamicBigramListPolicy mBigramListPolicy;
    DynamicPatriciaTrieNodeReader mNodeReader;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_PATRICIA_TRIE_POLICY_H
