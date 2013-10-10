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

#include <cstddef>
#include <vector>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_utils.h"

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
    class TraversingEventListener {
     public:
        virtual ~TraversingEventListener() {};

        // Returns whether the event handling was succeeded or not.
        virtual bool onAscend() = 0;

        // Returns whether the event handling was succeeded or not.
        virtual bool onDescend(const int ptNodeArrayPos) = 0;

        // Returns whether the event handling was succeeded or not.
        virtual bool onReadingPtNodeArrayTail() = 0;

        // Returns whether the event handling was succeeded or not.
        virtual bool onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints) = 0;

     protected:
        TraversingEventListener() {};

     private:
        DISALLOW_COPY_AND_ASSIGN(TraversingEventListener);
    };

    DynamicPatriciaTrieReadingHelper(const BufferWithExtendableBuffer *const buffer,
            const DictionaryBigramsStructurePolicy *const bigramsPolicy,
            const DictionaryShortcutsStructurePolicy *const shortcutsPolicy)
            : mIsError(false), mReadingState(), mBuffer(buffer),
              mNodeReader(mBuffer, bigramsPolicy, shortcutsPolicy), mReadingStateStack() {}

    ~DynamicPatriciaTrieReadingHelper() {}

    AK_FORCE_INLINE bool isError() const {
        return mIsError;
    }

    AK_FORCE_INLINE bool isEnd() const {
        return mReadingState.mPos == NOT_A_DICT_POS;
    }

    // Initialize reading state with the head position of a PtNode array.
    AK_FORCE_INLINE void initWithPtNodeArrayPos(const int ptNodeArrayPos) {
        if (ptNodeArrayPos == NOT_A_DICT_POS) {
            mReadingState.mPos = NOT_A_DICT_POS;
        } else {
            mIsError = false;
            mReadingState.mPos = ptNodeArrayPos;
            mReadingState.mTotalCodePointCountSinceInitialization = 0;
            mReadingState.mTotalPtNodeIndexInThisArrayChain = 0;
            mReadingState.mPtNodeArrayIndexInThisArrayChain = 0;
            mReadingState.mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            mReadingStateStack.clear();
            nextPtNodeArray();
            if (!isEnd()) {
                fetchPtNodeInfo();
            }
        }
    }

    // Initialize reading state with the head position of a node.
    AK_FORCE_INLINE void initWithPtNodePos(const int ptNodePos) {
        if (ptNodePos == NOT_A_DICT_POS) {
            mReadingState.mPos = NOT_A_DICT_POS;
        } else {
            mIsError = false;
            mReadingState.mPos = ptNodePos;
            mReadingState.mRemainingPtNodeCountInThisArray = 1;
            mReadingState.mTotalCodePointCountSinceInitialization = 0;
            mReadingState.mTotalPtNodeIndexInThisArrayChain = 1;
            mReadingState.mPtNodeArrayIndexInThisArrayChain = 1;
            mReadingState.mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            mReadingState.mPosOfThisPtNodeArrayHead = NOT_A_DICT_POS;
            mReadingStateStack.clear();
            fetchPtNodeInfo();
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
        return mReadingState.mTotalCodePointCountSinceInitialization;
    }

    // Return code point count include the last read node's code points.
    AK_FORCE_INLINE int getTotalCodePointCount() const {
        return mReadingState.mTotalCodePointCountSinceInitialization
                + mNodeReader.getCodePointCount();
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
        mReadingState.mRemainingPtNodeCountInThisArray -= 1;
        mReadingState.mPos = mNodeReader.getSiblingNodePos();
        if (mReadingState.mRemainingPtNodeCountInThisArray <= 0) {
            // All nodes in the current node array have been read.
            followForwardLink();
            if (!isEnd()) {
                fetchPtNodeInfo();
            }
        } else {
            fetchPtNodeInfo();
        }
    }

    // Read the first child node of the current node.
    AK_FORCE_INLINE void readChildNode() {
        if (mNodeReader.hasChildren()) {
            mReadingState.mTotalCodePointCountSinceInitialization +=
                    mNodeReader.getCodePointCount();
            mReadingState.mTotalPtNodeIndexInThisArrayChain = 0;
            mReadingState.mPtNodeArrayIndexInThisArrayChain = 0;
            mReadingState.mPos = mNodeReader.getChildrenPos();
            mReadingState.mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            // Read children node array.
            nextPtNodeArray();
            if (!isEnd()) {
                fetchPtNodeInfo();
            }
        } else {
            mReadingState.mPos = NOT_A_DICT_POS;
        }
    }

    // Read the parent node of the current node.
    AK_FORCE_INLINE void readParentNode() {
        if (mNodeReader.getParentPos() != NOT_A_DICT_POS) {
            mReadingState.mTotalCodePointCountSinceInitialization +=
                    mNodeReader.getCodePointCount();
            mReadingState.mTotalPtNodeIndexInThisArrayChain = 1;
            mReadingState.mPtNodeArrayIndexInThisArrayChain = 1;
            mReadingState.mRemainingPtNodeCountInThisArray = 1;
            mReadingState.mPos = mNodeReader.getParentPos();
            mReadingState.mPosOfLastForwardLinkField = NOT_A_DICT_POS;
            mReadingState.mPosOfThisPtNodeArrayHead = NOT_A_DICT_POS;
            fetchPtNodeInfo();
        } else {
            mReadingState.mPos = NOT_A_DICT_POS;
        }
    }

    AK_FORCE_INLINE int getPosOfLastForwardLinkField() const {
        return mReadingState.mPosOfLastForwardLinkField;
    }

    AK_FORCE_INLINE int getPosOfLastPtNodeArrayHead() const {
        return mReadingState.mPosOfThisPtNodeArrayHead;
    }

    AK_FORCE_INLINE void reloadCurrentPtNodeInfo() {
        if (!isEnd()) {
            fetchPtNodeInfo();
        }
    }

    bool traverseAllPtNodesInPostorderDepthFirstManner(TraversingEventListener *const listener);

    bool traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            TraversingEventListener *const listener);

 private:
    DISALLOW_COPY_AND_ASSIGN(DynamicPatriciaTrieReadingHelper);

    // This class encapsulates the reading state of a position in the dictionary. It points at a
    // specific PtNode in the dictionary.
    class PtNodeReadingState {
     public:
        // Note that copy constructor and assignment operator are used for this class to use
        // std::vector.
        PtNodeReadingState() : mPos(NOT_A_DICT_POS), mRemainingPtNodeCountInThisArray(0),
                mTotalCodePointCountSinceInitialization(0), mTotalPtNodeIndexInThisArrayChain(0),
                mPtNodeArrayIndexInThisArrayChain(0), mPosOfLastForwardLinkField(NOT_A_DICT_POS),
                mPosOfThisPtNodeArrayHead(NOT_A_DICT_POS) {}

        int mPos;
        // Remaining node count in the current array.
        int mRemainingPtNodeCountInThisArray;
        int mTotalCodePointCountSinceInitialization;
        // Counter of PtNodes used to avoid infinite loops caused by broken or malicious links.
        int mTotalPtNodeIndexInThisArrayChain;
        // Counter of PtNode arrays used to avoid infinite loops caused by cyclic links of empty
        // PtNode arrays.
        int mPtNodeArrayIndexInThisArrayChain;
        int mPosOfLastForwardLinkField;
        int mPosOfThisPtNodeArrayHead;
    };

    static const int MAX_CHILD_COUNT_TO_AVOID_INFINITE_LOOP;
    static const int MAX_PT_NODE_ARRAY_COUNT_TO_AVOID_INFINITE_LOOP;
    static const size_t MAX_READING_STATE_STACK_SIZE;

    // TODO: Introduce error code to track what caused the error.
    bool mIsError;
    PtNodeReadingState mReadingState;
    const BufferWithExtendableBuffer *const mBuffer;
    DynamicPatriciaTrieNodeReader mNodeReader;
    int mMergedNodeCodePoints[MAX_WORD_LENGTH];
    std::vector<PtNodeReadingState> mReadingStateStack;

    void nextPtNodeArray();

    void followForwardLink();

    AK_FORCE_INLINE void fetchPtNodeInfo() {
        mNodeReader.fetchNodeInfoInBufferFromPtNodePosAndGetNodeCodePoints(mReadingState.mPos,
                MAX_WORD_LENGTH, mMergedNodeCodePoints);
        if (mNodeReader.getCodePointCount() <= 0) {
            // Empty node is not allowed.
            mIsError = true;
            mReadingState.mPos = NOT_A_DICT_POS;
        }
    }

    AK_FORCE_INLINE void pushReadingStateToStack() {
        if (mReadingStateStack.size() > MAX_READING_STATE_STACK_SIZE) {
            AKLOGI("Reading state stack overflow. Max size: %zd", MAX_READING_STATE_STACK_SIZE);
            ASSERT(false);
            mIsError = true;
            mReadingState.mPos = NOT_A_DICT_POS;
        } else {
            mReadingStateStack.push_back(mReadingState);
        }
    }

    AK_FORCE_INLINE void popReadingStateFromStack() {
        if (mReadingStateStack.empty()) {
            mReadingState.mPos = NOT_A_DICT_POS;
        } else {
            mReadingState = mReadingStateStack.back();
            mReadingStateStack.pop_back();
            if (!isEnd()) {
                fetchPtNodeInfo();
            }
        }
    }
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_READING_HELPER_H */
