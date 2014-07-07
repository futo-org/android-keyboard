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

const int BigramDictContent::INVALID_LINKED_ENTRY_POS = Ver4DictConstants::NOT_A_TERMINAL_ID;

const BigramEntry BigramDictContent::getBigramEntryAndAdvancePosition(
        int *const bigramEntryPos) const {
    const BufferWithExtendableBuffer *const bigramListBuffer = getContentBuffer();
    const int bigramEntryTailPos = (*bigramEntryPos) + getBigramEntrySize();
    if (*bigramEntryPos < 0 || bigramEntryTailPos > bigramListBuffer->getTailPosition()) {
        AKLOGE("Invalid bigram entry position. bigramEntryPos: %d, bigramEntryTailPos: %d, "
                "bufSize: %d", *bigramEntryPos, bigramEntryTailPos,
                        bigramListBuffer->getTailPosition());
        ASSERT(false);
        return BigramEntry(false /* hasNext */, NOT_A_PROBABILITY,
                Ver4DictConstants::NOT_A_TERMINAL_ID);
    }
    const int bigramFlags = bigramListBuffer->readUintAndAdvancePosition(
            Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE, bigramEntryPos);
    const bool isLink = (bigramFlags & Ver4DictConstants::BIGRAM_IS_LINK_MASK) != 0;
    int probability = NOT_A_PROBABILITY;
    int timestamp = NOT_A_TIMESTAMP;
    int level = 0;
    int count = 0;
    if (mHasHistoricalInfo) {
        timestamp = bigramListBuffer->readUintAndAdvancePosition(
                Ver4DictConstants::TIME_STAMP_FIELD_SIZE, bigramEntryPos);
        level = bigramListBuffer->readUintAndAdvancePosition(
                Ver4DictConstants::WORD_LEVEL_FIELD_SIZE, bigramEntryPos);
        count = bigramListBuffer->readUintAndAdvancePosition(
                Ver4DictConstants::WORD_COUNT_FIELD_SIZE, bigramEntryPos);
    } else {
        probability = bigramListBuffer->readUintAndAdvancePosition(
                Ver4DictConstants::PROBABILITY_SIZE, bigramEntryPos);
    }
    const int encodedTargetTerminalId = bigramListBuffer->readUintAndAdvancePosition(
            Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE, bigramEntryPos);
    const int targetTerminalId =
            (encodedTargetTerminalId == Ver4DictConstants::INVALID_BIGRAM_TARGET_TERMINAL_ID) ?
                    Ver4DictConstants::NOT_A_TERMINAL_ID : encodedTargetTerminalId;
    if (isLink) {
        const int linkedEntryPos = targetTerminalId;
        if (linkedEntryPos == INVALID_LINKED_ENTRY_POS) {
            // Bigram list terminator is found.
            return BigramEntry(false /* hasNext */, NOT_A_PROBABILITY,
                    Ver4DictConstants::NOT_A_TERMINAL_ID);
        }
        *bigramEntryPos = linkedEntryPos;
        return getBigramEntryAndAdvancePosition(bigramEntryPos);
    }
    // hasNext is always true because we should continue to read the next entry until the terminator
    // is found.
    if (mHasHistoricalInfo) {
        const HistoricalInfo historicalInfo(timestamp, level, count);
        return BigramEntry(true /* hasNext */, probability, &historicalInfo, targetTerminalId);
    } else {
        return BigramEntry(true /* hasNext */, probability, targetTerminalId);
    }
}

bool BigramDictContent::writeBigramEntryAndAdvancePosition(
        const BigramEntry *const bigramEntryToWrite, int *const entryWritingPos) {
    return writeBigramEntryAttributesAndAdvancePosition(false /* isLink */,
            bigramEntryToWrite->getProbability(), bigramEntryToWrite->getTargetTerminalId(),
            bigramEntryToWrite->getHistoricalInfo()->getTimeStamp(),
            bigramEntryToWrite->getHistoricalInfo()->getLevel(),
            bigramEntryToWrite->getHistoricalInfo()->getCount(),
            entryWritingPos);
}

