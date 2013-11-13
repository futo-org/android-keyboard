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

#include "suggest/policyimpl/dictionary/bigram/ver4_bigram_list_policy.h"

#include "suggest/policyimpl/dictionary/bigram/bigram_list_read_write_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"

namespace latinime {

void Ver4BigramListPolicy::getNextBigram(int *const outBigramPos, int *const outProbability,
        bool *const outHasNext, int *const bigramEntryPos) const {
    int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
    mBigramDictContent->getBigramEntryAndAdvancePosition(outProbability, outHasNext,
            &targetTerminalId, bigramEntryPos);
    if (outBigramPos) {
        // Lookup target PtNode position.
        *outBigramPos = mTerminalPositionLookupTable->getTerminalPtNodePosition(targetTerminalId);
    }
}

bool Ver4BigramListPolicy::addNewEntry(const int terminalId, const int newTargetTerminalId,
        const int newProbability, bool *const outAddedNewEntry) {
    if (outAddedNewEntry) {
        *outAddedNewEntry = false;
    }
    const int bigramListPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (bigramListPos == NOT_A_DICT_POS) {
        // Updating PtNode doesn't have a bigram list.
        // Create new bigram list.
        if (!mBigramDictContent->createNewBigramList(terminalId)) {
            return false;
        }
        // Write an entry.
        int writingPos =  mBigramDictContent->getBigramListHeadPos(terminalId);
        if (!mBigramDictContent->writeBigramEntryAndAdvancePosition(newProbability,
                false /* hasNext */, newTargetTerminalId, &writingPos)) {
            return false;
        }
        return true;
    }

    const int entryPosToUpdate = getEntryPosToUpdate(newTargetTerminalId, bigramListPos);
    if (entryPosToUpdate != NOT_A_DICT_POS) {
        // Overwrite existing entry.
        int readingPos = entryPosToUpdate;
        bool hasNext = false;
        int probability = NOT_A_PROBABILITY;
        int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
        mBigramDictContent->getBigramEntryAndAdvancePosition(&probability, &hasNext,
                &targetTerminalId, &readingPos);
        if (targetTerminalId == Ver4DictConstants::NOT_A_TERMINAL_ID && outAddedNewEntry) {
            // Reuse invalid entry.
            *outAddedNewEntry = true;
        }
        int writingPos = entryPosToUpdate;
        return mBigramDictContent->writeBigramEntryAndAdvancePosition(newProbability, hasNext,
                newTargetTerminalId, &writingPos);
    }

    // Add new entry to the bigram list.
    // Create new bigram list.
    if (!mBigramDictContent->createNewBigramList(terminalId)) {
        return false;
    }
    // Write new entry at a head position of the bigram list.
    int writingPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (!mBigramDictContent->writeBigramEntryAndAdvancePosition(newProbability,
            true /* hasNext */, newTargetTerminalId, &writingPos)) {
        return false;
    }
    if (outAddedNewEntry) {
        *outAddedNewEntry = true;
    }
    // Append existing entries by copying.
    return mBigramDictContent->copyBigramList(bigramListPos, writingPos);
}

int Ver4BigramListPolicy::getEntryPosToUpdate(const int targetTerminalIdToFind,
        const int bigramListPos) const {
    bool hasNext = true;
    int invalidEntryPos = NOT_A_DICT_POS;
    int readingPos = bigramListPos;
    while(hasNext) {
        const int entryPos = readingPos;
        int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
        mBigramDictContent->getBigramEntryAndAdvancePosition(0 /* probability */, &hasNext,
                &targetTerminalId, &readingPos);
        if (targetTerminalId == targetTerminalIdToFind) {
            // Entry with same target is found.
            return entryPos;
        } else if (targetTerminalId == Ver4DictConstants::NOT_A_TERMINAL_ID) {
            // Invalid entry that can be reused is found.
            invalidEntryPos = entryPos;
        }
    }
    return invalidEntryPos;
}

} // namespace latinime
