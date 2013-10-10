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
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"

namespace latinime {

class DicNode;
class DicNodeVector;

class DynamicPatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    DynamicPatriciaTriePolicy(const MmappedBuffer *const buffer)
            : mBuffer(buffer), mHeaderPolicy(mBuffer->getBuffer(), buffer->getBufferSize()),
              mBufferWithExtendableBuffer(mBuffer->getBuffer() + mHeaderPolicy.getSize(),
                      mBuffer->getBufferSize() - mHeaderPolicy.getSize()),
              mShortcutListPolicy(&mBufferWithExtendableBuffer),
              mBigramListPolicy(&mHeaderPolicy, &mBufferWithExtendableBuffer, &mShortcutListPolicy,
                      mHeaderPolicy.isDecayingDict()),
              mUnigramCount(mHeaderPolicy.getUnigramCount()),
              mBigramCount(mHeaderPolicy.getBigramCount()), mNeedsToDecayForTesting(false) {}

    ~DynamicPatriciaTriePolicy() {
        delete mBuffer;
    }

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    void createAndGetAllChildNodes(const DicNode *const dicNode,
            DicNodeVector *const childDicNodes) const;

    int getCodePointsAndProbabilityAndReturnCodePointCount(
            const int terminalPtNodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const;

    int getTerminalNodePositionOfWord(const int *const inWord,
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
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTriePolicy);

    static const char *const UNIGRAM_COUNT_QUERY;
    static const char *const BIGRAM_COUNT_QUERY;
    static const char *const MAX_UNIGRAM_COUNT_QUERY;
    static const char *const MAX_BIGRAM_COUNT_QUERY;
    static const char *const SET_NEEDS_TO_DECAY_FOR_TESTING_QUERY;
    static const int MAX_DICT_EXTENDED_REGION_SIZE;
    static const int MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS;

    const MmappedBuffer *const mBuffer;
    const HeaderPolicy mHeaderPolicy;
    BufferWithExtendableBuffer mBufferWithExtendableBuffer;
    DynamicShortcutListPolicy mShortcutListPolicy;
    DynamicBigramListPolicy mBigramListPolicy;
    int mUnigramCount;
    int mBigramCount;
    int mNeedsToDecayForTesting;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_PATRICIA_TRIE_POLICY_H
