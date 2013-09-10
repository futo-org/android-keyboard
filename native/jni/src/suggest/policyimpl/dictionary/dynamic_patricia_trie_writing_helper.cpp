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
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_utils.h"
#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/shortcut/dynamic_shortcut_list_policy.h"

namespace latinime {

// TODO: Enable dynamic update and remove this flag.
const bool DynamicPatriciaTrieWritingHelper::ENABLE_DYNAMIC_UPDATE = false;

bool DynamicPatriciaTrieWritingHelper::addUnigramWord(
        DynamicPatriciaTrieReadingHelper *const readingHelper,
        const int *const wordCodePoints, const int codePointCount, const int probability) {
    int parentPos = NOT_A_VALID_WORD_POS;
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
                if (ENABLE_DYNAMIC_UPDATE) {
                    return reallocatePtNodeAndAddNewPtNodes(nodeReader,
                            readingHelper->getMergedNodeCodePoints(), j, probability,
                            wordCodePoints + matchedCodePointCount,
                            codePointCount - matchedCodePointCount);
                } else {
                    return false;
                }
            }
        }
        // All characters are matched.
        if (codePointCount == readingHelper->getTotalCodePointCount()) {
            if (ENABLE_DYNAMIC_UPDATE) {
                return setPtNodeProbability(nodeReader, probability,
                        readingHelper->getMergedNodeCodePoints());
            } else {
                return false;
            }
        }
        if (!nodeReader->hasChildren()) {
            if (ENABLE_DYNAMIC_UPDATE) {
                return createChildrenPtNodeArrayAndAChildPtNode(nodeReader, probability,
                        wordCodePoints + readingHelper->getTotalCodePointCount(),
                        codePointCount - readingHelper->getTotalCodePointCount());
            } else {
                return false;
            }
        }
        // Advance to the children nodes.
        parentPos = nodeReader->getNodePos();
        readingHelper->readChildNode();
    }
    if (readingHelper->isError()) {
        // The dictionary is invalid.
        return false;
    }
    int pos = readingHelper->getPosOfLastForwardLinkField();
    if (ENABLE_DYNAMIC_UPDATE) {
        return createAndInsertNodeIntoPtNodeArray(parentPos,
                wordCodePoints + readingHelper->getPrevTotalCodePointCount(),
                codePointCount - readingHelper->getPrevTotalCodePointCount(),
                probability, &pos);
    } else {
        return false;
    }
}

bool DynamicPatriciaTrieWritingHelper::addBigramWords(const int word0Pos, const int word1Pos,
        const int probability) {
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoFromBuffer(word0Pos);
    if (nodeReader.isDeleted()) {
        return false;
    }
    // TODO: Implement.
    return false;
}

// Remove a bigram relation from word0Pos to word1Pos.
bool DynamicPatriciaTrieWritingHelper::removeBigramWords(const int word0Pos, const int word1Pos) {
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoFromBuffer(word0Pos);
    if (nodeReader.isDeleted() || nodeReader.getBigramsPos() == NOT_A_DICT_POS) {
        return false;
    }
    // TODO: Implement.
    return false;
}

bool DynamicPatriciaTrieWritingHelper::markNodeAsMovedAndSetPosition(
        const DynamicPatriciaTrieNodeReader *const originalNode, const int movedPos) {
    int pos = originalNode->getNodePos();
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
    int writingPos = originalNode->getNodePos();
    // Update flags.
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, updatedFlags,
            &writingPos)) {
        return false;
    }
    // Update moved position, which is stored in the parent offset field.
    const int movedPosOffset = movedPos - originalNode->getNodePos();
    if (!DynamicPatriciaTrieWritingUtils::writeParentOffsetAndAdvancePosition(
            mBuffer, movedPosOffset, &writingPos)) {
        return false;
    }
    return true;
}

