/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"

#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_helper.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

const int DynamicBigramListPolicy::CONTINUING_BIGRAM_LINK_COUNT_LIMIT = 10000;
const int DynamicBigramListPolicy::BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT = 100000;

void DynamicBigramListPolicy::getNextBigram(int *const outBigramPos, int *const outProbability,
        bool *const outHasNext, int *const bigramEntryPos) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramEntryPos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *bigramEntryPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int originalBigramPos;
    BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(buffer, &bigramFlags,
            &originalBigramPos, bigramEntryPos);
    if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
        originalBigramPos += mBuffer->getOriginalBufferSize();
    }
    *outProbability = BigramListReadWriteUtils::getProbabilityFromFlags(bigramFlags);
    *outHasNext = BigramListReadWriteUtils::hasNext(bigramFlags);
    if (mIsDecayingDict && !ForgettingCurveUtils::isValidEncodedProbability(*outProbability)) {
        // This bigram is too weak to output.
        *outBigramPos = NOT_A_DICT_POS;
    } else {
        *outBigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
    }
    if (usesAdditionalBuffer) {
        *bigramEntryPos += mBuffer->getOriginalBufferSize();
    }
}

void DynamicBigramListPolicy::skipAllBigrams(int *const bigramListPos) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramListPos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *bigramListPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::skipExistingBigrams(buffer, bigramListPos);
    if (usesAdditionalBuffer) {
        *bigramListPos += mBuffer->getOriginalBufferSize();
    }
}

bool DynamicBigramListPolicy::copyAllBigrams(BufferWithExtendableBuffer *const bufferToWrite,
        int *const fromPos, int *const toPos, int *const outBigramsCount) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*fromPos);
    if (usesAdditionalBuffer) {
        *fromPos -= mBuffer->getOriginalBufferSize();
    }
    *outBigramsCount = 0;
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int bigramEntryCount = 0;
    int lastWrittenEntryPos = NOT_A_DICT_POS;
    do {
        if (++bigramEntryCount > BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT) {
            AKLOGE("Too many bigram entries. Entry count: %d, Limit: %d",
                    bigramEntryCount, BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT);
            ASSERT(false);
            return false;
        }
        // The buffer address can be changed after calling buffer writing methods.
        int originalBigramPos;
        BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                mBuffer->getBuffer(usesAdditionalBuffer), &bigramFlags, &originalBigramPos,
                fromPos);
        if (originalBigramPos == NOT_A_DICT_POS) {
            // skip invalid bigram entry.
            continue;
        }
        if (usesAdditionalBuffer) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        const int bigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
        if (bigramPos == NOT_A_DICT_POS) {
            // Target PtNode has been invalidated.
            continue;
        }
        lastWrittenEntryPos = *toPos;
        if (!BigramListReadWriteUtils::createAndWriteBigramEntry(bufferToWrite, bigramPos,
                BigramListReadWriteUtils::getProbabilityFromFlags(bigramFlags),
                BigramListReadWriteUtils::hasNext(bigramFlags), toPos)) {
            return false;
        }
        (*outBigramsCount)++;
    } while(BigramListReadWriteUtils::hasNext(bigramFlags));
    // Makes the last entry the terminal of the list. Updates the flags.
    if (lastWrittenEntryPos != NOT_A_DICT_POS) {
        if (!BigramListReadWriteUtils::setHasNextFlag(bufferToWrite, false /* hasNext */,
                lastWrittenEntryPos)) {
            return false;
        }
    }
    if (usesAdditionalBuffer) {
        *fromPos += mBuffer->getOriginalBufferSize();
    }
    return true;
}

// Finding useless bigram entries and remove them. Bigram entry is useless when the target PtNode
// has been deleted or is not a valid terminal.
bool DynamicBigramListPolicy::updateAllBigramEntriesAndDeleteUselessEntries(
        int *const bigramListPos, int *const outValidBigramEntryCount) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramListPos);
    if (usesAdditionalBuffer) {
        *bigramListPos -= mBuffer->getOriginalBufferSize();
    }
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, this /* bigramsPolicy */, mShortcutPolicy);
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int bigramEntryCount = 0;
    do {
        if (++bigramEntryCount > BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT) {
            AKLOGE("Too many bigram entries. Entry count: %d, Limit: %d",
                    bigramEntryCount, BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT);
            ASSERT(false);
            return false;
        }
        int bigramEntryPos = *bigramListPos;
        int originalBigramPos;
        // The buffer address can be changed after calling buffer writing methods.
        BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                mBuffer->getBuffer(usesAdditionalBuffer), &bigramFlags, &originalBigramPos,
                bigramListPos);
        if (usesAdditionalBuffer) {
            bigramEntryPos += mBuffer->getOriginalBufferSize();
        }
        if (originalBigramPos == NOT_A_DICT_POS) {
            // This entry has already been removed.
            continue;
        }
        if (usesAdditionalBuffer) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        const int bigramTargetNodePos =
                followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
        nodeReader.fetchNodeInfoInBufferFromPtNodePos(bigramTargetNodePos);
        if (nodeReader.isDeleted() || !nodeReader.isTerminal()
                || bigramTargetNodePos == NOT_A_DICT_POS) {
            // The target is no longer valid terminal. Invalidate the current bigram entry.
            if (!BigramListReadWriteUtils::writeBigramEntry(mBuffer, bigramFlags,
                    NOT_A_DICT_POS /* targetPtNodePos */, &bigramEntryPos)) {
                return false;
            }
            continue;
        }
        bool isRemoved = false;
        if (!updateProbabilityForDecay(bigramFlags, bigramTargetNodePos, &bigramEntryPos,
                &isRemoved)) {
            return false;
        }
        if (!isRemoved) {
            (*outValidBigramEntryCount) += 1;
        }
    } while(BigramListReadWriteUtils::hasNext(bigramFlags));
    return true;
}

