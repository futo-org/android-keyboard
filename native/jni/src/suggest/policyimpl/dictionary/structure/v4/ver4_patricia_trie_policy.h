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
#include "suggest/policyimpl/dictionary/bigram/ver4_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/ver4_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_updating_helper.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_writer.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_writing_helper.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

class DicNode;
class DicNodeVector;

// TODO: Implement.
class Ver4PatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    Ver4PatriciaTriePolicy(const Ver4DictBuffers::Ver4DictBuffersPtr &buffers)
            : mBuffers(buffers), mHeaderPolicy(mBuffers.get()->getHeaderPolicy()),
              mDictBuffer(mBuffers.get()->getWritableTrieBuffer()),
              mBigramPolicy(mBuffers.get()->getMutableBigramDictContent(),
                      mBuffers.get()->getTerminalPositionLookupTable(), mHeaderPolicy,
                      mHeaderPolicy->isDecayingDict()),
              mShortcutPolicy(mBuffers.get()->getMutableShortcutDictContent(),
                      mBuffers.get()->getTerminalPositionLookupTable()),
              mNodeReader(mDictBuffer, mBuffers.get()->getProbabilityDictContent()),
              mNodeWriter(mDictBuffer, mBuffers.get(), &mNodeReader, &mBigramPolicy,
                      &mShortcutPolicy, mHeaderPolicy->isDecayingDict()),
              mUpdatingHelper(mDictBuffer, &mNodeReader, &mNodeWriter),
              mWritingHelper(mBuffers.get()),
              mUnigramCount(mHeaderPolicy->getUnigramCount()),
              mBigramCount(mHeaderPolicy->getBigramCount()), mNeedsToDecayForTesting(false) {};

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
        return mHeaderPolicy;
    }

    const DictionaryBigramsStructurePolicy *getBigramsStructurePolicy() const {
        return &mBigramPolicy;
    }

    const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const {
        return &mShortcutPolicy;
    }

    bool addUnigramWord(const int *const word, const int length, const int probability,
            const int *const shortcutTargetCodePoints, const int shortcutLength,
            const int shortcutProbability, const bool isNotAWord, const bool isBlacklisted,
            const int timestamp);

    bool addBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1, const int probability, const int timestamp);

    bool removeBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1);

    void flush(const char *const filePath);

    void flushWithGC(const char *const filePath);

    bool needsToRunGC(const bool mindsBlockByGC) const;

    void getProperty(const char *const query, const int queryLength, char *const outResult,
            const int maxResultLength);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Ver4PatriciaTriePolicy);

    static const char *const UNIGRAM_COUNT_QUERY;
    static const char *const BIGRAM_COUNT_QUERY;
    static const char *const MAX_UNIGRAM_COUNT_QUERY;
    static const char *const MAX_BIGRAM_COUNT_QUERY;
    static const char *const SET_NEEDS_TO_DECAY_FOR_TESTING_QUERY;
    // When the dictionary size is near the maximum size, we have to refuse dynamic operations to
    // prevent the dictionary from overflowing.
    static const int MARGIN_TO_REFUSE_DYNAMIC_OPERATIONS;
    static const int MIN_DICT_SIZE_TO_REFUSE_DYNAMIC_OPERATIONS;

    Ver4DictBuffers::Ver4DictBuffersPtr mBuffers;
    const HeaderPolicy *const mHeaderPolicy;
    BufferWithExtendableBuffer *const mDictBuffer;
    Ver4BigramListPolicy mBigramPolicy;
    Ver4ShortcutListPolicy mShortcutPolicy;
    Ver4PatriciaTrieNodeReader mNodeReader;
    Ver4PatriciaTrieNodeWriter mNodeWriter;
    DynamicPatriciaTrieUpdatingHelper mUpdatingHelper;
    Ver4PatriciaTrieWritingHelper mWritingHelper;
    int mUnigramCount;
    int mBigramCount;
    bool mNeedsToDecayForTesting;
};
} // namespace latinime
#endif // LATINIME_VER4_PATRICIA_TRIE_POLICY_H
