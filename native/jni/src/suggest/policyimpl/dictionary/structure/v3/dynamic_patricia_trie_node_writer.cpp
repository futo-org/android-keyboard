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

#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_writer.h"

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/dynamic_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

const int DynamicPatriciaTrieNodeWriter::CHILDREN_POSITION_FIELD_SIZE = 3;

bool DynamicPatriciaTrieNodeWriter::markPtNodeAsDeleted(
        const PtNodeParams *const toBeUpdatedPtNodeParams) {
    int pos = toBeUpdatedPtNodeParams->getHeadPos();
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
    int writingPos = toBeUpdatedPtNodeParams->getHeadPos();
    // Update flags.
    return DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
            &writingPos);
}

bool DynamicPatriciaTrieNodeWriter::markPtNodeAsMoved(
        const PtNodeParams *const toBeUpdatedPtNodeParams,
        const int movedPos, const int bigramLinkedNodePos) {
    int pos = toBeUpdatedPtNodeParams->getHeadPos();
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
    int writingPos = toBeUpdatedPtNodeParams->getHeadPos();
    // Update flags.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
            &writingPos)) {
        return false;
    }
    // Update moved position, which is stored in the parent offset field.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(
            mBuffer, movedPos, toBeUpdatedPtNodeParams->getHeadPos(), &writingPos)) {
        return false;
    }
    // Update bigram linked node position, which is stored in the children position field.
    int childrenPosFieldPos = toBeUpdatedPtNodeParams->getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(
            mBuffer, bigramLinkedNodePos, &childrenPosFieldPos)) {
        return false;
    }
    if (toBeUpdatedPtNodeParams->hasChildren()) {
        // Update children's parent position.
        mReadingHelper.initWithPtNodeArrayPos(toBeUpdatedPtNodeParams->getChildrenPos());
        while (!mReadingHelper.isEnd()) {
            const PtNodeParams childPtNodeParams(mReadingHelper.getPtNodeParams());
            int parentOffsetFieldPos = childPtNodeParams.getHeadPos()
                    + DynamicPatriciaTrieWritingUtils::NODE_FLAG_FIELD_SIZE;
            if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(
                    mBuffer, bigramLinkedNodePos, childPtNodeParams.getHeadPos(),
                    &parentOffsetFieldPos)) {
                // Parent offset cannot be written because of a bug or a broken dictionary; thus,
                // we give up to update dictionary.
                return false;
            }
            mReadingHelper.readNextSiblingNode(childPtNodeParams);
        }
    }
    return true;
}

bool DynamicPatriciaTrieNodeWriter::updatePtNodeProbability(
        const PtNodeParams *const toBeUpdatedPtNodeParams, const int newProbability) {
    if (!toBeUpdatedPtNodeParams->isTerminal()) {
        return false;
    }
    const int probabilityToWrite = getUpdatedProbability(toBeUpdatedPtNodeParams->getProbability(),
            newProbability);
    int probabilityFieldPos = toBeUpdatedPtNodeParams->getProbabilityFieldPos();
    return DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
            probabilityToWrite, &probabilityFieldPos);
}

bool DynamicPatriciaTrieNodeWriter::updateChildrenPosition(
        const PtNodeParams *const toBeUpdatedPtNodeParams, const int newChildrenPosition) {
    int childrenPosFieldPos = toBeUpdatedPtNodeParams->getChildrenPosFieldPos();
    return DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBuffer,
            newChildrenPosition, &childrenPosFieldPos);
}

bool DynamicPatriciaTrieNodeWriter::writePtNodeAndAdvancePosition(
        const PtNodeParams *const ptNodeParams, int *const ptNodeWritingPos) {
    return writePtNodeAndGetProbabilityFieldPosAndAdvancePosition(ptNodeParams,
            0 /* outProbabilityFieldPos */, ptNodeWritingPos);
}

bool DynamicPatriciaTrieNodeWriter::writeNewTerminalPtNodeAndAdvancePosition(
        const PtNodeParams *const ptNodeParams, int *const ptNodeWritingPos) {
    int probabilityFieldPos = NOT_A_DICT_POS;
    if (!writePtNodeAndGetProbabilityFieldPosAndAdvancePosition(ptNodeParams, &probabilityFieldPos,
            ptNodeWritingPos)) {
        return false;
    }
    if (probabilityFieldPos == NOT_A_DICT_POS) {
        return false;
    }
    const int probabilityToWrite = getUpdatedProbability(
            NOT_A_PROBABILITY /* originalProbability */, ptNodeParams->getProbability());
    return DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
            probabilityToWrite, &probabilityFieldPos);
}