// Updates bigram target PtNode positions in the list after the placing step in GC.
bool DynamicBigramListPolicy::updateAllBigramTargetPtNodePositions(int *const bigramListPos,
        const DynamicPatriciaTrieWritingHelper::PtNodePositionRelocationMap *const
                ptNodePositionRelocationMap, int *const outBigramEntryCount) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramListPos);
    if (usesAdditionalBuffer) {
        *bigramListPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int bigramEntryCount = 0;
    do {
        if (++bigramEntryCount > BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT) {
            AKLOGE("Too many bigram entries. Entry count: %d, Limit: %d",
                    bigramEntryCount, BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT);
            ASSERT(false);
            return false;
        }
        int bigramEntryPos = *bigramListPos;
        if (usesAdditionalBuffer) {
            bigramEntryPos += mBuffer->getOriginalBufferSize();
        }
        int bigramTargetPtNodePos;
        // The buffer address can be changed after calling buffer writing methods.
        BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                mBuffer->getBuffer(usesAdditionalBuffer), &bigramFlags, &bigramTargetPtNodePos,
                bigramListPos);
        if (bigramTargetPtNodePos == NOT_A_DICT_POS) {
            continue;
        }
        if (usesAdditionalBuffer) {
            bigramTargetPtNodePos += mBuffer->getOriginalBufferSize();
        }

        DynamicPatriciaTrieWritingHelper::PtNodePositionRelocationMap::const_iterator it =
                ptNodePositionRelocationMap->find(bigramTargetPtNodePos);
        if (it != ptNodePositionRelocationMap->end()) {
            bigramTargetPtNodePos = it->second;
        } else {
            bigramTargetPtNodePos = NOT_A_DICT_POS;
        }
        if (!BigramListReadWriteUtils::writeBigramEntry(mBuffer, bigramFlags,
                bigramTargetPtNodePos, &bigramEntryPos)) {
            return false;
        }
    } while(BigramListReadWriteUtils::hasNext(bigramFlags));
    (*outBigramEntryCount) = bigramEntryCount;
    return true;
}

bool DynamicBigramListPolicy::addNewBigramEntryToBigramList(const int bigramTargetPos,
        const int probability, int *const bigramListPos, bool *const outAddedNewBigram) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*bigramListPos);
    if (usesAdditionalBuffer) {
        *bigramListPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int bigramEntryCount = 0;
    do {
        if (++bigramEntryCount > BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT) {
            AKLOGE("Too many bigram entries. Entry count: %d, Limit: %d",
                    bigramEntryCount, BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT);
            ASSERT(false);
            return false;
        }
        int entryPos = *bigramListPos;
        if (usesAdditionalBuffer) {
            entryPos += mBuffer->getOriginalBufferSize();
        }
        int originalBigramPos;
        // The buffer address can be changed after calling buffer writing methods.
        BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                mBuffer->getBuffer(usesAdditionalBuffer), &bigramFlags, &originalBigramPos,
                bigramListPos);
        if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        if (followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos) == bigramTargetPos) {
            // Update this bigram entry.
            *outAddedNewBigram = false;
            const int originalProbability = BigramListReadWriteUtils::getProbabilityFromFlags(
                    bigramFlags);
            const int probabilityToWrite = mIsDecayingDict ?
                    ForgettingCurveUtils::getUpdatedEncodedProbability(originalProbability,
                            probability) : probability;
            const BigramListReadWriteUtils::BigramFlags updatedFlags =
                    BigramListReadWriteUtils::setProbabilityInFlags(bigramFlags,
                            probabilityToWrite);
            return BigramListReadWriteUtils::writeBigramEntry(mBuffer, updatedFlags,
                    originalBigramPos, &entryPos);
        }
        if (BigramListReadWriteUtils::hasNext(bigramFlags)) {
            continue;
        }
        // The current last entry is found.
        // First, update the flags of the last entry.
        if (!BigramListReadWriteUtils::setHasNextFlag(mBuffer, true /* hasNext */, entryPos)) {
            *outAddedNewBigram = false;
            return false;
        }
        if (usesAdditionalBuffer) {
            *bigramListPos += mBuffer->getOriginalBufferSize();
        }
        // Then, add a new entry after the last entry.
        *outAddedNewBigram = true;
        return writeNewBigramEntry(bigramTargetPos, probability, bigramListPos);
    } while(BigramListReadWriteUtils::hasNext(bigramFlags));
    // We return directly from the while loop.
    ASSERT(false);
    return false;
}

