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

#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_updating_helper.h"

#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_writer.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

const int DynamicPatriciaTrieUpdatingHelper::CHILDREN_POSITION_FIELD_SIZE = 3;

bool DynamicPatriciaTrieUpdatingHelper::addUnigramWord(
        DynamicPatriciaTrieReadingHelper *const readingHelper,
        const int *const wordCodePoints, const int codePointCount, const int probability,
        const bool isNotAWord, const bool isBlacklisted, const int timestamp,
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
                return reallocatePtNodeAndAddNewPtNodes(&ptNodeParams, j, isNotAWord, isBlacklisted,
                        probability, timestamp, wordCodePoints + matchedCodePointCount,
                        codePointCount - matchedCodePointCount);
            }
        }
        // All characters are matched.
        if (codePointCount == readingHelper->getTotalCodePointCount(ptNodeParams)) {
            return setPtNodeProbability(&ptNodeParams, isNotAWord, isBlacklisted, probability,
                    timestamp, outAddedNewUnigram);
        }
        if (!ptNodeParams.hasChildren()) {
            *outAddedNewUnigram = true;
            return createChildrenPtNodeArrayAndAChildPtNode(&ptNodeParams,
                    isNotAWord, isBlacklisted, probability, timestamp,
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
            isNotAWord, isBlacklisted, probability, timestamp, &pos);
}

bool DynamicPatriciaTrieUpdatingHelper::addBigramWords(const int word0Pos, const int word1Pos,
        const int probability, const int timestamp, bool *const outAddedNewBigram) {
    const PtNodeParams sourcePtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word0Pos));
    const PtNodeParams targetPtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word1Pos));
    return mPtNodeWriter->addNewBigramEntry(&sourcePtNodeParams, &targetPtNodeParams, probability,
            timestamp, outAddedNewBigram);
}

// Remove a bigram relation from word0Pos to word1Pos.
bool DynamicPatriciaTrieUpdatingHelper::removeBigramWords(const int word0Pos, const int word1Pos) {
    const PtNodeParams sourcePtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word0Pos));
    const PtNodeParams targetPtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(word1Pos));
    return mPtNodeWriter->removeBigramEntry(&sourcePtNodeParams, &targetPtNodeParams);
}

bool DynamicPatriciaTrieUpdatingHelper::createAndInsertNodeIntoPtNodeArray(const int parentPos,
        const int *const nodeCodePoints, const int nodeCodePointCount,
        const bool isNotAWord, const bool isBlacklisted, const int probability,
        const int timestamp,  int *const forwardLinkFieldPos) {
    const int newPtNodeArrayPos = mBuffer->getTailPosition();
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(mBuffer,
            newPtNodeArrayPos, forwardLinkFieldPos)) {
        return false;
    }
    return createNewPtNodeArrayWithAChildPtNode(parentPos, nodeCodePoints, nodeCodePointCount,
            isNotAWord, isBlacklisted, probability, timestamp);
}

bool DynamicPatriciaTrieUpdatingHelper::setPtNodeProbability(
        const PtNodeParams *const originalPtNodeParams, const bool isNotAWord,
        const bool isBlacklisted, const int probability, const int timestamp,
        bool *const outAddedNewUnigram) {
    if (originalPtNodeParams->isTerminal()) {
        // Overwrites the probability.
        *outAddedNewUnigram = false;
        return mPtNodeWriter->updatePtNodeProbability(originalPtNodeParams, probability, timestamp);
    } else {
        // Make the node terminal and write the probability.
        *outAddedNewUnigram = true;
        const int movedPos = mBuffer->getTailPosition();
        int writingPos = movedPos;
        const PtNodeParams ptNodeParamsToWrite(getUpdatedPtNodeParams(originalPtNodeParams,
                isNotAWord, isBlacklisted, true /* isTerminal */,
                originalPtNodeParams->getParentPos(), originalPtNodeParams->getCodePointCount(),
                originalPtNodeParams->getCodePoints(), probability));
        if (!mPtNodeWriter->writeNewTerminalPtNodeAndAdvancePosition(&ptNodeParamsToWrite,
                timestamp, &writingPos)) {
            return false;
        }
        if (!mPtNodeWriter->markPtNodeAsMoved(originalPtNodeParams, movedPos, movedPos)) {
            return false;
        }
    }
    return true;
}

bool DynamicPatriciaTrieUpdatingHelper::createChildrenPtNodeArrayAndAChildPtNode(
        const PtNodeParams *const parentPtNodeParams, const bool isNotAWord,
        const bool isBlacklisted, const int probability, const int timestamp,
        const int *const codePoints, const int codePointCount) {
    const int newPtNodeArrayPos = mBuffer->getTailPosition();
    if (!mPtNodeWriter->updateChildrenPosition(parentPtNodeParams, newPtNodeArrayPos)) {
        return false;
    }
    return createNewPtNodeArrayWithAChildPtNode(parentPtNodeParams->getHeadPos(), codePoints,
            codePointCount, isNotAWord, isBlacklisted, probability, timestamp);
}

