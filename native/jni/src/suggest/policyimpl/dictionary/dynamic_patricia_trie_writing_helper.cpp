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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_helper.h"

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_gc_event_listeners.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"
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
        const int matchedCodePointCount = readingHelper->getPrevTotalCodePointCount();
        if (!readingHelper->isMatchedCodePoint(0 /* index */,
                wordCodePoints[matchedCodePointCount])) {
            // The first code point is different from target code point. Skip this node and read
            // the next sibling node.
            readingHelper->readNextSiblingNode();
            continue;
        }
        // Check following merged node code points.
        const DynamicPatriciaTrieNodeReader *const nodeReader = readingHelper->getNodeReader();
        const int nodeCodePointCount = nodeReader->getCodePointCount();
        for (int j = 1; j < nodeCodePointCount; ++j) {
            const int nextIndex = matchedCodePointCount + j;
            if (nextIndex >= codePointCount || !readingHelper->isMatchedCodePoint(j,
                    wordCodePoints[matchedCodePointCount + j])) {
                *outAddedNewUnigram = true;
                return reallocatePtNodeAndAddNewPtNodes(nodeReader,
                        readingHelper->getMergedNodeCodePoints(), j,
                        getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */,
                                probability),
                        wordCodePoints + matchedCodePointCount,
                        codePointCount - matchedCodePointCount);
            }
        }
        // All characters are matched.
        if (codePointCount == readingHelper->getTotalCodePointCount()) {
            return setPtNodeProbability(nodeReader, probability,
                    readingHelper->getMergedNodeCodePoints(), outAddedNewUnigram);
        }
        if (!nodeReader->hasChildren()) {
            *outAddedNewUnigram = true;
            return createChildrenPtNodeArrayAndAChildPtNode(nodeReader,
                    getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */, probability),
                    wordCodePoints + readingHelper->getTotalCodePointCount(),
                    codePointCount - readingHelper->getTotalCodePointCount());
        }
        // Advance to the children nodes.
        parentPos = nodeReader->getHeadPos();
        readingHelper->readChildNode();
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
    int mMergedNodeCodePoints[MAX_WORD_LENGTH];
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoInBufferFromPtNodePosAndGetNodeCodePoints(word0Pos, MAX_WORD_LENGTH,
            mMergedNodeCodePoints);
    // Move node to add bigram entry.
    const int newNodePos = mBuffer->getTailPosition();
    if (!markNodeAsMovedAndSetPosition(&nodeReader, newNodePos, newNodePos)) {
        return false;
    }
    int writingPos = newNodePos;
    // Write a new PtNode using original PtNode's info to the tail of the dictionary in mBuffer.
    if (!writePtNodeToBufferByCopyingPtNodeInfo(mBuffer, &nodeReader, nodeReader.getParentPos(),
            mMergedNodeCodePoints, nodeReader.getCodePointCount(), nodeReader.getProbability(),
            &writingPos)) {
        return false;
    }
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(newNodePos);
    if (nodeReader.getBigramsPos() != NOT_A_DICT_POS) {
        // Insert a new bigram entry into the existing bigram list.
        int bigramListPos = nodeReader.getBigramsPos();
        return mBigramPolicy->addNewBigramEntryToBigramList(word1Pos, probability, &bigramListPos,
                outAddedNewBigram);
    } else {
        // The PtNode doesn't have a bigram list.
        *outAddedNewBigram = true;
        // First, Write a bigram entry at the tail position of the PtNode.
        if (!mBigramPolicy->writeNewBigramEntry(word1Pos, probability, &writingPos)) {
            return false;
        }
        // Then, Mark as the PtNode having bigram list in the flags.
        const PatriciaTrieReadingUtils::NodeFlags updatedFlags =
                PatriciaTrieReadingUtils::createAndGetFlags(nodeReader.isBlacklisted(),
                        nodeReader.isNotAWord(), nodeReader.getProbability() != NOT_A_PROBABILITY,
                        nodeReader.getShortcutPos() != NOT_A_DICT_POS, true /* hasBigrams */,
                        nodeReader.getCodePointCount() > 1, CHILDREN_POSITION_FIELD_SIZE);
        writingPos = newNodePos;
        // Write updated flags into the moved PtNode's flags field.
        return DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
                &writingPos);
    }
}

// Remove a bigram relation from word0Pos to word1Pos.
bool DynamicPatriciaTrieWritingHelper::removeBigramWords(const int word0Pos, const int word1Pos) {
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(word0Pos);
    if (nodeReader.getBigramsPos() == NOT_A_DICT_POS) {
        return false;
    }
    return mBigramPolicy->removeBigram(nodeReader.getBigramsPos(), word1Pos);
}

