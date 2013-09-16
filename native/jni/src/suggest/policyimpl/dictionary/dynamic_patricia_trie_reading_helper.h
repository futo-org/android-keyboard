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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_READING_HELPER_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_READING_HELPER_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DictionaryBigramsStructurePolicy;
class DictionaryShortcutsStructurePolicy;

/*
 * This class is used for traversing dynamic patricia trie. This class supports iterating nodes and
 * dealing with additional buffer. This class counts nodes and node arrays to avoid infinite loop.
 */
class DynamicPatriciaTrieReadingHelper {
 public:
    DynamicPatriciaTrieReadingHelper(const BufferWithExtendableBuffer *const buffer,
            const DictionaryBigramsStructurePolicy *const bigramsPolicy,
            const DictionaryShortcutsStructurePolicy *const shortcutsPolicy)
            : mIsError(false), mPos(NOT_A_DICT_POS), mNodeCount(0), mPrevTotalCodePointCount(0),
              mTotalNodeCount(0), mNodeArrayCount(0), mPosOfLastForwardLinkField(NOT_A_DICT_POS),
              mBuffer(buffer), mNodeReader(mBuffer, bigramsPolicy, shortcutsPolicy) {}

    ~DynamicPatriciaTrieReadingHelper() {}

    AK_FORCE_INLINE bool isError() const {
        return mIsError;
    }

    AK_FORCE_INLINE bool isEnd() const {
        return mPos == NOT_A_DICT_POS;
    }

    // Initialize reading state with the head position of a node array.
    AK_FORCE_INLINE void initWithNodeArrayPos(const int nodeArrayPos) {
        if (nodeArrayPos == NOT_A_DICT_POS) {
            mPos = NOT_A_DICT_POS;
        } else {
            mIsError = false;
            mPos = nodeArrayPos;
            mNodeCount = 0;
            mPrevTotalCodePointCount = 0;
            mTotalNodeCount = 0;
            mNodeArrayCount = 0;
            mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            nextNodeArray();
            if (!isEnd()) {
                fetchNodeInfo();
            }
        }
    }

    // Initialize reading state with the head position of a node.
    AK_FORCE_INLINE void initWithNodePos(const int nodePos) {
        if (nodePos == NOT_A_DICT_POS) {
            mPos = NOT_A_DICT_POS;
        } else {
            mIsError = false;
            mPos = nodePos;
            mNodeCount = 1;
            mPrevTotalCodePointCount = 0;
            mTotalNodeCount = 1;
            mNodeArrayCount = 1;
            mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            fetchNodeInfo();
        }
    }

    AK_FORCE_INLINE const DynamicPatriciaTrieNodeReader* getNodeReader() const {
        return &mNodeReader;
    }

    AK_FORCE_INLINE bool isValidTerminalNode() const {
        return !isEnd() && !mNodeReader.isDeleted() && mNodeReader.isTerminal();
    }

    AK_FORCE_INLINE bool isMatchedCodePoint(const int index, const int codePoint) const {
        return mMergedNodeCodePoints[index] == codePoint;
    }

    // Return code point count exclude the last read node's code points.
    AK_FORCE_INLINE int getPrevTotalCodePointCount() const {
        return mPrevTotalCodePointCount;
    }

    // Return code point count include the last read node's code points.
    AK_FORCE_INLINE int getTotalCodePointCount() const {
        return mPrevTotalCodePointCount + mNodeReader.getCodePointCount();
    }

    AK_FORCE_INLINE void fetchMergedNodeCodePointsInReverseOrder(
            const int index, int *const outCodePoints) const {
        const int nodeCodePointCount = mNodeReader.getCodePointCount();
        for (int i =  0; i < nodeCodePointCount; ++i) {
            outCodePoints[index + i] = mMergedNodeCodePoints[nodeCodePointCount - 1 - i];
        }
    }

    AK_FORCE_INLINE const int *getMergedNodeCodePoints() const {
        return mMergedNodeCodePoints;
    }

    AK_FORCE_INLINE void readNextSiblingNode() {
        mNodeCount -= 1;
        mPos = mNodeReader.getSiblingNodePos();
        if (mNodeCount <= 0) {
            // All nodes in the current node array have been read.
            followForwardLink();
            if (!isEnd()) {
                fetchNodeInfo();
            }
        } else {
            fetchNodeInfo();
        }
    }

    // Read the first child node of the current node.
    AK_FORCE_INLINE void readChildNode() {
        if (mNodeReader.hasChildren()) {
            mPrevTotalCodePointCount += mNodeReader.getCodePointCount();
            mTotalNodeCount = 0;
            mNodeArrayCount = 0;
            mPos = mNodeReader.getChildrenPos();
            mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            // Read children node array.
            nextNodeArray();
            if (!isEnd()) {
                fetchNodeInfo();
            }
        } else {
            mPos = NOT_A_DICT_POS;
        }
    }

    // Read the parent node of the current node.
    AK_FORCE_INLINE void readParentNode() {
        if (mNodeReader.getParentPos() != NOT_A_DICT_POS) {
            mPrevTotalCodePointCount += mNodeReader.getCodePointCount();
            mTotalNodeCount = 1;
            mNodeArrayCount = 1;
            mNodeCount = 1;
            mPos = mNodeReader.getParentPos();
            mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            fetchNodeInfo();
        } else {
            mPos = NOT_A_DICT_POS;
        }
    }

    AK_FORCE_INLINE int getPosOfLastForwardLinkField() const {
        return mPosOfLastForwardLinkField;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(DynamicPatriciaTrieReadingHelper);

    static const int MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP;
    static const int MAX_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP;

    bool mIsError;
    int mPos;
    // Node count of a node array.
    int mNodeCount;
    int mPrevTotalCodePointCount;
    int mTotalNodeCount;
    int mNodeArrayCount;
    int mPosOfLastForwardLinkField;
    const BufferWithExtendableBuffer *const mBuffer;
    DynamicPatriciaTrieNodeReader mNodeReader;
    int mMergedNodeCodePoints[MAX_WORD_LENGTH];

    void nextNodeArray();

    void followForwardLink();

    AK_FORCE_INLINE void fetchNodeInfo() {
        mNodeReader.fetchNodeInfoFromBufferAndGetNodeCodePoints(mPos, MAX_WORD_LENGTH,
                mMergedNodeCodePoints);
        if (mNodeReader.getCodePointCount() <= 0) {
            // Empty node is not allowed.
            mIsError = true;
            mPos = NOT_A_DICT_POS;
        }
    }
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_READING_HELPER_H */
