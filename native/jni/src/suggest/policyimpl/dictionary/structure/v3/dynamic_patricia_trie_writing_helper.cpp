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

#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_helper.h"

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_writer.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_gc_event_listeners.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_writer.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/dynamic_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/utils/dict_file_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"
#include "utils/hash_map_compat.h"

namespace latinime {

// TODO: Make MAX_DICTIONARY_SIZE 8MB.
const size_t DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE = 2 * 1024 * 1024;

void DynamicPatriciaTrieWritingHelper::writeToDictFile(const char *const fileName,
        const HeaderPolicy *const headerPolicy, const int unigramCount, const int bigramCount) {
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    const int extendedRegionSize = headerPolicy->getExtendedRegionSize() +
            mBuffer->getUsedAdditionalBufferSize();
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, false /* updatesLastUpdatedTime */,
            false /* updatesLastDecayedTime */, unigramCount, bigramCount, extendedRegionSize)) {
        return;
    }
    DictFileWritingUtils::flushAllHeaderAndBodyToFile(fileName, &headerBuffer, mBuffer);
}

void DynamicPatriciaTrieWritingHelper::writeToDictFileWithGC(const int rootPtNodeArrayPos,
        const char *const fileName, const HeaderPolicy *const headerPolicy) {
    BufferWithExtendableBuffer newDictBuffer(MAX_DICTIONARY_SIZE);
    int unigramCount = 0;
    int bigramCount = 0;
    if (mNeedsToDecay) {
        ForgettingCurveUtils::sTimeKeeper.setCurrentTime();
    }
    if (!runGC(rootPtNodeArrayPos, headerPolicy, &newDictBuffer, &unigramCount, &bigramCount)) {
        return;
    }
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, true /* updatesLastUpdatedTime */,
            mNeedsToDecay, unigramCount, bigramCount, 0 /* extendedRegionSize */)) {
        return;
    }
    DictFileWritingUtils::flushAllHeaderAndBodyToFile(fileName, &headerBuffer, &newDictBuffer);
}

// TODO: Make this method version independent.
bool DynamicPatriciaTrieWritingHelper::runGC(const int rootPtNodeArrayPos,
        const HeaderPolicy *const headerPolicy, BufferWithExtendableBuffer *const bufferToWrite,
        int *const outUnigramCount, int *const outBigramCount) {
    DynamicPatriciaTrieNodeReader ptNodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    DynamicPatriciaTrieReadingHelper readingHelper(mBuffer, &ptNodeReader);
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners
            ::TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
                    traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted(
                            headerPolicy, mPtNodeWriter, mBuffer, mNeedsToDecay);
    if (!readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(
            &traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted)) {
        return false;
    }
    if (mNeedsToDecay && traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
            .getValidUnigramCount() > ForgettingCurveUtils::MAX_UNIGRAM_COUNT_AFTER_GC) {
        // TODO: Remove more unigrams.
    }

    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateBigramProbability
            traversePolicyToUpdateBigramProbability(mPtNodeWriter);
    if (!readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(
            &traversePolicyToUpdateBigramProbability)) {
        return false;
    }
    if (mNeedsToDecay && traversePolicyToUpdateBigramProbability.getValidBigramEntryCount()
            > ForgettingCurveUtils::MAX_BIGRAM_COUNT_AFTER_GC) {
        // TODO: Remove more bigrams.
    }

    // Mapping from positions in mBuffer to positions in bufferToWrite.
    PtNodeWriter::DictPositionRelocationMap dictPositionRelocationMap;
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieNodeWriter newPtNodeWriter(bufferToWrite, &ptNodeReader, mBigramPolicy,
            mShortcutPolicy);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
            traversePolicyToPlaceAndWriteValidPtNodesToBuffer(&newPtNodeWriter, bufferToWrite,
                    &dictPositionRelocationMap);
    if (!readingHelper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            &traversePolicyToPlaceAndWriteValidPtNodesToBuffer)) {
        return false;
    }

    // Create policy instance for the GCed dictionary.
    DynamicShortcutListPolicy newDictShortcutPolicy(bufferToWrite);
    DynamicBigramListPolicy newDictBigramPolicy(headerPolicy, bufferToWrite, &newDictShortcutPolicy,
            mNeedsToDecay);
    // Create reading node reader and reading helper for the GCed dictionary.
    DynamicPatriciaTrieNodeReader newDictNodeReader(bufferToWrite, &newDictBigramPolicy,
            &newDictShortcutPolicy);
    DynamicPatriciaTrieReadingHelper newDictReadingHelper(bufferToWrite, &newDictNodeReader);
    DynamicPatriciaTrieNodeWriter newDictNodeWriter(bufferToWrite, &newDictNodeReader,
            &newDictBigramPolicy, &newDictShortcutPolicy);
    newDictReadingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateAllPositionFields
            traversePolicyToUpdateAllPositionFields(&newDictNodeWriter, &dictPositionRelocationMap);
    if (!newDictReadingHelper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            &traversePolicyToUpdateAllPositionFields)) {
        return false;
    }
    *outUnigramCount = traversePolicyToUpdateAllPositionFields.getUnigramCount();
    *outBigramCount = traversePolicyToUpdateAllPositionFields.getBigramCount();
    return true;
}

} // namespace latinime