void DynamicPatriciaTrieWritingHelper::writeToDictFile(const char *const fileName,
        const HeaderPolicy *const headerPolicy, const int unigramCount, const int bigramCount) {
    BufferWithExtendableBuffer headerBuffer(0 /* originalBuffer */, 0 /* originalBufferSize */);
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
    BufferWithExtendableBuffer newDictBuffer(0 /* originalBuffer */, 0 /* originalBufferSize */,
            MAX_DICTIONARY_SIZE);
    int unigramCount = 0;
    int bigramCount = 0;
    if (mNeedsToDecay) {
        ForgettingCurveUtils::sTimeKeeper.setCurrentTime();
    }
    if (!runGC(rootPtNodeArrayPos, headerPolicy, &newDictBuffer, &unigramCount, &bigramCount)) {
        return;
    }
    BufferWithExtendableBuffer headerBuffer(0 /* originalBuffer */, 0 /* originalBufferSize */);
    if (!headerPolicy->writeHeaderToBuffer(&headerBuffer, true /* updatesLastUpdatedTime */,
            mNeedsToDecay, unigramCount, bigramCount, 0 /* extendedRegionSize */)) {
        return;
    }
    DictFileWritingUtils::flushAllHeaderAndBodyToFile(fileName, &headerBuffer, &newDictBuffer);
}

bool DynamicPatriciaTrieWritingHelper::markNodeAsDeleted(
        const DynamicPatriciaTrieNodeReader *const nodeToUpdate) {
    int pos = nodeToUpdate->getHeadPos();
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(pos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        pos -= mBuffer->getOriginalBufferSize();
    }
    // Read original flags
    const PatriciaTrieReadingUtils::NodeFlags originalFlags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictBuf, &pos);
    const PatriciaTrieReadingUtils::NodeFlags updatedFlags =
            DynamicPatriciaTrieReadingUtils::updateAndGetFlags(originalFlags, false /* isMoved */,
                    true /* isDeleted */);
    int writingPos = nodeToUpdate->getHeadPos();
    // Update flags.
    return DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
            &writingPos);
}

bool DynamicPatriciaTrieWritingHelper::markNodeAsMovedAndSetPosition(
        const DynamicPatriciaTrieNodeReader *const originalNode, const int movedPos,
        const int bigramLinkedNodePos) {
    int pos = originalNode->getHeadPos();
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(pos);
    const uint8_t *const dictBuf = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        pos -= mBuffer->getOriginalBufferSize();
    }
    // Read original flags
    const PatriciaTrieReadingUtils::NodeFlags originalFlags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictBuf, &pos);
    const PatriciaTrieReadingUtils::NodeFlags updatedFlags =
            DynamicPatriciaTrieReadingUtils::updateAndGetFlags(originalFlags, true /* isMoved */,
                    false /* isDeleted */);
    int writingPos = originalNode->getHeadPos();
    // Update flags.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
            &writingPos)) {
        return false;
    }
    // Update moved position, which is stored in the parent offset field.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(
            mBuffer, movedPos, originalNode->getHeadPos(), &writingPos)) {
        return false;
    }
    // Update bigram linked node position, which is stored in the children position field.
    int childrenPosFieldPos = originalNode->getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(
            mBuffer, bigramLinkedNodePos, &childrenPosFieldPos)) {
        return false;
    }
    if (originalNode->hasChildren()) {
        // Update children's parent position.
        DynamicPatriciaTrieReadingHelper readingHelper(mBuffer, mBigramPolicy, mShortcutPolicy);
        const DynamicPatriciaTrieNodeReader *const nodeReader = readingHelper.getNodeReader();
        readingHelper.initWithPtNodeArrayPos(originalNode->getChildrenPos());
        while (!readingHelper.isEnd()) {
            int parentOffsetFieldPos = nodeReader->getHeadPos()
                    + DynamicPatriciaTrieWritingUtils::NODE_FLAG_FIELD_SIZE;
            if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(
                    mBuffer, bigramLinkedNodePos, nodeReader->getHeadPos(),
                    &parentOffsetFieldPos)) {
                // Parent offset cannot be written because of a bug or a broken dictionary; thus,
                // we give up to update dictionary.
                return false;
            }
            readingHelper.readNextSiblingNode();
        }
    }
    return true;
}

