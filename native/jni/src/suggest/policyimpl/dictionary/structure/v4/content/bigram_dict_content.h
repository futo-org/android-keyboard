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

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_entry.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/sparse_table_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"

namespace latinime {

class BigramDictContent : public SparseTableDictContent {
 public:
    BigramDictContent(const char *const dictPath, const bool hasHistoricalInfo,
            const bool isUpdatable)
            : SparseTableDictContent(dictPath,
                      Ver4DictConstants::BIGRAM_LOOKUP_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::BIGRAM_CONTENT_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::BIGRAM_FILE_EXTENSION, isUpdatable,
                      Ver4DictConstants::BIGRAM_ADDRESS_TABLE_BLOCK_SIZE,
                      Ver4DictConstants::BIGRAM_ADDRESS_TABLE_DATA_SIZE),
              mHasHistoricalInfo(hasHistoricalInfo) {}

    BigramDictContent(const bool hasHistoricalInfo)
            : SparseTableDictContent(Ver4DictConstants::BIGRAM_ADDRESS_TABLE_BLOCK_SIZE,
                      Ver4DictConstants::BIGRAM_ADDRESS_TABLE_DATA_SIZE),
              mHasHistoricalInfo(hasHistoricalInfo) {}

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

    bool writeBigramEntry(const BigramEntry *const bigramEntryToWrite, const int entryWritingPos) {
        int writingPos = entryWritingPos;
        return writeBigramEntryAndAdvancePosition(bigramEntryToWrite, &writingPos);
    }

    bool writeBigramEntryAndAdvancePosition(const BigramEntry *const bigramEntryToWrite,
            int *const entryWritingPos);

    bool createNewBigramList(const int terminalId) {
        const int bigramListPos = getContentBuffer()->getTailPosition();
        return getUpdatableAddressLookupTable()->set(terminalId, bigramListPos);
    }

    bool copyBigramList(const int bigramListPos, const int toPos);

    bool flushToFile(const char *const dictPath) const {
        return flush(dictPath, Ver4DictConstants::BIGRAM_LOOKUP_TABLE_FILE_EXTENSION,
                Ver4DictConstants::BIGRAM_CONTENT_TABLE_FILE_EXTENSION,
                Ver4DictConstants::BIGRAM_FILE_EXTENSION);
    }

    bool runGC(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            const BigramDictContent *const originalBigramDictContent,
            int *const outBigramEntryCount);

 private:
    DISALLOW_COPY_AND_ASSIGN(BigramDictContent);

    int createAndGetBigramFlags(const int probability, const bool hasNext) const {
        return (probability & Ver4DictConstants::BIGRAM_PROBABILITY_MASK)
                | (hasNext ? Ver4DictConstants::BIGRAM_HAS_NEXT_MASK : 0);
    }

    bool runGCBigramList(const int bigramListPos,
            const BigramDictContent *const sourceBigramDictContent, const int toPos,
            const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            int *const outEntryCount);

    bool mHasHistoricalInfo;
};
} // namespace latinime
#endif /* LATINIME_BIGRAM_DICT_CONTENT_H */
