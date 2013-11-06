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

const int DynamicPatriciaTrieWritingHelper::CHILDREN_POSITION_FIELD_SIZE = 3;
// TODO: Make MAX_DICTIONARY_SIZE 8MB.
const size_t DynamicPatriciaTrieWritingHelper::MAX_DICTIONARY_SIZE = 2 * 1024 * 1024;

bool DynamicPatriciaTrieWritingHelper::addUnigramWord(
        DynamicPatriciaTrieReadingHelper *const readingHelper,
        const int *const wordCodePoints, const int codePointCount, const int probability,
        bool *const outAddedNewUnigram) {
    int parentPos = NOT_A_DICT_POS;
    while (!readingHelper->isEnd()) {
        const PtNodeParams ptNodeParams(readingHelper->getPtNodeParams());
        if (!ptNodeParams.isValid()) {
            break;
        }
        const int matchedCodePointCount = readingHelper->getPrevTotalCodePointCount();
        if (!readingHelper->isMatchedCodePoint(ptNodeParams, 0 /* index */,
                wordCodePoints[matchedCodePointCount])) {
            // The first code point is different from target code point. Skip this node and read
            // the next sibling node.
            readingHelper->readNextSiblingNode(ptNodeParams);
            continue;
        }
        // Check following merged node code points.
        const int nodeCodePointCount = ptNodeParams.getCodePointCount();
        for (int j = 1; j < nodeCodePointCount; ++j) {
            const int nextIndex = matchedCodePointCount + j;
            if (nextIndex >= codePointCount || !readingHelper->isMatchedCodePoint(ptNodeParams, j,
                    wordCodePoints[matchedCodePointCount + j])) {
                *outAddedNewUnigram = true;
                return reallocatePtNodeAndAddNewPtNodes(&ptNodeParams, j,
                        getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */,
                                probability),
                        wordCodePoints + matchedCodePointCount,
                        codePointCount - matchedCodePointCount);
            }
        }
        // All characters are matched.
        if (codePointCount == readingHelper->getTotalCodePointCount(ptNodeParams)) {
            return setPtNodeProbability(&ptNodeParams, probability, outAddedNewUnigram);
        }
        if (!ptNodeParams.hasChildren()) {
            *outAddedNewUnigram = true;
            return createChildrenPtNodeArrayAndAChildPtNode(&ptNodeParams,
                    getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */, probability),
                    wordCodePoints + readingHelper->getTotalCodePointCount(ptNodeParams),
                    codePointCount - readingHelper->getTotalCodePointCount(ptNodeParams));
        }
        // Advance to the children nodes.
        parentPos = ptNodeParams.getHeadPos();
        readingHelper->readChildNode(ptNodeParams);
    }
    if (readingHelper->isError()) {
        // The dictionary is invalid.
        return false;
    }
    int pos = readingHelper->getPosOfLastForwardLinkField();
    *outAddedNewUnigram = true;
    return createAndInsertNodeIntoPtNodeArray(parentPos,
            wordCodePoints + readingHelper->getPrevTotalCodePointCount(),
            codePointCount - readingHelper->getPrevTotalCodePointCount(),
            getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */, probability), &pos);
}

bool DynamicPatriciaTrieWritingHelper::addBigramWords(const int word0Pos, const int word1Pos,
        const int probability, bool *const outAddedNewBigram) {
    const PtNodeParams sourcePtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word0Pos));
    const PtNodeParams targetPtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word1Pos));
    return mPtNodeWriter->addNewBigramEntry(&sourcePtNodeParams, &targetPtNodeParams, probability,
            outAddedNewBigram);
}

// Remove a bigram relation from word0Pos to word1Pos.
bool DynamicPatriciaTrieWritingHelper::removeBigramWords(const int word0Pos, const int word1Pos) {
    const PtNodeParams ptNodeParams(mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word0Pos));
    if (ptNodeParams.getBigramsPos() == NOT_A_DICT_POS) {
        return false;
    }
    return mBigramPolicy->removeBigram(ptNodeParams.getBigramsPos(), word1Pos);
}

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

bool DynamicPatriciaTrieWritingHelper::createAndInsertNodeIntoPtNodeArray(const int parentPos,
        const int *const nodeCodePoints, const int nodeCodePointCount, const int probability,
        int *const forwardLinkFieldPos) {
    const int newPtNodeArrayPos = mBuffer->getTailPosition();
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(mBuffer,
            newPtNodeArrayPos, forwardLinkFieldPos)) {
        return false;
    }
    return createNewPtNodeArrayWithAChildPtNode(parentPos, nodeCodePoints, nodeCodePointCount,
            probability);
}

