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

#ifndef LATINIME_BIGRAM_DICT_CONTENT_H
#define LATINIME_BIGRAM_DICT_CONTENT_H

#include <cstdint>
#include <cstdio>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_entry.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/sparse_table_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"

namespace latinime {

class BigramDictContent : public SparseTableDictContent {
 public:
    BigramDictContent(uint8_t *const *buffers, const int *bufferSizes, const bool hasHistoricalInfo)
            : SparseTableDictContent(buffers, bufferSizes,
                      Ver4DictConstants::BIGRAM_ADDRESS_TABLE_BLOCK_SIZE,
                      Ver4DictConstants::BIGRAM_ADDRESS_TABLE_DATA_SIZE),
              mHasHistoricalInfo(hasHistoricalInfo) {}

    BigramDictContent(const bool hasHistoricalInfo)
            : SparseTableDictContent(Ver4DictConstants::BIGRAM_ADDRESS_TABLE_BLOCK_SIZE,
                      Ver4DictConstants::BIGRAM_ADDRESS_TABLE_DATA_SIZE),
              mHasHistoricalInfo(hasHistoricalInfo) {}

    int getContentTailPos() const {
        return getContentBuffer()->getTailPosition();
    }

    const BigramEntry getBigramEntry(const int bigramEntryPos) const {
        int readingPos = bigramEntryPos;
        return getBigramEntryAndAdvancePosition(&readingPos);
    }

    const BigramEntry getBigramEntryAndAdvancePosition(int *const bigramEntryPos) const;

    // Returns head position of bigram list for a PtNode specified by terminalId.
    int getBigramListHeadPos(const int terminalId) const {
        const SparseTable *const addressLookupTable = getAddressLookupTable();
        if (!addressLookupTable->contains(terminalId)) {
            return NOT_A_DICT_POS;
        }
        return addressLookupTable->get(terminalId);
    }

    bool writeBigramEntryAtTail(const BigramEntry *const bigramEntryToWrite) {
        int writingPos = getContentBuffer()->getTailPosition();
        return writeBigramEntryAndAdvancePosition(bigramEntryToWrite, &writingPos);
    }

    bool writeBigramEntry(const BigramEntry *const bigramEntryToWrite, const int entryWritingPos) {
        int writingPos = entryWritingPos;
        return writeBigramEntryAndAdvancePosition(bigramEntryToWrite, &writingPos);
    }

    bool writeBigramEntryAndAdvancePosition(const BigramEntry *const bigramEntryToWrite,
            int *const entryWritingPos);

    bool writeTerminator(const int writingPos) {
        // Terminator is a link to the invalid position.
        return writeLink(INVALID_LINKED_ENTRY_POS, writingPos);
    }

    bool writeLink(const int linkedPos, const int writingPos);

    bool createNewBigramList(const int terminalId) {
        const int bigramListPos = getContentBuffer()->getTailPosition();
        return getUpdatableAddressLookupTable()->set(terminalId, bigramListPos);
    }

    bool flushToFile(FILE *const file) const {
        return flush(file);
    }

    bool runGC(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            const BigramDictContent *const originalBigramDictContent,
            int *const outBigramEntryCount);

    int getBigramEntrySize() const {
        if (mHasHistoricalInfo) {
            return Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE
                    + Ver4DictConstants::TIME_STAMP_FIELD_SIZE
                    + Ver4DictConstants::WORD_LEVEL_FIELD_SIZE
                    + Ver4DictConstants::WORD_COUNT_FIELD_SIZE
                    + Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE;
        } else {
            return Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE
                    + Ver4DictConstants::PROBABILITY_SIZE
                    + Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE;
        }
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(BigramDictContent);

    static const int INVALID_LINKED_ENTRY_POS;

    bool writeBigramEntryAttributesAndAdvancePosition(
            const bool isLink, const int probability, const int targetTerminalId,
            const int timestamp, const int level, const int count, int *const entryWritingPos);

    bool runGCBigramList(const int bigramListPos,
            const BigramDictContent *const sourceBigramDictContent, const int toPos,
            const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            int *const outEntryCount);

    bool mHasHistoricalInfo;
};
} // namespace latinime
#endif /* LATINIME_BIGRAM_DICT_CONTENT_H */
