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

#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

int TerminalPositionLookupTable::getTerminalPtNodePosition(const int terminalId) const {
    if (terminalId < 0 || terminalId >= mSize) {
        return NOT_A_DICT_POS;
    }
    const int terminalPos = getBuffer()->readUint(
            Ver4DictConstants::TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE, getEntryPos(terminalId));
    return (terminalPos == Ver4DictConstants::NOT_A_TERMINAL_ADDRESS) ?
            NOT_A_DICT_POS : terminalPos - mHeaderRegionSize;
}

bool TerminalPositionLookupTable::setTerminalPtNodePosition(
        const int terminalId, const int terminalPtNodePos) {
    if (terminalId < 0) {
        return NOT_A_DICT_POS;
    }
    while (terminalId >= mSize) {
        // Write new entry.
        if (!getWritableBuffer()->writeUint(Ver4DictConstants::NOT_A_TERMINAL_ADDRESS,
                Ver4DictConstants::TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE, getEntryPos(mSize))) {
            return false;
        }
        mSize++;
    }
    const int terminalPos = (terminalPtNodePos != NOT_A_DICT_POS) ?
            terminalPtNodePos + mHeaderRegionSize : Ver4DictConstants::NOT_A_TERMINAL_ADDRESS;
    return getWritableBuffer()->writeUint(terminalPos,
            Ver4DictConstants::TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE, getEntryPos(terminalId));
}

bool TerminalPositionLookupTable::flushToFile(const char *const dictDirPath,
        const int newHeaderRegionSize) const {
    const int headerRegionSizeDiff = newHeaderRegionSize - mHeaderRegionSize;
    // If header region size has been changed, terminal PtNode positions have to be adjusted
    // depending on the new header region size.
    if (headerRegionSizeDiff != 0) {
        TerminalPositionLookupTable lookupTableToWrite;
        for (int i = 0; i < mSize; ++i) {
            const int terminalPtNodePosition = getTerminalPtNodePosition(i)
                    + headerRegionSizeDiff;
            if (!lookupTableToWrite.setTerminalPtNodePosition(i, terminalPtNodePosition)) {
                AKLOGE("Cannot set terminal position to lookupTableToWrite."
                        " terminalId: %d, position: %d", i, terminalPtNodePosition);
                return false;
            }
        }
        return lookupTableToWrite.flush(dictDirPath,
                Ver4DictConstants::TERMINAL_ADDRESS_TABLE_FILE_EXTENSION);
    } else {
        // We can simply use this lookup table because the header region size has not been
        // changed.
        return flush(dictDirPath, Ver4DictConstants::TERMINAL_ADDRESS_TABLE_FILE_EXTENSION);
    }
}

bool TerminalPositionLookupTable::runGCTerminalIds(TerminalIdMap *const terminalIdMap) {
    int removedEntryCount = 0;
    int nextNewTerminalId = 0;
    for (int i = 0; i < mSize; ++i) {
        const int terminalPos = getBuffer()->readUint(
                Ver4DictConstants::TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE, getEntryPos(i));
        if (terminalPos == Ver4DictConstants::NOT_A_TERMINAL_ADDRESS) {
            // This entry is a garbage.
            removedEntryCount++;
        } else {
            // Give a new terminal id to the entry.
            if (!getWritableBuffer()->writeUint(terminalPos,
                    Ver4DictConstants::TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE,
                    getEntryPos(nextNewTerminalId))) {
                return false;
            }
            // Memorize the mapping to the old terminal id to the new terminal id.
            terminalIdMap->insert(TerminalIdMap::value_type(i, nextNewTerminalId));
            nextNewTerminalId++;
        }
    }
    mSize = nextNewTerminalId;
    return true;
}

} // namespace latinime