bool BigramDictContent::writeBigramEntryAttributesAndAdvancePosition(
        const bool isLink, const int probability, const int targetTerminalId,
        const int timestamp, const int level, const int count, int *const entryWritingPos) {
    BufferWithExtendableBuffer *const bigramListBuffer = getWritableContentBuffer();
    const int bigramFlags = isLink ? Ver4DictConstants::BIGRAM_IS_LINK_MASK : 0;
    if (!bigramListBuffer->writeUintAndAdvancePosition(bigramFlags,
            Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE, entryWritingPos)) {
        AKLOGE("Cannot write bigram flags. pos: %d, flags: %x", *entryWritingPos, bigramFlags);
        return false;
    }
    if (mHasHistoricalInfo) {
        if (!bigramListBuffer->writeUintAndAdvancePosition(timestamp,
                Ver4DictConstants::TIME_STAMP_FIELD_SIZE, entryWritingPos)) {
            AKLOGE("Cannot write bigram timestamps. pos: %d, timestamp: %d", *entryWritingPos,
                    timestamp);
            return false;
        }
        if (!bigramListBuffer->writeUintAndAdvancePosition(level,
                Ver4DictConstants::WORD_LEVEL_FIELD_SIZE, entryWritingPos)) {
            AKLOGE("Cannot write bigram level. pos: %d, level: %d", *entryWritingPos,
                    level);
            return false;
        }
        if (!bigramListBuffer->writeUintAndAdvancePosition(count,
                Ver4DictConstants::WORD_COUNT_FIELD_SIZE, entryWritingPos)) {
            AKLOGE("Cannot write bigram count. pos: %d, count: %d", *entryWritingPos,
                    count);
            return false;
        }
    } else {
        if (!bigramListBuffer->writeUintAndAdvancePosition(probability,
                Ver4DictConstants::PROBABILITY_SIZE, entryWritingPos)) {
            AKLOGE("Cannot write bigram probability. pos: %d, probability: %d", *entryWritingPos,
                    probability);
            return false;
        }
    }
    const int targetTerminalIdToWrite = (targetTerminalId == Ver4DictConstants::NOT_A_TERMINAL_ID) ?
            Ver4DictConstants::INVALID_BIGRAM_TARGET_TERMINAL_ID : targetTerminalId;
    if (!bigramListBuffer->writeUintAndAdvancePosition(targetTerminalIdToWrite,
            Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE, entryWritingPos)) {
        AKLOGE("Cannot write bigram target terminal id. pos: %d, target terminal id: %d",
                *entryWritingPos, targetTerminalId);
        return false;
    }
    return true;
}

bool BigramDictContent::writeLink(const int linkedEntryPos, const int writingPos) {
    const int targetTerminalId = linkedEntryPos;
    int pos = writingPos;
    return writeBigramEntryAttributesAndAdvancePosition(true /* isLink */,
            NOT_A_PROBABILITY /* probability */, targetTerminalId, NOT_A_TIMESTAMP, 0 /* level */,
            0 /* count */, &pos);
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
        int *const outEntryCount) {
    bool hasNext = true;
    int readingPos = bigramListPos;
    int writingPos = toPos;
    while (hasNext) {
        const BigramEntry originalBigramEntry =
                sourceBigramDictContent->getBigramEntryAndAdvancePosition(&readingPos);
        hasNext = originalBigramEntry.hasNext();
        if (!originalBigramEntry.isValid()) {
            continue;
        }
        TerminalPositionLookupTable::TerminalIdMap::const_iterator it =
                terminalIdMap->find(originalBigramEntry.getTargetTerminalId());
        if (it == terminalIdMap->end()) {
            // Target word has been removed.
            continue;
        }
        const BigramEntry updatedBigramEntry =
                originalBigramEntry.updateTargetTerminalIdAndGetEntry(it->second);
        if (!writeBigramEntryAndAdvancePosition(&updatedBigramEntry, &writingPos)) {
            AKLOGE("Cannot write bigram entry to run GC. pos: %d", writingPos);
            return false;
        }
        *outEntryCount += 1;
    }
    if (*outEntryCount > 0) {
        if (!writeTerminator(writingPos)) {
            AKLOGE("Cannot write terminator to run GC. pos: %d", writingPos);
            return false;
        }
    }
    return true;
}

} // namespace latinime
