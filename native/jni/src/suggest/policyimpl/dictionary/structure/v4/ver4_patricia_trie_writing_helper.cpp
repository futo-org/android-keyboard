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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_writing_helper.h"

#include <cstring>

#include "suggest/policyimpl/dictionary/bigram/ver4_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/ver4_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_writer.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

void Ver4PatriciaTrieWritingHelper::writeToDictFile(const char *const trieFilePath,
        const HeaderPolicy *const headerPolicy, const int unigramCount,
        const int bigramCount) const {
    const int dirPathBufSize = strlen(trieFilePath) + 1 /* terminator */;
    char dirPath[dirPathBufSize];
    FileUtils::getDirPath(trieFilePath, dirPathBufSize, dirPath);
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    const int extendedRegionSize = headerPolicy->getExtendedRegionSize()
            + mBuffers->getTrieBuffer()->getUsedAdditionalBufferSize();
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, false /* updatesLastUpdatedTime */,
            false /* updatesLastDecayedTime */, unigramCount, bigramCount, extendedRegionSize)) {
        AKLOGE("Cannot write header structure to buffer. updatesLastUpdatedTime: %d, "
                "updatesLastDecayedTime: %d, unigramCount: %d, bigramCount: %d, "
                "extendedRegionSize: %d", false, false, unigramCount, bigramCount,
                extendedRegionSize);
        return;
    }
    mBuffers->flushHeaderAndDictBuffers(dirPath, &headerBuffer);
}

void Ver4PatriciaTrieWritingHelper::writeToDictFileWithGC(const int rootPtNodeArrayPos,
        const char *const trieFilePath, const HeaderPolicy *const headerPolicy,
        const bool needsToDecay) {
    Ver4DictBuffers::Ver4DictBuffersPtr dictBuffers(
            Ver4DictBuffers::createVer4DictBuffers(headerPolicy));
    int unigramCount = 0;
    int bigramCount = 0;
    if (needsToDecay) {
        ForgettingCurveUtils::sTimeKeeper.setCurrentTime();
    }
    if (!runGC(rootPtNodeArrayPos, headerPolicy, dictBuffers.get(), &unigramCount, &bigramCount,
            needsToDecay)) {
        return;
    }
    BufferWithExtendableBuffer headerBuffer(
            BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE);
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, true /* updatesLastUpdatedTime */,
            needsToDecay, unigramCount, bigramCount, 0 /* extendedRegionSize */)) {
        return;
    }
    const int dirPathBufSize = strlen(trieFilePath) + 1 /* terminator */;
    char dirPath[dirPathBufSize];
    FileUtils::getDirPath(trieFilePath, dirPathBufSize, dirPath);
    dictBuffers.get()->flushHeaderAndDictBuffers(dirPath, &headerBuffer);
}