bool DynamicPatriciaTrieNodeWriter::addNewBigramEntry(
        const PtNodeParams *const sourcePtNodeParams,
        const PtNodeParams *const targetPtNodeParam, const int probability,
        bool *const outAddedNewBigram) {
    const int newNodePos = mBuffer->getTailPosition();
    int writingPos = newNodePos;
    // Write a new PtNode using original PtNode's info to the tail of the dictionary in mBuffer.
    if (!writePtNodeAndAdvancePosition(sourcePtNodeParams, &writingPos)) {
        return false;
    }
    if (!markPtNodeAsMoved(sourcePtNodeParams, newNodePos, newNodePos)) {
        return false;
    }
    const PtNodeParams newPtNodeParams(
            mPtNodeReader->fetchNodeInfoInBufferFromPtNodePos(newNodePos));
    if (newPtNodeParams.getBigramsPos() != NOT_A_DICT_POS) {
        // Insert a new bigram entry into the existing bigram list.
        int bigramListPos = newPtNodeParams.getBigramsPos();
        return mBigramPolicy->addNewBigramEntryToBigramList(targetPtNodeParam->getHeadPos(),
                probability, &bigramListPos, outAddedNewBigram);
    } else {
        // The PtNode doesn't have a bigram list.
        *outAddedNewBigram = true;
        // First, Write a bigram entry at the tail position of the PtNode.
        if (!mBigramPolicy->writeNewBigramEntry(targetPtNodeParam->getHeadPos(), probability,
                &writingPos)) {
            return false;
        }
        // Then, Mark as the PtNode having bigram list in the flags.
        const PatriciaTrieReadingUtils::NodeFlags updatedFlags =
                PatriciaTrieReadingUtils::createAndGetFlags(newPtNodeParams.isBlacklisted(),
                        newPtNodeParams.isNotAWord(),
                        newPtNodeParams.getProbability() != NOT_A_PROBABILITY,
                        newPtNodeParams.getShortcutPos() != NOT_A_DICT_POS, true /* hasBigrams */,
                        newPtNodeParams.getCodePointCount() > 1, CHILDREN_POSITION_FIELD_SIZE);
        writingPos = newNodePos;
        // Write updated flags into the moved PtNode's flags field.
        return DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
                &writingPos);
    }
}

bool DynamicPatriciaTrieNodeWriter::removeBigramEntry(
        const PtNodeParams *const sourcePtNodeParams, const PtNodeParams *const targetPtNodeParam) {
    if (sourcePtNodeParams->getBigramsPos() == NOT_A_DICT_POS) {
        return false;
    }
    return mBigramPolicy->removeBigram(sourcePtNodeParams->getBigramsPos(),
            targetPtNodeParam->getHeadPos());
}

bool DynamicPatriciaTrieNodeWriter::updateAllBigramEntriesAndDeleteUselessEntries(
        const PtNodeParams *const sourcePtNodeParams, int *const outBigramEntryCount) {
    int bigramListPos = sourcePtNodeParams->getBigramsPos();
    return mBigramPolicy->updateAllBigramEntriesAndDeleteUselessEntries(&bigramListPos,
            outBigramEntryCount);
}

bool DynamicPatriciaTrieNodeWriter::updateAllPositionFields(
        const PtNodeParams *const toBeUpdatedPtNodeParams,
        const DictPositionRelocationMap *const dictPositionRelocationMap,
        int *const outBigramEntryCount) {
    int parentPos = toBeUpdatedPtNodeParams->getParentPos();
    if (parentPos != NOT_A_DICT_POS) {
        PtNodeWriter::PtNodePositionRelocationMap::const_iterator it =
                dictPositionRelocationMap->mPtNodePositionRelocationMap.find(parentPos);
        if (it != dictPositionRelocationMap->mPtNodePositionRelocationMap.end()) {
            parentPos = it->second;
        }
    }
    int writingPos = toBeUpdatedPtNodeParams->getHeadPos()
            + DynamicPatriciaTrieWritingUtils::NODE_FLAG_FIELD_SIZE;
    // Write an updated offset to the parent PtNode.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(mBuffer,
            parentPos, toBeUpdatedPtNodeParams->getHeadPos(), &writingPos)) {
        return false;
    }

    // Updates children position that is a PtNodeArray position.
    int childrenPos = toBeUpdatedPtNodeParams->getChildrenPos();
    if (childrenPos != NOT_A_DICT_POS) {
        PtNodeWriter::PtNodeArrayPositionRelocationMap::const_iterator it =
                dictPositionRelocationMap->mPtNodeArrayPositionRelocationMap.find(childrenPos);
        if (it != dictPositionRelocationMap->mPtNodeArrayPositionRelocationMap.end()) {
            childrenPos = it->second;
        }
    }
    if (!updateChildrenPosition(toBeUpdatedPtNodeParams, childrenPos)) {
        return false;
    }
    // Updates bigram target PtNode positions in the bigram list.
    int bigramsPos = toBeUpdatedPtNodeParams->getBigramsPos();
    int bigramCount = 0;
    if (bigramsPos != NOT_A_DICT_POS) {
        int bigramEntryCount;
        if (!mBigramPolicy->updateAllBigramTargetPtNodePositions(&bigramsPos,
                &dictPositionRelocationMap->mPtNodePositionRelocationMap, &bigramEntryCount)) {
            return false;
        }
        bigramCount += bigramEntryCount;
    }
    if (outBigramEntryCount) {
        *outBigramEntryCount = bigramCount;
    }
    return true;
}

