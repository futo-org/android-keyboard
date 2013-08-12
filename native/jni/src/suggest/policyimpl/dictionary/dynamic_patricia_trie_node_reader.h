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

class DictionaryBigramsStructurePolicy;
class DictionaryShortcutsStructurePolicy;

/*
 * This class is used for helping to read nodes of dynamic patricia trie. This class handles moved
 * node and reads node attributes.
 */
class DynamicPatriciaTrieNodeReader {
 public:
    DynamicPatriciaTrieNodeReader(const uint8_t *const dictRoot,
            const DictionaryBigramsStructurePolicy *const bigramsPolicy,
            const DictionaryShortcutsStructurePolicy *const shortcutsPolicy)
            : mDictRoot(dictRoot), mBigramsPolicy(bigramsPolicy),
              mShortcutsPolicy(shortcutsPolicy), mNodePos(NOT_A_VALID_WORD_POS), mFlags(0),
              mParentPos(NOT_A_DICT_POS), mCodePointCount(0), mProbability(NOT_A_PROBABILITY),
              mChildrenPos(NOT_A_DICT_POS), mShortcutPos(NOT_A_DICT_POS),
              mBigramPos(NOT_A_DICT_POS), mSiblingPos(NOT_A_VALID_WORD_POS) {}

    ~DynamicPatriciaTrieNodeReader() {}

    // Reads node information from dictionary buffer and updates members with the information.
    AK_FORCE_INLINE void fetchNodeInfoFromBuffer(const int nodePos) {
        fetchNodeInfoFromBufferAndGetNodeCodePoints(nodePos , 0 /* maxCodePointCount */,
                0 /* outCodePoints */);
    }

    AK_FORCE_INLINE void fetchNodeInfoFromBufferAndGetNodeCodePoints(const int nodePos,
            const int maxCodePointCount, int *const outCodePoints) {
        mNodePos = nodePos;
        mSiblingPos = NOT_A_VALID_WORD_POS;
        fetchNodeInfoFromBufferAndProcessMovedNode(mNodePos, maxCodePointCount, outCodePoints);
    }

    AK_FORCE_INLINE int getNodePos() const {
        return mNodePos;
    }

    // Flags
    AK_FORCE_INLINE bool isDeleted() const {
        return DynamicPatriciaTrieReadingUtils::isDeleted(mFlags);
    }

    AK_FORCE_INLINE bool hasChildren() const {
        return PatriciaTrieReadingUtils::hasChildrenInFlags(mFlags);
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
    AK_FORCE_INLINE int getProbability() const {
        return mProbability;
    }

    // Children node group position
    AK_FORCE_INLINE int getChildrenPos() const {
        return mChildrenPos;
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

    // TODO: Consolidate mDictRoot.
    const uint8_t *const mDictRoot;
    const DictionaryBigramsStructurePolicy *const mBigramsPolicy;
    const DictionaryShortcutsStructurePolicy *const mShortcutsPolicy;
    int mNodePos;
    DynamicPatriciaTrieReadingUtils::NodeFlags mFlags;
    int mParentPos;
    uint8_t mCodePointCount;
    int mProbability;
    int mChildrenPos;
    int mShortcutPos;
    int mBigramPos;
    int mSiblingPos;

    void fetchNodeInfoFromBufferAndProcessMovedNode(const int nodePos, const int maxCodePointCount,
            int *const outCodePoints);
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H */