// Write new PtNode at writingPos.
bool DynamicPatriciaTrieWritingHelper::writePtNodeWithFullInfoToBuffer(
        BufferWithExtendableBuffer *const bufferToWrite, const bool isBlacklisted,
        const bool isNotAWord, const int parentPos, const int *const codePoints,
        const int codePointCount, const int probability, const int childrenPos,
        const int originalBigramListPos, const int originalShortcutListPos,
        int *const writingPos) {
    const int nodePos = *writingPos;
    // Write dummy flags. The Node flags are updated with appropriate flags at the last step of the
    // PtNode writing.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(bufferToWrite,
            0 /* nodeFlags */, writingPos)) {
        return false;
    }
    // Calculate a parent offset and write the offset.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(bufferToWrite,
            parentPos, nodePos, writingPos)) {
        return false;
    }
    // Write code points
    if (!DynamicPatriciaTrieWritingUtils::writeCodePointsAndAdvancePosition(bufferToWrite,
            codePoints, codePointCount, writingPos)) {
        return false;
    }
    // Write probability when the probability is a valid probability, which means this node is
    // terminal.
    if (probability != NOT_A_PROBABILITY) {
        if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(bufferToWrite,
                probability, writingPos)) {
            return false;
        }
    }
    // Write children position
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(bufferToWrite,
            childrenPos, writingPos)) {
        return false;
    }
    // Copy shortcut list when the originalShortcutListPos is valid dictionary position.
    if (originalShortcutListPos != NOT_A_DICT_POS) {
        int fromPos = originalShortcutListPos;
        if (!mShortcutPolicy->copyAllShortcutsAndReturnIfSucceededOrNot(bufferToWrite, &fromPos,
                writingPos)) {
            return false;
        }
    }
    // Copy bigram list when the originalBigramListPos is valid dictionary position.
    int bigramCount = 0;
    if (originalBigramListPos != NOT_A_DICT_POS) {
        int fromPos = originalBigramListPos;
        if (!mBigramPolicy->copyAllBigrams(bufferToWrite, &fromPos, writingPos, &bigramCount)) {
            return false;
        }
    }
    // Create node flags and write them.
    PatriciaTrieReadingUtils::NodeFlags nodeFlags =
            PatriciaTrieReadingUtils::createAndGetFlags(isBlacklisted, isNotAWord,
                    probability != NOT_A_PROBABILITY /* isTerminal */,
                    originalShortcutListPos != NOT_A_DICT_POS /* hasShortcutTargets */,
                    bigramCount > 0 /* hasBigrams */, codePointCount > 1 /* hasMultipleChars */,
                    CHILDREN_POSITION_FIELD_SIZE);
    int flagsFieldPos = nodePos;
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(bufferToWrite, nodeFlags,
            &flagsFieldPos)) {
        return false;
    }
    return true;
}

bool DynamicPatriciaTrieWritingHelper::writePtNodeToBuffer(
        BufferWithExtendableBuffer *const bufferToWrite, const int parentPos,
        const int *const codePoints, const int codePointCount, const int probability,
        int *const writingPos) {
    return writePtNodeWithFullInfoToBuffer(bufferToWrite, false /* isBlacklisted */,
            false /* isNotAWord */, parentPos, codePoints, codePointCount, probability,
            NOT_A_DICT_POS /* childrenPos */, NOT_A_DICT_POS /* originalBigramsPos */,
            NOT_A_DICT_POS /* originalShortcutPos */, writingPos);
}

bool DynamicPatriciaTrieWritingHelper::writePtNodeToBufferByCopyingPtNodeInfo(
        BufferWithExtendableBuffer *const bufferToWrite,
        const DynamicPatriciaTrieNodeReader *const originalNode, const int parentPos,
        const int *const codePoints, const int codePointCount, const int probability,
        int *const writingPos) {
    return writePtNodeWithFullInfoToBuffer(bufferToWrite, originalNode->isBlacklisted(),
            originalNode->isNotAWord(), parentPos, codePoints, codePointCount, probability,
            originalNode->getChildrenPos(), originalNode->getBigramsPos(),
            originalNode->getShortcutPos(), writingPos);
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
        const DynamicPatriciaTrieNodeReader *const originalPtNode, const int probability,
        const int *const codePoints, bool *const outAddedNewUnigram) {
    if (originalPtNode->isTerminal()) {
        // Overwrites the probability.
        *outAddedNewUnigram = false;
        const int probabilityToWrite = getUpdatedProbability(originalPtNode->getProbability(),
                probability);
        int probabilityFieldPos = originalPtNode->getProbabilityFieldPos();
        if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
                probabilityToWrite, &probabilityFieldPos)) {
            return false;
        }
    } else {
        // Make the node terminal and write the probability.
        *outAddedNewUnigram = true;
        int movedPos = mBuffer->getTailPosition();
        if (!markNodeAsMovedAndSetPosition(originalPtNode, movedPos, movedPos)) {
            return false;
        }
        if (!writePtNodeToBufferByCopyingPtNodeInfo(mBuffer, originalPtNode,
                originalPtNode->getParentPos(), codePoints, originalPtNode->getCodePointCount(),
                getUpdatedProbability(NOT_A_PROBABILITY /* originalProbability */, probability),
                &movedPos)) {
            return false;
        }
    }
    return true;
}

