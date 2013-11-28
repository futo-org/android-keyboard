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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_writer.h"

#include "suggest/policyimpl/dictionary/bigram/ver4_bigram_list_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/dynamic_shortcut_list_policy.h"
#include "suggest/policyimpl/dictionary/structure/v2/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

const int Ver4PatriciaTrieNodeWriter::CHILDREN_POSITION_FIELD_SIZE = 3;

bool Ver4PatriciaTrieNodeWriter::markPtNodeAsDeleted(
        const PtNodeParams *const toBeUpdatedPtNodeParams) {
    int pos = toBeUpdatedPtNodeParams->getHeadPos();
    const bool usesAdditionalBuffer = mTrieBuffer->isInAdditionalBuffer(pos);
    const uint8_t *const dictBuf = mTrieBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        pos -= mTrieBuffer->getOriginalBufferSize();
    }
    // Read original flags
    const PatriciaTrieReadingUtils::NodeFlags originalFlags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictBuf, &pos);
    const PatriciaTrieReadingUtils::NodeFlags updatedFlags =
            DynamicPatriciaTrieReadingUtils::updateAndGetFlags(originalFlags, false /* isMoved */,
                    true /* isDeleted */);
    int writingPos = toBeUpdatedPtNodeParams->getHeadPos();
    // Update flags.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mTrieBuffer, updatedFlags,
            &writingPos)) {
        return false;
    }
    if (toBeUpdatedPtNodeParams->getTerminalId() != NOT_A_DICT_POS) {
        // The PtNode is a terminal. Delete entry from the terminal position lookup table.
        return mBuffers->getUpdatableTerminalPositionLookupTable()->setTerminalPtNodePosition(
                toBeUpdatedPtNodeParams->getTerminalId(), NOT_A_DICT_POS /* ptNodePos */);
    } else {
        return true;
    }
}

bool Ver4PatriciaTrieNodeWriter::markPtNodeAsMoved(
        const PtNodeParams *const toBeUpdatedPtNodeParams,
        const int movedPos, const int bigramLinkedNodePos) {
    int pos = toBeUpdatedPtNodeParams->getHeadPos();
    const bool usesAdditionalBuffer = mTrieBuffer->isInAdditionalBuffer(pos);
    const uint8_t *const dictBuf = mTrieBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        pos -= mTrieBuffer->getOriginalBufferSize();
    }
    // Read original flags
    const PatriciaTrieReadingUtils::NodeFlags originalFlags =
            PatriciaTrieReadingUtils::getFlagsAndAdvancePosition(dictBuf, &pos);
    const PatriciaTrieReadingUtils::NodeFlags updatedFlags =
            DynamicPatriciaTrieReadingUtils::updateAndGetFlags(originalFlags, true /* isMoved */,
                    false /* isDeleted */);
    int writingPos = toBeUpdatedPtNodeParams->getHeadPos();
    // Update flags.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mTrieBuffer, updatedFlags,
            &writingPos)) {
        return false;
    }
    // Update moved position, which is stored in the parent offset field.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(
            mTrieBuffer, movedPos, toBeUpdatedPtNodeParams->getHeadPos(), &writingPos)) {
        return false;
    }
    // Update bigram linked node position, which is stored in the children position field.
    int childrenPosFieldPos = toBeUpdatedPtNodeParams->getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(
            mTrieBuffer, bigramLinkedNodePos, &childrenPosFieldPos)) {
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
                    mTrieBuffer, bigramLinkedNodePos, childPtNodeParams.getHeadPos(),
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

bool Ver4PatriciaTrieNodeWriter::updatePtNodeProbability(
        const PtNodeParams *const toBeUpdatedPtNodeParams, const int newProbability) {
    if (!toBeUpdatedPtNodeParams->isTerminal()) {
        return false;
    }
    const int probabilityToWrite = getUpdatedProbability(toBeUpdatedPtNodeParams->getProbability(),
            newProbability);
    return mBuffers->getUpdatableProbabilityDictContent()->setProbability(
            toBeUpdatedPtNodeParams->getTerminalId(), probabilityToWrite);
}

bool Ver4PatriciaTrieNodeWriter::updateChildrenPosition(
        const PtNodeParams *const toBeUpdatedPtNodeParams, const int newChildrenPosition) {
    int childrenPosFieldPos = toBeUpdatedPtNodeParams->getChildrenPosFieldPos();
    return DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mTrieBuffer,
            newChildrenPosition, &childrenPosFieldPos);
}

bool Ver4PatriciaTrieNodeWriter::updateTerminalId(const PtNodeParams *const toBeUpdatedPtNodeParams,
        const int newTerminalId) {
    return mTrieBuffer->writeUint(newTerminalId, Ver4DictConstants::TERMINAL_ID_FIELD_SIZE,
            toBeUpdatedPtNodeParams->getTerminalIdFieldPos());
}

bool Ver4PatriciaTrieNodeWriter::writePtNodeAndAdvancePosition(
        const PtNodeParams *const ptNodeParams, int *const ptNodeWritingPos) {
    return writePtNodeAndGetTerminalIdAndAdvancePosition(ptNodeParams, 0 /* outTerminalId */,
            ptNodeWritingPos);
}


bool Ver4PatriciaTrieNodeWriter::writeNewTerminalPtNodeAndAdvancePosition(
        const PtNodeParams *const ptNodeParams, int *const ptNodeWritingPos) {
    int terminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
    if (!writePtNodeAndGetTerminalIdAndAdvancePosition(ptNodeParams, &terminalId,
            ptNodeWritingPos)) {
        return false;
    }
    // Write probability.
    const int probabilityToWrite = getUpdatedProbability(NOT_A_PROBABILITY,
            ptNodeParams->getProbability());
    return mBuffers->getUpdatableProbabilityDictContent()->setProbability(terminalId,
            probabilityToWrite);
}