bool DynamicPatriciaTrieWritingHelper::setPtNodeProbability(
        const PtNodeParams *const originalPtNodeParams, const int probability,
        bool *const outAddedNewUnigram) {
    if (originalPtNodeParams->isTerminal()) {
        // Overwrites the probability.
        *outAddedNewUnigram = false;
        const int probabilityToWrite = getUpdatedProbability(
                originalPtNodeParams->getProbability(), probability);
        return mPtNodeWriter->updatePtNodeProbability(originalPtNodeParams, probabilityToWrite);
    } else {
        // Make the node terminal and write the probability.
        *outAddedNewUnigram = true;
        const int movedPos = mBuffer->getTailPosition();
        int writingPos = movedPos;
        const PtNodeParams ptNodeParamsToWrite(getUpdatedPtNodeParams(originalPtNodeParams,
                originalPtNodeParams->getParentPos(), originalPtNodeParams->getCodePointCount(),
                originalPtNodeParams->getCodePoints(),
                getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */, probability)));
        if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&ptNodeParamsToWrite, &writingPos)) {
            return false;
        }
        if (!mPtNodeWriter->markPtNodeAsMoved(originalPtNodeParams, movedPos, movedPos)) {
            return false;
        }
    }
    return true;
}

bool DynamicPatriciaTrieWritingHelper::createChildrenPtNodeArrayAndAChildPtNode(
        const PtNodeParams *const parentPtNodeParams, const int probability,
        const int *const codePoints, const int codePointCount) {
    const int newPtNodeArrayPos = mBuffer->getTailPosition();
    if (!mPtNodeWriter->updateChildrenPosition(parentPtNodeParams, newPtNodeArrayPos)) {
        return false;
    }
    return createNewPtNodeArrayWithAChildPtNode(parentPtNodeParams->getHeadPos(), codePoints,
            codePointCount, probability);
}

bool DynamicPatriciaTrieWritingHelper::createNewPtNodeArrayWithAChildPtNode(
        const int parentPtNodePos, const int *const nodeCodePoints, const int nodeCodePointCount,
        const int probability) {
    int writingPos = mBuffer->getTailPosition();
    if (!DynamicPatriciaTrieWritingUtils::writePtNodeArraySizeAndAdvancePosition(mBuffer,
            1 /* arraySize */, &writingPos)) {
        return false;
    }
    const PtNodeParams ptNodeParamsToWrite(getPtNodeParamsForNewPtNode(
            parentPtNodePos, nodeCodePointCount, nodeCodePoints, probability));
    if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&ptNodeParamsToWrite, &writingPos)) {
        return false;
    }
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(mBuffer,
            NOT_A_DICT_POS /* forwardLinkPos */, &writingPos)) {
        return false;
    }
    return true;
}

