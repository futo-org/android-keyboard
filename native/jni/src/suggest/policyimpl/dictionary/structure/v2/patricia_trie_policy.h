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

#include <cstdint>
#include <vector>

#include "defines.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/structure/v2/bigram/bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v2/shortcut/shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v2/ver2_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v2/ver2_pt_node_array_reader.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"
#include "suggest/policyimpl/dictionary/utils/mmapped_buffer.h"
#include "utils/byte_array_view.h"

namespace latinime {

class DicNode;
class DicNodeVector;

class PatriciaTriePolicy : public DictionaryStructureWithBufferPolicy {
 public:
    PatriciaTriePolicy(MmappedBuffer::MmappedBufferPtr mmappedBuffer)
            : mMmappedBuffer(std::move(mmappedBuffer)),
              mHeaderPolicy(mMmappedBuffer->getReadOnlyByteArrayView().data(),
                      FormatUtils::VERSION_2),
              mDictRoot(mMmappedBuffer->getReadOnlyByteArrayView().data()
                      + mHeaderPolicy.getSize()),
              mDictBufferSize(mMmappedBuffer->getReadOnlyByteArrayView().size()
                      - mHeaderPolicy.getSize()),
              mBigramListPolicy(mDictRoot, mDictBufferSize), mShortcutListPolicy(mDictRoot),
              mPtNodeReader(mDictRoot, mDictBufferSize, &mBigramListPolicy, &mShortcutListPolicy),
              mPtNodeArrayReader(mDictRoot, mDictBufferSize),
              mTerminalPtNodePositionsForIteratingWords(), mIsCorrupted(false) {}

    AK_FORCE_INLINE int getRootPosition() const {
        return 0;
    }

    void createAndGetAllChildDicNodes(const DicNode *const dicNode,
            DicNodeVector *const childDicNodes) const;

    int getCodePointsAndProbabilityAndReturnCodePointCount(
            const int terminalNodePos, const int maxCodePointCount, int *const outCodePoints,
            int *const outUnigramProbability) const;

    int getTerminalPtNodePositionOfWord(const int *const inWord,
            const int length, const bool forceLowerCaseSearch) const;

    int getProbability(const int unigramProbability, const int bigramProbability) const;

    int getProbabilityOfPtNode(const int *const prevWordsPtNodePos, const int ptNodePos) const;

    void iterateNgramEntries(const int *const prevWordsPtNodePos,
            NgramListener *const listener) const;

    int getShortcutPositionOfPtNode(const int ptNodePos) const;

    const DictionaryHeaderStructurePolicy *getHeaderStructurePolicy() const {
        return &mHeaderPolicy;
    }

    const DictionaryShortcutsStructurePolicy *getShortcutsStructurePolicy() const {
        return &mShortcutListPolicy;
    }

    bool addUnigramEntry(const int *const word, const int length,
            const UnigramProperty *const unigramProperty) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: addUnigramEntry() is called for non-updatable dictionary.");
        return false;
    }

    bool removeUnigramEntry(const int *const word, const int length) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: removeUnigramEntry() is called for non-updatable dictionary.");
        return false;
    }

    bool addNgramEntry(const PrevWordsInfo *const prevWordsInfo,
            const BigramProperty *const bigramProperty) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: addNgramEntry() is called for non-updatable dictionary.");
        return false;
    }

    bool removeNgramEntry(const PrevWordsInfo *const prevWordsInfo, const int *const word,
            const int length) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: removeNgramEntry() is called for non-updatable dictionary.");
        return false;
    }

    bool flush(const char *const filePath) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: flush() is called for non-updatable dictionary.");
        return false;
    }

    bool flushWithGC(const char *const filePath) {
        // This method should not be called for non-updatable dictionary.
        AKLOGI("Warning: flushWithGC() is called for non-updatable dictionary.");
        return false;
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

    const WordProperty getWordProperty(const int *const codePoints,
            const int codePointCount) const;

    int getNextWordAndNextToken(const int token, int *const outCodePoints,
            int *const outCodePointCount);

    bool isCorrupted() const {
        return mIsCorrupted;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(PatriciaTriePolicy);

    const MmappedBuffer::MmappedBufferPtr mMmappedBuffer;
    const HeaderPolicy mHeaderPolicy;
    const uint8_t *const mDictRoot;
    const int mDictBufferSize;
    const BigramListPolicy mBigramListPolicy;
    const ShortcutListPolicy mShortcutListPolicy;
    const Ver2ParticiaTrieNodeReader mPtNodeReader;
    const Ver2PtNodeArrayReader mPtNodeArrayReader;
    std::vector<int> mTerminalPtNodePositionsForIteratingWords;
    mutable bool mIsCorrupted;

    int getBigramsPositionOfPtNode(const int ptNodePos) const;
    int createAndGetLeavingChildNode(const DicNode *const dicNode, const int ptNodePos,
            DicNodeVector *const childDicNodes) const;
};
} // namespace latinime
#endif // LATINIME_PATRICIA_TRIE_POLICY_H