bool DynamicPatriciaTrieWritingHelper::createChildrenPtNodeArrayAndAChildPtNode(
        const DynamicPatriciaTrieNodeReader *const parentNode, const int probability,
        const int *const codePoints, const int codePointCount) {
    const int newPtNodeArrayPos = mBuffer->getTailPosition();
    int childrenPosFieldPos = parentNode->getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBuffer,
            newPtNodeArrayPos, &childrenPosFieldPos)) {
        return false;
    }
    return createNewPtNodeArrayWithAChildPtNode(parentNode->getHeadPos(), codePoints,
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
    if (!writePtNodeToBuffer(mBuffer, parentPtNodePos, nodeCodePoints, nodeCodePointCount,
            probability, &writingPos)) {
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
        const DynamicPatriciaTrieNodeReader *const reallocatingPtNode,
        const int *const reallocatingPtNodeCodePoints, const int overlappingCodePointCount,
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
    if (!writePtNodeToBuffer(mBuffer, reallocatingPtNode->getParentPos(),
            reallocatingPtNodeCodePoints, overlappingCodePointCount, newProbability,
            &writingPos)) {
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
    if (!writePtNodeToBufferByCopyingPtNodeInfo(mBuffer, reallocatingPtNode,
            firstPartOfReallocatedPtNodePos,
            reallocatingPtNodeCodePoints + overlappingCodePointCount,
            reallocatingPtNode->getCodePointCount() - overlappingCodePointCount,
            reallocatingPtNode->getProbability(), &writingPos)) {
        return false;
    }
    if (addsExtraChild) {
        if (!writePtNodeToBuffer(mBuffer, firstPartOfReallocatedPtNodePos,
                newNodeCodePoints + overlappingCodePointCount,
                newNodeCodePointCount - overlappingCodePointCount, probabilityOfNewPtNode,
                &writingPos)) {
            return false;
        }
    }
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(mBuffer,
            NOT_A_DICT_POS /* forwardLinkPos */, &writingPos)) {
        return false;
    }
    // Update original reallocatingPtNode as moved.
    if (!markNodeAsMovedAndSetPosition(reallocatingPtNode, firstPartOfReallocatedPtNodePos,
            secondPartOfReallocatedPtNodePos)) {
        return false;
    }
    // Load node info. Information of the 1st part will be fetched.
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(firstPartOfReallocatedPtNodePos);
    // Update children position.
    int childrenPosFieldPos = nodeReader.getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBuffer,
            actualChildrenPos, &childrenPosFieldPos)) {
        return false;
    }
    return true;
}

bool DynamicPatriciaTrieWritingHelper::runGC(const int rootPtNodeArrayPos,
        const HeaderPolicy *const headerPolicy, BufferWithExtendableBuffer *const bufferToWrite,
        int *const outUnigramCount, int *const outBigramCount) {
    DynamicPatriciaTrieReadingHelper readingHelper(mBuffer, mBigramPolicy, mShortcutPolicy);
    readingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners
            ::TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted
                    traversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted(
                            headerPolicy, this, mBuffer, mNeedsToDecay);
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
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToPlaceAndWriteValidPtNodesToBuffer
            traversePolicyToPlaceAndWriteValidPtNodesToBuffer(this, bufferToWrite,
                    &dictPositionRelocationMap);
    if (!readingHelper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(
            &traversePolicyToPlaceAndWriteValidPtNodesToBuffer)) {
        return false;
    }

    // Create policy instance for the GCed dictionary.
    DynamicShortcutListPolicy newDictShortcutPolicy(bufferToWrite);
    DynamicBigramListPolicy newDictBigramPolicy(headerPolicy, bufferToWrite, &newDictShortcutPolicy,
            mNeedsToDecay);
    // Create reading helper for the GCed dictionary.
    DynamicPatriciaTrieReadingHelper newDictReadingHelper(bufferToWrite, &newDictBigramPolicy,
            &newDictShortcutPolicy);
    newDictReadingHelper.initWithPtNodeArrayPos(rootPtNodeArrayPos);
    DynamicPatriciaTrieGcEventListeners::TraversePolicyToUpdateAllPositionFields
            traversePolicyToUpdateAllPositionFields(this, &newDictBigramPolicy, bufferToWrite,
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
        const int newProbability) {
    if (mNeedsToDecay) {
        return ForgettingCurveUtils::getUpdatedEncodedProbability(originalProbability,
                newProbability);
    } else {
        return newProbability;
    }
}

} // namespace latinime