bool Ver4PatriciaTrieWritingHelper::runGC(const int rootPtNodeArrayPos,
        const HeaderPolicy *const headerPolicy, Ver4DictBuffers *const buffersToWrite,
        int *const outUnigramCount, int *const outBigramCount, const bool needsToDecay) {
    Ver4PatriciaTrieNodeReader ptNodeReader(mBuffers->getTrieBuffer(),
            mBuffers->getProbabilityDictContent());
    Ver4BigramListPolicy bigramPolicy(mBuffers->getUpdatableBigramDictContent(),
            mBuffers->getTerminalPositionLookupTable(), headerPolicy, needsToDecay);
    Ver4ShortcutListPolicy shortcutPolicy(mBuffers->getShortcutDictContent(),
            mBuffers->getTerminalPositionLookupTable());
    Ver4PatriciaTrieNodeWriter ptNodeWriter(mBuffers->getWritableTrieBuffer(),
            mBuffers, &ptNodeReader, &bigramPolicy, &shortcutPolicy,
            false /* needsToDecayWhenUpdating */);

    DynamicPatriciaTrieReadingHelper readingHelper(mBuffers->getTrieBuffer(), &ptNodeReader);
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners
            ::TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
                    traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted(
                            headerPolicy, &ptNodeWriter, mBuffers->getWritableTrieBuffer(),
                            needsToDecay);
    if (!readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(
            &traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted)) {
        return false;
    }
    if (needsToDecay && traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
            .getValidUnigramCount() > ForgettingCurveUtils::MAX_UNIGRAM_COUNT_AFTER_GC) {
        // TODO: Remove more unigrams.
    }

    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateBigramProbability
            traversePolicyToUpdateBigramProbability(&ptNodeWriter);
    if (!readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(
            &traversePolicyToUpdateBigramProbability)) {
        return false;
    }
    if (needsToDecay && traversePolicyToUpdateBigramProbability.getValidBigramEntryCount()
            > ForgettingCurveUtils::MAX_BIGRAM_COUNT_AFTER_GC) {
        // TODO: Remove more bigrams.
    }

    // Mapping from positions in mBuffer to positions in bufferToWrite.
    PtNodeWriter::DictPositionRelocationMap dictPositionRelocationMap;
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    Ver4PatriciaTrieNodeWriter ptNodeWriterForNewBuffers(buffersToWrite->getWritableTrieBuffer(),
            buffersToWrite, &ptNodeReader, &bigramPolicy, &shortcutPolicy,
            false /* needsToDecayWhenUpdating */);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
            traversePolicyToPlaceAndWriteValidPtNodesToBuffer(&ptNodeWriterForNewBuffers,
                    buffersToWrite->getWritableTrieBuffer(), &dictPositionRelocationMap);
    if (!readingHelper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            &traversePolicyToPlaceAndWriteValidPtNodesToBuffer)) {
        return false;
    }

    // Create policy instances for the GCed dictionary.
    Ver4PatriciaTrieNodeReader newPtNodeReader(buffersToWrite->getTrieBuffer(),
            buffersToWrite->getProbabilityDictContent());
    Ver4BigramListPolicy newBigramPolicy(buffersToWrite->getUpdatableBigramDictContent(),
            buffersToWrite->getTerminalPositionLookupTable(), headerPolicy,
            false /* needsToDecay */);
    Ver4ShortcutListPolicy newShortcutPolicy(buffersToWrite->getShortcutDictContent(),
            buffersToWrite->getTerminalPositionLookupTable());
    Ver4PatriciaTrieNodeWriter newPtNodeWriter(buffersToWrite->getWritableTrieBuffer(),
            buffersToWrite, &newPtNodeReader, &newBigramPolicy, &newShortcutPolicy,
            false /* needsToDecayWhenUpdating */);
    // Re-assign terminal IDs for valid terminal PtNodes.
    TerminalPositionLookupTable::TerminalIdMap terminalIdMap;
    if(!buffersToWrite->getUpdatableTerminalPositionLookupTable()->runGCTerminalIds(
            &terminalIdMap)) {
        return false;
    }
    // Run GC for probability dict content.
    if (!buffersToWrite->getUpdatableProbabilityDictContent()->runGC(&terminalIdMap,
            mBuffers->getProbabilityDictContent())) {
        return false;
    }
    // Run GC for bigram dict content.
    if(!buffersToWrite->getUpdatableBigramDictContent()->runGC(&terminalIdMap,
            mBuffers->getBigramDictContent(), outBigramCount)) {
        return false;
    }
    // Run GC for shortcut dict content.
    if(!buffersToWrite->getUpdatableShortcutDictContent()->runGC(&terminalIdMap,
            mBuffers->getShortcutDictContent())) {
        return false;
    }
    DynamicPatriciaTrieReadingHelper newDictReadingHelper(buffersToWrite->getTrieBuffer(),
            &newPtNodeReader);
    newDictReadingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateAllPositionFields
            traversePolicyToUpdateAllPositionFields(&newPtNodeWriter, &dictPositionRelocationMap);
    if (!newDictReadingHelper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            &traversePolicyToUpdateAllPositionFields)) {
        return false;
    }
    newDictReadingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    TraversePolicyToUpdateAllTerminalIds traversePolicyToUpdateAllTerminalIds(&newPtNodeWriter,
            &terminalIdMap);
    if (!newDictReadingHelper.traverseAllPtNodesInPostorderDepthFirstManner(
            &traversePolicyToUpdateAllTerminalIds)) {
        return false;
    }
    *outUnigramCount = traversePolicyToUpdateAllPositionFields.getUnigramCount();
    return true;
}

bool Ver4PatriciaTrieWritingHelper::TraversePolicyToUpdateAllTerminalIds::onVisitingPtNode(
        const PtNodeParams *const ptNodeParams) {
    if (!ptNodeParams->isTerminal()) {
        return true;
    }
    TerminalPositionLookupTable::TerminalIdMap::const_iterator it =
            mTerminalIdMap->find(ptNodeParams->getTerminalId());
    if (it == mTerminalIdMap->end()) {
        AKLOGE("terminal Id %d is not in the terminal position map. map size: %zd",
                ptNodeParams->getTerminalId(), mTerminalIdMap->size());
        return false;
    }
    return mPtNodeWriter->updateTerminalId(ptNodeParams, it->second);
}

} // namespace latinime
