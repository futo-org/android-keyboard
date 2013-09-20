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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H

#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DictionaryBigramsStructurePolicy;
class DictionaryShortcutsStructurePolicy;

/*
 * This class is used for helping to read nodes of dynamic patricia trie. This class handles moved
 * node and reads node attributes.
 */
class DynamicPatriciaTrieNodeReader {
 public:
    DynamicPatriciaTrieNodeReader(const BufferWithExtendableBuffer *const buffer,
            const DictionaryBigramsStructurePolicy *const bigramsPolicy,
            const DictionaryShortcutsStructurePolicy *const shortcutsPolicy)
            : mBuffer(buffer), mBigramsPolicy(bigramsPolicy),
              mShortcutsPolicy(shortcutsPolicy), mHeadPos(NOT_A_DICT_POS), mFlags(0),
              mParentPos(NOT_A_DICT_POS), mCodePointCount(0), mProbabilityFieldPos(NOT_A_DICT_POS),
              mProbability(NOT_A_PROBABILITY), mChildrenPosFieldPos(NOT_A_DICT_POS),
              mChildrenPos(NOT_A_DICT_POS), mBigramLinkedNodePos(NOT_A_DICT_POS),
              mShortcutPos(NOT_A_DICT_POS),  mBigramPos(NOT_A_DICT_POS),
              mSiblingPos(NOT_A_DICT_POS) {}

    ~DynamicPatriciaTrieNodeReader() {}

    // Reads PtNode information from dictionary buffer and updates members with the information.
    AK_FORCE_INLINE void fetchNodeInfoInBufferFromPtNodePos(const int ptNodePos) {
        fetchNodeInfoInBufferFromPtNodePosAndGetNodeCodePoints(ptNodePos ,
                0 /* maxCodePointCount */, 0 /* outCodePoints */);
    }

    AK_FORCE_INLINE void fetchNodeInfoInBufferFromPtNodePosAndGetNodeCodePoints(
            const int ptNodePos, const int maxCodePointCount, int *const outCodePoints) {
        mSiblingPos = NOT_A_DICT_POS;
        mBigramLinkedNodePos = NOT_A_DICT_POS;
        fetchPtNodeInfoFromBufferAndProcessMovedPtNode(ptNodePos, maxCodePointCount, outCodePoints);
    }

    // HeadPos is different from NodePos when the current PtNode is a moved PtNode.
    AK_FORCE_INLINE int getHeadPos() const {
        return mHeadPos;
    }

    // Flags
    AK_FORCE_INLINE bool isDeleted() const {
        return DynamicPatriciaTrieReadingUtils::isDeleted(mFlags);
    }

    AK_FORCE_INLINE bool hasChildren() const {
        return mChildrenPos != NOT_A_DICT_POS;
    }

    AK_FORCE_INLINE bool isTerminal() const {
        return PatriciaTrieReadingUtils::isTerminal(mFlags);
    }

    AK_FORCE_INLINE bool isBlacklisted() const {
        return PatriciaTrieReadingUtils::isBlacklisted(mFlags);
    }

    AK_FORCE_INLINE bool isNotAWord() const {
        return PatriciaTrieReadingUtils::isNotAWord(mFlags);
    }

    // Parent node position
    AK_FORCE_INLINE int getParentPos() const {
        return mParentPos;
    }

    // Number of code points
    AK_FORCE_INLINE uint8_t getCodePointCount() const {
        return mCodePointCount;
    }

    // Probability
    AK_FORCE_INLINE int getProbabilityFieldPos() const {
        return mProbabilityFieldPos;
    }

    AK_FORCE_INLINE int getProbability() const {
        return mProbability;
    }

    // Children PtNode array position
    AK_FORCE_INLINE int getChildrenPosFieldPos() const {
        return mChildrenPosFieldPos;
    }

    AK_FORCE_INLINE int getChildrenPos() const {
        return mChildrenPos;
    }

    // Bigram linked node position.
    AK_FORCE_INLINE int getBigramLinkedNodePos() const {
        return mBigramLinkedNodePos;
    }

    // Shortcutlist position
    AK_FORCE_INLINE int getShortcutPos() const {
        return mShortcutPos;
    }

    // Bigrams position
    AK_FORCE_INLINE int getBigramsPos() const {
        return mBigramPos;
    }

    // Sibling node position
    AK_FORCE_INLINE int getSiblingNodePos() const {
        return mSiblingPos;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(DynamicPatriciaTrieNodeReader);

    const BufferWithExtendableBuffer *const mBuffer;
    const DictionaryBigramsStructurePolicy *const mBigramsPolicy;
    const DictionaryShortcutsStructurePolicy *const mShortcutsPolicy;
    int mHeadPos;
    DynamicPatriciaTrieReadingUtils::NodeFlags mFlags;
    int mParentPos;
    uint8_t mCodePointCount;
    int mProbabilityFieldPos;
    int mProbability;
    int mChildrenPosFieldPos;
    int mChildrenPos;
    int mBigramLinkedNodePos;
    int mShortcutPos;
    int mBigramPos;
    int mSiblingPos;

    void fetchPtNodeInfoFromBufferAndProcessMovedPtNode(const int ptNodePos,
            const int maxCodePointCount, int *const outCodePoints);

    void invalidatePtNodeInfo();
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H */