bool DynamicBigramListPolicy::writeNewBigramEntry(const int bigramTargetPos, const int probability,
        int *const writingPos) {
    // hasNext is false because we are adding a new bigram entry at the end of the bigram list.
    const int probabilityToWrite = mIsDecayingDict ?
            ForgettingCurveUtils::getUpdatedEncodedProbability(NOT_A_PROBABILITY, probability) :
                    probability;
    return BigramListReadWriteUtils::createAndWriteBigramEntry(mBuffer, bigramTargetPos,
            probabilityToWrite, false /* hasNext */, writingPos);
}

bool DynamicBigramListPolicy::removeBigram(const int bigramListPos, const int bigramTargetPos) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(bigramListPos);
    int pos = bigramListPos;
    if (usesAdditionalBuffer) {
        pos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags bigramFlags;
    int bigramEntryCount = 0;
    do {
        if (++bigramEntryCount > BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT) {
            AKLOGE("Too many bigram entries. Entry count: %d, Limit: %d",
                    bigramEntryCount, BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT);
            ASSERT(false);
            return false;
        }
        int bigramEntryPos = pos;
        int originalBigramPos;
        // The buffer address can be changed after calling buffer writing methods.
        BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                mBuffer->getBuffer(usesAdditionalBuffer), &bigramFlags, &originalBigramPos, &pos);
        if (usesAdditionalBuffer) {
            bigramEntryPos += mBuffer->getOriginalBufferSize();
        }
        if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        const int bigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
        if (bigramPos != bigramTargetPos) {
            continue;
        }
        // Target entry is found. Write an invalid target position to mark the bigram invalid.
        return BigramListReadWriteUtils::writeBigramEntry(mBuffer, bigramFlags,
                NOT_A_DICT_POS /* targetOffset */, &bigramEntryPos);
    } while(BigramListReadWriteUtils::hasNext(bigramFlags));
    return false;
}

int DynamicBigramListPolicy::followBigramLinkAndGetCurrentBigramPtNodePos(
        const int originalBigramPos) const {
    if (originalBigramPos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    int currentPos = originalBigramPos;
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, this /* bigramsPolicy */, mShortcutPolicy);
    nodeReader.fetchNodeInfoInBufferFromPtNodePos(currentPos);
    int bigramLinkCount = 0;
    while (nodeReader.getBigramLinkedNodePos() != NOT_A_DICT_POS) {
        currentPos = nodeReader.getBigramLinkedNodePos();
        nodeReader.fetchNodeInfoInBufferFromPtNodePos(currentPos);
        bigramLinkCount++;
        if (bigramLinkCount > CONTINUING_BIGRAM_LINK_COUNT_LIMIT) {
            AKLOGE("Bigram link is invalid. start position: %d", originalBigramPos);
            ASSERT(false);
            return NOT_A_DICT_POS;
        }
    }
    return currentPos;
}

bool DynamicBigramListPolicy::updateProbabilityForDecay(
        const BigramListReadWriteUtils::BigramFlags bigramFlags, const int targetPtNodePos,
        int *const bigramEntryPos, bool *const outRemoved) const {
    *outRemoved = false;
    if (mIsDecayingDict) {
        // Update bigram probability for decaying.
        const int newProbability = ForgettingCurveUtils::getEncodedProbabilityToSave(
                BigramListReadWriteUtils::getProbabilityFromFlags(bigramFlags), mHeaderPolicy);
        if (ForgettingCurveUtils::isValidEncodedProbability(newProbability)) {
            // Write new probability.
            const BigramListReadWriteUtils::BigramFlags updatedBigramFlags =
                    BigramListReadWriteUtils::setProbabilityInFlags(
                            bigramFlags, newProbability);
            if (!BigramListReadWriteUtils::writeBigramEntry(mBuffer, updatedBigramFlags,
                    targetPtNodePos, bigramEntryPos)) {
                return false;
            }
        } else {
            // Remove current bigram entry.
            *outRemoved = true;
            if (!BigramListReadWriteUtils::writeBigramEntry(mBuffer, bigramFlags,
                    NOT_A_DICT_POS /* targetPtNodePos */, bigramEntryPos)) {
                return false;
            }
        }
    }
    return true;
}

} // namespace latinime