bool Ver4PatriciaTrieNodeWriter::addNewBigramEntry(
        const PtNodeParams *const sourcePtNodeParams,
        const PtNodeParams *const targetPtNodeParam, const int probability,
        bool *const outAddedNewBigram) {
    return mBigramPolicy->addNewEntry(sourcePtNodeParams->getTerminalId(),
            targetPtNodeParam->getTerminalId(), probability, outAddedNewBigram);
}

bool Ver4PatriciaTrieNodeWriter::removeBigramEntry(
        const PtNodeParams *const sourcePtNodeParams, const PtNodeParams *const targetPtNodeParam) {
    return mBigramPolicy->removeEntry(sourcePtNodeParams->getTerminalId(),
            targetPtNodeParam->getTerminalId());
}

bool Ver4PatriciaTrieNodeWriter::updateAllBigramEntriesAndDeleteUselessEntries(
            const PtNodeParams *const sourcePtNodeParams, int *const outBigramEntryCount) {
    return mBigramPolicy->updateAllBigramEntriesAndDeleteUselessEntries(
            sourcePtNodeParams->getTerminalId(), outBigramEntryCount);
}

bool Ver4PatriciaTrieNodeWriter::updateAllPositionFields(
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
    // Write updated parent offset.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(mTrieBuffer,
            parentPos, toBeUpdatedPtNodeParams->getHeadPos(), &writingPos)) {
        return false;
    }

    // Updates children position.
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

    // Counts bigram entries.
    if (outBigramEntryCount) {
        *outBigramEntryCount = mBigramPolicy->getBigramEntryConut(
                toBeUpdatedPtNodeParams->getTerminalId());
    }
    return true;
}

bool Ver4PatriciaTrieNodeWriter::writePtNodeAndGetTerminalIdAndAdvancePosition(
        const PtNodeParams *const ptNodeParams, int *const outTerminalId,
        int *const ptNodeWritingPos) {
    const int nodePos = *ptNodeWritingPos;
    // Write dummy flags. The Node flags are updated with appropriate flags at the last step of the
    // PtNode writing.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mTrieBuffer,
            0 /* nodeFlags */, ptNodeWritingPos)) {
        return false;
    }
    // Calculate a parent offset and write the offset.
    if (!DynamicPatriciaTrieWritingUtils::writeParentPosOffsetAndAdvancePosition(mTrieBuffer,
            ptNodeParams->getParentPos(), nodePos, ptNodeWritingPos)) {
        return false;
    }
    // Write code points
    if (!DynamicPatriciaTrieWritingUtils::writeCodePointsAndAdvancePosition(mTrieBuffer,
            ptNodeParams->getCodePoints(), ptNodeParams->getCodePointCount(), ptNodeWritingPos)) {
        return false;
    }
    int terminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
    if (ptNodeParams->getTerminalId() != Ver4DictConstants::NOT_A_TERMINAL_ID) {
        terminalId = ptNodeParams->getTerminalId();
    } else if (ptNodeParams->isTerminal()) {
        // Write terminal information using a new terminal id.
        // Get a new unused terminal id.
        terminalId = mBuffers->getTerminalPositionLookupTable()->getNextTerminalId();
    }
    const int isTerminal = terminalId != Ver4DictConstants::NOT_A_TERMINAL_ID;
    if (isTerminal) {
        // Update the lookup table.
        if (!mBuffers->getUpdatableTerminalPositionLookupTable()->setTerminalPtNodePosition(
                terminalId, nodePos)) {
            return false;
        }
        // Write terminal Id.
        if (!mTrieBuffer->writeUintAndAdvancePosition(terminalId,
                Ver4DictConstants::TERMINAL_ID_FIELD_SIZE, ptNodeWritingPos)) {
            return false;
        }
        // Write probability.
        if (ptNodeParams->getProbability() != NOT_A_PROBABILITY) {
            if (!mBuffers->getUpdatableProbabilityDictContent()->setProbability(
                    terminalId, ptNodeParams->getProbability())) {
                return false;
            }
        }
        if (outTerminalId) {
            *outTerminalId = terminalId;
        }
    }
    // Write children position
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mTrieBuffer,
            ptNodeParams->getChildrenPos(), ptNodeWritingPos)) {
        return false;
    }
    // Create node flags and write them.
    PatriciaTrieReadingUtils::NodeFlags nodeFlags =
            PatriciaTrieReadingUtils::createAndGetFlags(ptNodeParams->isBlacklisted(),
                    ptNodeParams->isNotAWord(), isTerminal,
                    ptNodeParams->hasShortcutTargets(), ptNodeParams->hasBigrams(),
                    ptNodeParams->getCodePointCount() > 1 /* hasMultipleChars */,
                    CHILDREN_POSITION_FIELD_SIZE);
    int flagsFieldPos = nodePos;
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mTrieBuffer, nodeFlags,
            &flagsFieldPos)) {
        return false;
    }
    return true;
}

int Ver4PatriciaTrieNodeWriter::getUpdatedProbability(const int originalProbability,
        const int newProbability) const {
    if (mNeedsToDecayWhenUpdating) {
        return ForgettingCurveUtils::getUpdatedEncodedProbability(originalProbability,
                newProbability);
    } else {
        return newProbability;
    }
}

}