// Returns whether the dictionary updating was succeeded or not.
bool DynamicPatriciaTrieWritingHelper::reallocatePtNodeAndAddNewPtNodes(
        const PtNodeParams *const reallocatingPtNodeParams, const int overlappingCodePointCount,
        const int probabilityOfNewPtNode, const int *const newNodeCodePoints,
        const int newNodeCodePointCount) {
    // When addsExtraChild is true, split the reallocating PtNode and add new child.
    // Reallocating PtNode: abcde, newNode: abcxy.
    // abc (1st, not terminal) __ de (2nd)
    //                         \_ xy (extra child, terminal)
    // Otherwise, this method makes 1st part terminal and write probabilityOfNewPtNode.
    // Reallocating PtNode: abcde, newNode: abc.
    // abc (1st, terminal) __ de (2nd)
    const bool addsExtraChild = newNodeCodePointCount > overlappingCodePointCount;
    const int firstPartOfReallocatedPtNodePos = mBuffer->getTailPosition();
    int writingPos = firstPartOfReallocatedPtNodePos;
    // Write the 1st part of the reallocating node. The children position will be updated later
    // with actual children position.
    const int newProbability = addsExtraChild ? NOT_A_PROBABILITY : probabilityOfNewPtNode;
    const PtNodeParams ptNodeParamsToWrite(getPtNodeParamsForNewPtNode(
            reallocatingPtNodeParams->getParentPos(), overlappingCodePointCount,
            reallocatingPtNodeParams->getCodePoints(), newProbability));
    if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&ptNodeParamsToWrite, &writingPos)) {
        return false;
    }
    const int actualChildrenPos = writingPos;
    // Create new children PtNode array.
    const size_t newPtNodeCount = addsExtraChild ? 2 : 1;
    if (!DynamicPatriciaTrieWritingUtils::writePtNodeArraySizeAndAdvancePosition(mBuffer,
            newPtNodeCount, &writingPos)) {
        return false;
    }
    // Write the 2nd part of the reallocating node.
    const int secondPartOfReallocatedPtNodePos = writingPos;
    const PtNodeParams childPartPtNodeParams(getUpdatedPtNodeParams(reallocatingPtNodeParams,
            firstPartOfReallocatedPtNodePos,
            reallocatingPtNodeParams->getCodePointCount() - overlappingCodePointCount,
            reallocatingPtNodeParams->getCodePoints() + overlappingCodePointCount,
            reallocatingPtNodeParams->getProbability()));
    if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&childPartPtNodeParams, &writingPos)) {
        return false;
    }
    if (addsExtraChild) {
        const PtNodeParams extraChildPtNodeParams(getPtNodeParamsForNewPtNode(
                firstPartOfReallocatedPtNodePos, newNodeCodePointCount - overlappingCodePointCount,
                newNodeCodePoints + overlappingCodePointCount, probabilityOfNewPtNode));
        if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&extraChildPtNodeParams, &writingPos)) {
            return false;
        }
    }
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(mBuffer,
            NOT_A_DICT_POS /* forwardLinkPos */, &writingPos)) {
        return false;
    }
    // Update original reallocating PtNode as moved.
    if (!mPtNodeWriter->markPtNodeAsMoved(reallocatingPtNodeParams, firstPartOfReallocatedPtNodePos,
            secondPartOfReallocatedPtNodePos)) {
        return false;
    }
    // Load node info. Information of the 1st part will be fetched.
    const PtNodeParams ptNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(firstPartOfReallocatedPtNodePos));
    // Update children position.
    return mPtNodeWriter->updateChildrenPosition(&ptNodeParams, actualChildrenPos);
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
            traversePolicyToUpdateBigramProbability(mBigramPolicy);
    if (!readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(
            &traversePolicyToUpdateBigramProbability)) {
        return false;
    }
    if (mNeedsToDecay && traversePolicyToUpdateBigramProbability.getValidBigramEntryCount()
            > ForgettingCurveUtils::MAX_BIGRAM_COUNT_AFTER_GC) {
        // TODO: Remove more bigrams.
    }

    // Mapping from positions in mBuffer to positions in bufferToWrite.
    DictPositionRelocationMap dictPositionRelocationMap;
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieNodeWriter newPtNodeWriter(bufferToWrite,
            &ptNodeReader, mBigramPolicy, mShortcutPolicy);
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
    newDictReadingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateAllPositionFields
            traversePolicyToUpdateAllPositionFields(&newDictBigramPolicy, bufferToWrite,
                    &dictPositionRelocationMap);
    if (!newDictReadingHelper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            &traversePolicyToUpdateAllPositionFields)) {
        return false;
    }
    *outUnigramCount = traversePolicyToUpdateAllPositionFields.getUnigramCount();
    *outBigramCount = traversePolicyToUpdateAllPositionFields.getBigramCount();
    return true;
}

int DynamicPatriciaTrieWritingHelper::getUpdatedProbability(const int originalProbability,
        const int newProbability) const {
    if (mNeedsToDecay) {
        return ForgettingCurveUtils::getUpdatedEncodedProbability(originalProbability,
                newProbability);
    } else {
        return newProbability;
    }
}

const PtNodeParams DynamicPatriciaTrieWritingHelper::getUpdatedPtNodeParams(
        const PtNodeParams *const originalPtNodeParams, const int parentPos,
        const int codePointCount, const int *const codePoints, const int probability) const {
    const PatriciaTrieReadingUtils::NodeFlags flags = PatriciaTrieReadingUtils::createAndGetFlags(
            originalPtNodeParams->isBlacklisted(), originalPtNodeParams->isNotAWord(),
            probability != NOT_A_PROBABILITY /* isTerminal */,
            originalPtNodeParams->getShortcutPos() != NOT_A_DICT_POS /* hasShortcutTargets */,
            originalPtNodeParams->getBigramsPos() != NOT_A_DICT_POS /* hasBigrams */,
            codePointCount > 1 /* hasMultipleChars */, CHILDREN_POSITION_FIELD_SIZE);
    return PtNodeParams(originalPtNodeParams, flags, parentPos, codePointCount, codePoints,
            probability);
}

const PtNodeParams DynamicPatriciaTrieWritingHelper::getPtNodeParamsForNewPtNode(
        const int parentPos, const int codePointCount, const int *const codePoints,
        const int probability) const {
    const PatriciaTrieReadingUtils::NodeFlags flags = PatriciaTrieReadingUtils::createAndGetFlags(
            false /* isBlacklisted */, false /* isNotAWord */,
            probability != NOT_A_PROBABILITY /* isTerminal */,
            false /* hasShortcutTargets */, false /* hasBigrams */,
            codePointCount > 1 /* hasMultipleChars */, CHILDREN_POSITION_FIELD_SIZE);
    return PtNodeParams(flags, parentPos, codePointCount, codePoints, probability);
}

} // namespace latinime