// Write new PtNode at writingPos.
bool DynamicPatriciaTrieWritingHelper::writePtNodeWithFullInfoToBuffer(const bool isBlacklisted,
        const bool isNotAWord, const int parentPos, const int *const codePoints,
        const int codePointCount, const int probability, const int childrenPos,
        const int originalBigramListPos, const int originalShortcutListPos,
        int *const writingPos) {
    const int nodePos = *writingPos;
    // Create node flags and write them.
    const PatriciaTrieReadingUtils::NodeFlags nodeFlags =
            PatriciaTrieReadingUtils::createAndGetFlags(isBlacklisted, isNotAWord,
                    probability != NOT_A_PROBABILITY, originalShortcutListPos != NOT_A_DICT_POS,
                    originalBigramListPos != NOT_A_DICT_POS, codePointCount > 1,
                    3 /* childrenPositionFieldSize */);
    if (!DynamicPatriciaTrieWritingUtils::writeFlagsAndAdvancePosition(mBuffer, nodeFlags,
            writingPos)) {
        return false;
    }
    // Calculate a parent offset and write the offset.
    const int parentOffset = (parentPos != NOT_A_DICT_POS) ? parentPos - nodePos : NOT_A_DICT_POS;
    if (!DynamicPatriciaTrieWritingUtils::writeParentOffsetAndAdvancePosition(mBuffer,
            parentOffset, writingPos)) {
        return false;
    }
    // Write code points
    if (!DynamicPatriciaTrieWritingUtils::writeCodePointsAndAdvancePosition(mBuffer, codePoints,
            codePointCount, writingPos)) {
        return false;;
    }
    // Write probability when the probability is a valid probability, which means this node is
    // terminal.
    if (probability != NOT_A_PROBABILITY) {
        if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
                probability, writingPos)) {
            return false;
        }
    }
    // Write children position
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBuffer,
            childrenPos, writingPos)) {
        return false;
    }
    // Copy shortcut list when the originalShortcutListPos is valid dictionary position.
    if (originalShortcutListPos != NOT_A_DICT_POS) {
        int fromPos = originalShortcutListPos;
        if (!mShortcutPolicy->copyAllShortcutsAndReturnIfSucceededOrNot(&fromPos, writingPos)) {
            return false;
        }
    }
    // Copy bigram list when the originalBigramListPos is valid dictionary position.
    if (originalBigramListPos != NOT_A_DICT_POS) {
        int fromPos = originalBigramListPos;
        if (!mBigramPolicy->copyAllBigrams(&fromPos, writingPos)) {
            return false;
        }
    }
    return true;
}

bool DynamicPatriciaTrieWritingHelper::writePtNodeToBuffer(const int parentPos,
        const int *const codePoints, const int codePointCount, const int probability,
        int *const writingPos) {
    return writePtNodeWithFullInfoToBuffer(false /* isBlacklisted */, false /* isNotAWord */,
            parentPos, codePoints, codePointCount, probability,
            NOT_A_DICT_POS /* childrenPos */, NOT_A_DICT_POS /* originalBigramsPos */,
            NOT_A_DICT_POS /* originalShortcutPos */, writingPos);
}

bool DynamicPatriciaTrieWritingHelper::writePtNodeToBufferByCopyingPtNodeInfo(
        const DynamicPatriciaTrieNodeReader *const originalNode, const int parentPos,
        const int *const codePoints, const int codePointCount, const int probability,
        int *const writingPos) {
    return writePtNodeWithFullInfoToBuffer(originalNode->isBlacklisted(),
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
        const int *const codePoints) {
    if (originalPtNode->isTerminal()) {
        // Overwrites the probability.
        int probabilityFieldPos = originalPtNode->getProbabilityFieldPos();
        if (!DynamicPatriciaTrieWritingUtils::writeProbabilityAndAdvancePosition(mBuffer,
                probability, &probabilityFieldPos)) {
            return false;
        }
    } else {
        // Make the node terminal and write the probability.
        int movedPos = mBuffer->getTailPosition();
        if (!markNodeAsMovedAndSetPosition(originalPtNode, movedPos)) {
            return false;
        }
        if (!writePtNodeToBufferByCopyingPtNodeInfo(originalPtNode, originalPtNode->getParentPos(),
                codePoints, originalPtNode->getCodePointCount(), probability, &movedPos)) {
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
    return createNewPtNodeArrayWithAChildPtNode(parentNode->getNodePos(), codePoints,
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
    if (!writePtNodeToBuffer(parentPtNodePos, nodeCodePoints, nodeCodePointCount, probability,
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
    const int firstPtNodePos = mBuffer->getTailPosition();
    if (!markNodeAsMovedAndSetPosition(reallocatingPtNode, firstPtNodePos)) {
        return false;
    }
    int writingPos = firstPtNodePos;
    // Write the 1st part of the reallocating node. The children position will be updated later
    // with actual children position.
    const int newProbability = addsExtraChild ? NOT_A_PROBABILITY : probabilityOfNewPtNode;
    if (!writePtNodeToBuffer(reallocatingPtNode->getParentPos(), reallocatingPtNodeCodePoints,
            overlappingCodePointCount, newProbability, &writingPos)) {
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
    if (!writePtNodeToBufferByCopyingPtNodeInfo(reallocatingPtNode,
            reallocatingPtNode->getNodePos(),
            reallocatingPtNodeCodePoints + overlappingCodePointCount,
            reallocatingPtNode->getCodePointCount() - overlappingCodePointCount,
            reallocatingPtNode->getProbability(), &writingPos)) {
        return false;
    }
    if (addsExtraChild) {
        if (!writePtNodeToBuffer(reallocatingPtNode->getNodePos(),
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
    // Load node info. Information of the 1st part will be fetched.
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, mBigramPolicy, mShortcutPolicy);
    nodeReader.fetchNodeInfoFromBuffer(firstPtNodePos);
    // Update children position.
    int childrenPosFieldPos = nodeReader.getChildrenPosFieldPos();
    if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(mBuffer,
            actualChildrenPos, &childrenPosFieldPos)) {
        return false;
    }
    return true;
}

} // namespace latinime