bool DynamicPatriciaTrieNodeWriter::writePtNodeAndGetProbabilityFieldPosAndAdvancePosition(
        const PtNodeParams *const ptNodeParams, int *const outProbabilityFieldPos,
        int *const ptNodeWritingPos) {
    const int nodePos = *ptNodeWritingPos;
    // Write dummy flags. The Node flags are updated with appropriate flags at the last step of the
    // PtNode writing.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer,
            0 /* nodeFlags */, ptNodeWritingPos)) {
        return false;
    }
    // Calculate a parent offset and write the offset.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(mBuffer,
            ptNodeParams->getParentPos(), nodePos, ptNodeWritingPos)) {
        return false;
    }
    // Write code points
    if (!DynamicPatriciaTrieWritingUtils::writeCodePointsAndAdvancePosition(mBuffer,
            ptNodeParams->getCodePoints(), ptNodeParams->getCodePointCount(), ptNodeWritingPos)) {
        return false;
    }
    // Write probability when the probability is a valid probability, which means this node is
    // terminal.
    if (ptNodeParams->isTerminal()) {
        if (outProbabilityFieldPos) {
            *outProbabilityFieldPos = *ptNodeWritingPos;
        }
        if (ptNodeParams->getProbability() == NOT_A_PROBABILITY) {
            // Write a dummy probability.
            if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
                    0 /* probability */, ptNodeWritingPos)) {
                return false;
            }
        } else {
            if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
                    ptNodeParams->getProbability(), ptNodeWritingPos)) {
                return false;
            }
        }
    }
    // Write children position
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBuffer,
            ptNodeParams->getChildrenPos(), ptNodeWritingPos)) {
        return false;
    }
    // Copy shortcut list when the originalShortcutListPos is valid dictionary position.
    if (ptNodeParams->getShortcutPos() != NOT_A_DICT_POS) {
        int fromPos = ptNodeParams->getShortcutPos();
        if (!mShortcutPolicy->copyAllShortcutsAndReturnIfSucceededOrNot(mBuffer, &fromPos,
                ptNodeWritingPos)) {
            return false;
        }
    }
    // Copy bigram list when the originalBigramListPos is valid dictionary position.
    int bigramCount = 0;
    if (ptNodeParams->getBigramsPos() != NOT_A_DICT_POS) {
        int fromPos = ptNodeParams->getBigramsPos();
        if (!mBigramPolicy->copyAllBigrams(mBuffer, &fromPos, ptNodeWritingPos, &bigramCount)) {
            return false;
        }
    }
    // Create node flags and write them.
    PatriciaTrieReadingUtils::NodeFlags nodeFlags =
            PatriciaTrieReadingUtils::createAndGetFlags(ptNodeParams->isBlacklisted(),
                    ptNodeParams->isNotAWord(), ptNodeParams->isTerminal(),
                    ptNodeParams->getShortcutPos() != NOT_A_DICT_POS /* hasShortcutTargets */,
                    bigramCount > 0 /* hasBigrams */,
                    ptNodeParams->getCodePointCount() > 1 /* hasMultipleChars */,
                    CHILDREN_POSITION_FIELD_SIZE);
    int flagsFieldPos = nodePos;
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, nodeFlags,
            &flagsFieldPos)) {
        return false;
    }
    return true;
}

int DynamicPatriciaTrieNodeWriter::getUpdatedProbability(const int originalProbability,
        const int newProbability) const {
    if (mNeedsToDecayWhenUpdating) {
        return ForgettingCurveUtils::getUpdatedEncodedProbability(originalProbability,
                newProbability);
    } else {
        return newProbability;
    }
}

}