bool DynamicPatriciaTrieUpdatingHelper::createNewPtNodeArrayWithAChildPtNode(
        const int parentPtNodePos, const int *const nodeCodePoints, const int nodeCodePointCount,
        const bool isNotAWord, const bool isBlacklisted, const int probability,
        const int timestamp) {
    int writingPos = mBuffer->getTailPosition();
    if (!DynamicPatriciaTrieWritingUtils::writePtNodeArraySizeAndAdvancePosition(mBuffer,
            1 /* arraySize */, &writingPos)) {
        return false;
    }
    const PtNodeParams ptNodeParamsToWrite(getPtNodeParamsForNewPtNode(
            isNotAWord, isBlacklisted, true /* isTerminal */,
            parentPtNodePos, nodeCodePointCount, nodeCodePoints, probability));
    if (!mPtNodeWriter->writeNewTerminalPtNodeAndAdvancePosition(&ptNodeParamsToWrite, timestamp,
            &writingPos)) {
        return false;
    }
    if (!DynamicPatriciaTrieWritingUtils::writeForwardLinkPositionAndAdvancePosition(mBuffer,
            NOT_A_DICT_POS /* forwardLinkPos */, &writingPos)) {
        return false;
    }
    return true;
}

// Returns whether the dictionary updating was succeeded or not.
bool DynamicPatriciaTrieUpdatingHelper::reallocatePtNodeAndAddNewPtNodes(
        const PtNodeParams *const reallocatingPtNodeParams, const int overlappingCodePointCount,
        const bool isNotAWord, const bool isBlacklisted, const int probabilityOfNewPtNode,
        const int timestamp, const int *const newNodeCodePoints, const int newNodeCodePointCount) {
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
    if (addsExtraChild) {
        const PtNodeParams ptNodeParamsToWrite(getPtNodeParamsForNewPtNode(
                false /* isNotAWord */, false /* isBlacklisted */, false /* isTerminal */,
                reallocatingPtNodeParams->getParentPos(), overlappingCodePointCount,
                reallocatingPtNodeParams->getCodePoints(), NOT_A_PROBABILITY));
        if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&ptNodeParamsToWrite, &writingPos)) {
            return false;
        }
    } else {
        const PtNodeParams ptNodeParamsToWrite(getPtNodeParamsForNewPtNode(
                isNotAWord, isBlacklisted, true /* isTerminal */,
                reallocatingPtNodeParams->getParentPos(), overlappingCodePointCount,
                reallocatingPtNodeParams->getCodePoints(), probabilityOfNewPtNode));
        if (!mPtNodeWriter->writeNewTerminalPtNodeAndAdvancePosition(&ptNodeParamsToWrite,
                timestamp, &writingPos)) {
            return false;
        }
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
            reallocatingPtNodeParams->isNotAWord(), reallocatingPtNodeParams->isBlacklisted(),
            reallocatingPtNodeParams->isTerminal(), firstPartOfReallocatedPtNodePos,
            reallocatingPtNodeParams->getCodePointCount() - overlappingCodePointCount,
            reallocatingPtNodeParams->getCodePoints() + overlappingCodePointCount,
            reallocatingPtNodeParams->getProbability()));
    if (!mPtNodeWriter->writePtNodeAndAdvancePosition(&childPartPtNodeParams, &writingPos)) {
        return false;
    }
    if (addsExtraChild) {
        const PtNodeParams extraChildPtNodeParams(getPtNodeParamsForNewPtNode(
                isNotAWord, isBlacklisted, true /* isTerminal */,
                firstPartOfReallocatedPtNodePos, newNodeCodePointCount - overlappingCodePointCount,
                newNodeCodePoints + overlappingCodePointCount, probabilityOfNewPtNode));
        if (!mPtNodeWriter->writeNewTerminalPtNodeAndAdvancePosition(&extraChildPtNodeParams,
                timestamp, &writingPos)) {
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

const PtNodeParams DynamicPatriciaTrieUpdatingHelper::getUpdatedPtNodeParams(
        const PtNodeParams *const originalPtNodeParams, const bool isNotAWord,
        const bool isBlacklisted, const bool isTerminal, const int parentPos,
        const int codePointCount, const int *const codePoints, const int probability) const {
    const PatriciaTrieReadingUtils::NodeFlags flags = PatriciaTrieReadingUtils::createAndGetFlags(
            isBlacklisted, isNotAWord, isTerminal, originalPtNodeParams->hasShortcutTargets(),
            originalPtNodeParams->hasBigrams(), codePointCount > 1 /* hasMultipleChars */,
            CHILDREN_POSITION_FIELD_SIZE);
    return PtNodeParams(originalPtNodeParams, flags, parentPos, codePointCount, codePoints,
            probability);
}

const PtNodeParams DynamicPatriciaTrieUpdatingHelper::getPtNodeParamsForNewPtNode(
        const bool isNotAWord, const bool isBlacklisted, const bool isTerminal,
        const int parentPos, const int codePointCount, const int *const codePoints,
        const int probability) const {
    const PatriciaTrieReadingUtils::NodeFlags flags = PatriciaTrieReadingUtils::createAndGetFlags(
            isBlacklisted, isNotAWord, isTerminal, false /* hasShortcutTargets */,
            false /* hasBigrams */, codePointCount > 1 /* hasMultipleChars */,
            CHILDREN_POSITION_FIELD_SIZE);
    return PtNodeParams(flags, parentPos, codePointCount, codePoints, probability);
}

} // namespace latinime
