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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_GC_EVENT_LISTENERS_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_GC_EVENT_LISTENERS_H

#include <vector>

#include "defines.h"
#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_helper.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "utils/hash_map_compat.h"

namespace latinime {

class DictionaryHeaderStructurePolicy;

class DynamicPatriciaTrieGcEventListeners {
 public:
    // Updates all PtNodes that can be reached from the root. Checks if each PtNode is useless or
    // not and marks useless PtNodes as deleted. Such deleted PtNodes will be discarded in the GC.
    // TODO: Concatenate non-terminal PtNodes.
    class TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
        : public DynamicPatriciaTrieReadingHelper::TraversingEventListener {
     public:
        TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted(
                const DictionaryHeaderStructurePolicy *const headerPolicy,
                DynamicPatriciaTrieWritingHelper *const writingHelper,
                BufferWithExtendableBuffer *const buffer, const bool isDecayingDict)
                : mHeaderPolicy(headerPolicy), mWritingHelper(writingHelper), mBuffer(buffer),
                  mIsDecayingDict(isDecayingDict), mValueStack(), mChildrenValue(0),
                  mValidUnigramCount(0) {}

        ~TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted() {};

        bool onAscend() {
            if (mValueStack.empty()) {
                return false;
            }
            mChildrenValue = mValueStack.back();
            mValueStack.pop_back();
            return true;
        }

        bool onDescend(const int ptNodeArrayPos) {
            mValueStack.push_back(0);
            mChildrenValue = 0;
            return true;
        }

        bool onReadingPtNodeArrayTail() { return true; }

        bool onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints);

        int getValidUnigramCount() const {
            return mValidUnigramCount;
        }

     private:
        DISALLOW_IMPLICIT_CONSTRUCTORS(
                TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted);

        const DictionaryHeaderStructurePolicy *const mHeaderPolicy;
        DynamicPatriciaTrieWritingHelper *const mWritingHelper;
        BufferWithExtendableBuffer *const mBuffer;
        const bool mIsDecayingDict;
        std::vector<int> mValueStack;
        int mChildrenValue;
        int mValidUnigramCount;
    };

    // Updates all bigram entries that are held by valid PtNodes. This removes useless bigram
    // entries.
    class TraversePolicyToUpdateBigramProbability
            : public DynamicPatriciaTrieReadingHelper::TraversingEventListener {
     public:
        TraversePolicyToUpdateBigramProbability(
                DynamicBigramListPolicy *const bigramPolicy)
                : mBigramPolicy(bigramPolicy), mValidBigramEntryCount(0) {}

        bool onAscend() { return true; }

        bool onDescend(const int ptNodeArrayPos) { return true; }

        bool onReadingPtNodeArrayTail() { return true; }

        bool onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints);

        int getValidBigramEntryCount() const {
            return mValidBigramEntryCount;
        }

     private:
        DISALLOW_IMPLICIT_CONSTRUCTORS(TraversePolicyToUpdateBigramProbability);

        DynamicBigramListPolicy *const mBigramPolicy;
        int mValidBigramEntryCount;
    };

    class TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
            : public DynamicPatriciaTrieReadingHelper::TraversingEventListener {
     public:
        TraversePolicyToPlaceAndWriteValidPtNodesToBuffer(
                DynamicPatriciaTrieWritingHelper *const writingHelper,
                BufferWithExtendableBuffer *const bufferToWrite,
                DynamicPatriciaTrieWritingHelper::DictPositionRelocationMap *const
                        dictPositionRelocationMap)
                : mWritingHelper(writingHelper), mBufferToWrite(bufferToWrite),
                  mDictPositionRelocationMap(dictPositionRelocationMap), mValidPtNodeCount(0),
                  mPtNodeArraySizeFieldPos(NOT_A_DICT_POS) {};

        bool onAscend() { return true; }

        bool onDescend(const int ptNodeArrayPos);

        bool onReadingPtNodeArrayTail();

        bool onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints);

     private:
        DISALLOW_IMPLICIT_CONSTRUCTORS(TraversePolicyToPlaceAndWriteValidPtNodesToBuffer);

        DynamicPatriciaTrieWritingHelper *const mWritingHelper;
        BufferWithExtendableBuffer *const mBufferToWrite;
        DynamicPatriciaTrieWritingHelper::DictPositionRelocationMap *const
                mDictPositionRelocationMap;
        int mValidPtNodeCount;
        int mPtNodeArraySizeFieldPos;
    };

    class TraversePolicyToUpdateAllPositionFields
            : public DynamicPatriciaTrieReadingHelper::TraversingEventListener {
     public:
        TraversePolicyToUpdateAllPositionFields(
                DynamicPatriciaTrieWritingHelper *const writingHelper,
                DynamicBigramListPolicy *const bigramPolicy,
                BufferWithExtendableBuffer *const bufferToWrite,
                const DynamicPatriciaTrieWritingHelper::DictPositionRelocationMap *const
                        dictPositionRelocationMap)
                : mWritingHelper(writingHelper), mBigramPolicy(bigramPolicy),
                  mBufferToWrite(bufferToWrite),
                  mDictPositionRelocationMap(dictPositionRelocationMap), mUnigramCount(0),
                  mBigramCount(0) {};

        bool onAscend() { return true; }

        bool onDescend(const int ptNodeArrayPos) { return true; }

        bool onReadingPtNodeArrayTail() { return true; }

        bool onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node,
                const int *const nodeCodePoints);

        int getUnigramCount() const {
            return mUnigramCount;
        }

        int getBigramCount() const {
            return mBigramCount;
        }

     private:
        DISALLOW_IMPLICIT_CONSTRUCTORS(TraversePolicyToUpdateAllPositionFields);

        DynamicPatriciaTrieWritingHelper *const mWritingHelper;
        DynamicBigramListPolicy *const mBigramPolicy;
        BufferWithExtendableBuffer *const mBufferToWrite;
        const DynamicPatriciaTrieWritingHelper::DictPositionRelocationMap *const
                mDictPositionRelocationMap;
        int mUnigramCount;
        int mBigramCount;
    };

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTrieGcEventListeners);
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_GC_EVENT_LISTENERS_H */
