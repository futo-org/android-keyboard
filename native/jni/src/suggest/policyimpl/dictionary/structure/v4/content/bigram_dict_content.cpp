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

#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_dict_content.h"

#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

void BigramDictContent::getBigramEntryAndAdvancePosition(int *const outProbability,
        bool *const outHasNext, int *const outTargetTerminalId, int *const bigramEntryPos) const {
    const BufferWithExtendableBuffer *const bigramListBuffer = getContentBuffer();
    const int bigramFlags = bigramListBuffer->readUintAndAdvancePosition(
            Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE, bigramEntryPos);
    if (outProbability) {
        *outProbability = bigramFlags & Ver4DictConstants::BIGRAM_PROBABILITY_MASK;
    }
    if (outHasNext) {
        *outHasNext = (bigramFlags & Ver4DictConstants::BIGRAM_HAS_NEXT_MASK) != 0;
    }
    const int targetTerminalId = bigramListBuffer->readUintAndAdvancePosition(
            Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE, bigramEntryPos);
    if (outTargetTerminalId) {
        *outTargetTerminalId =
                (targetTerminalId == Ver4DictConstants::INVALID_BIGRAM_TARGET_TERMINAL_ID) ?
                        Ver4DictConstants::NOT_A_TERMINAL_ID : targetTerminalId;
    }
}

bool BigramDictContent::writeBigramEntryAndAdvancePosition(const int probability, const int hasNext,
        const int targetTerminalId, int *const entryWritingPos) {
    BufferWithExtendableBuffer *const bigramListBuffer = getWritableContentBuffer();
    const int bigramFlags = createAndGetBigramFlags(probability, hasNext);
    if (!bigramListBuffer->writeUintAndAdvancePosition(bigramFlags,
            Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE, entryWritingPos)) {
        AKLOGE("Cannot write bigram flags. pos: %d, flags: %x", *entryWritingPos, bigramFlags);
        return false;
    }
    const int targetTerminalIdToWrite =
            (targetTerminalId == Ver4DictConstants::NOT_A_TERMINAL_ID) ?
                    Ver4DictConstants::INVALID_BIGRAM_TARGET_TERMINAL_ID : targetTerminalId;
    return bigramListBuffer->writeUintAndAdvancePosition(targetTerminalIdToWrite,
            Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE, entryWritingPos);
}

bool BigramDictContent::copyBigramList(const int bigramListPos, const int toPos) {
    bool hasNext = true;
    int readingPos = bigramListPos;
    int writingPos = toPos;
    while (hasNext) {
        int probability = NOT_A_PROBABILITY;
        int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
        getBigramEntryAndAdvancePosition(&probability, &hasNext, &targetTerminalId,
                &readingPos);
        if (!writeBigramEntryAndAdvancePosition(probability, hasNext, targetTerminalId,
                &writingPos)) {
            AKLOGE("Cannot write bigram entry to copy. pos: %d", writingPos);
            return false;
        }
    }
    return true;
}

bool BigramDictContent::runGC(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        const BigramDictContent *const originalBigramDictContent,
        int *const outBigramEntryCount) {
    for (TerminalPositionLookupTable::TerminalIdMap::const_iterator it = terminalIdMap->begin();
            it != terminalIdMap->end(); ++it) {
        const int originalBigramListPos =
                originalBigramDictContent->getBigramListHeadPos(it->first);
        if (originalBigramListPos == NOT_A_DICT_POS) {
            // This terminal does not have a bigram list.
            continue;
        }
        const int bigramListPos = getContentBuffer()->getTailPosition();
        int bigramEntryCount = 0;
        // Copy bigram list with GC from original content.
        if (!runGCBigramList(originalBigramListPos, originalBigramDictContent, bigramListPos,
                terminalIdMap, &bigramEntryCount)) {
            AKLOGE("Cannot complete GC for the bigram list. original pos: %d, pos: %d",
                    originalBigramListPos, bigramListPos);
            return false;
        }
        if (bigramEntryCount == 0) {
            // All bigram entries are useless. This terminal does not have a bigram list.
            continue;
        }
        *outBigramEntryCount += bigramEntryCount;
        // Set bigram list position to the lookup table.
        if (!getUpdatableAddressLookupTable()->set(it->second, bigramListPos)) {
            AKLOGE("Cannot set bigram list position. terminal id: %d, pos: %d",
                    it->second, bigramListPos);
            return false;
        }
    }
    return true;
}

// Returns whether GC for the bigram list was succeeded or not.
bool BigramDictContent::runGCBigramList(const int bigramListPos,
        const BigramDictContent *const sourceBigramDictContent, const int toPos,
        const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        int *const outEntrycount) {
    bool hasNext = true;
    int readingPos = bigramListPos;
    int writingPos = toPos;
    int lastEntryPos = NOT_A_DICT_POS;
    while (hasNext) {
        int probability = NOT_A_PROBABILITY;
        int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
        sourceBigramDictContent->getBigramEntryAndAdvancePosition(&probability, &hasNext,
                &targetTerminalId, &readingPos);
        if (targetTerminalId == Ver4DictConstants::NOT_A_TERMINAL_ID) {
            continue;
        }
        TerminalPositionLookupTable::TerminalIdMap::const_iterator it =
                terminalIdMap->find(targetTerminalId);
        if (it == terminalIdMap->end()) {
            // Target word has been removed.
            continue;
        }
        lastEntryPos = hasNext ? writingPos : NOT_A_DICT_POS;
        if (!writeBigramEntryAndAdvancePosition(probability, hasNext, it->second,
                &writingPos)) {
            AKLOGE("Cannot write bigram entry to run GC. pos: %d", writingPos);
            return false;
        }
        *outEntrycount += 1;
    }
    if (lastEntryPos != NOT_A_DICT_POS) {
        // Update has next flag in the last written entry.
        int probability = NOT_A_PROBABILITY;
        int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
        getBigramEntry(&probability, 0 /* outHasNext */, &targetTerminalId, lastEntryPos);
        if (!writeBigramEntry(probability, false /* hasNext */, targetTerminalId, writingPos)) {
            AKLOGE("Cannot write bigram entry to set hasNext flag after GC. pos: %d", writingPos);
            return false;
        }
    }
    return true;
}

} // namespace latinime
