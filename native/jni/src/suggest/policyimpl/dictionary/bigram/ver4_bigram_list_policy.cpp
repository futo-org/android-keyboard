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
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

void Ver4BigramListPolicy::getNextBigram(int *const outBigramPos, int *const outProbability,
        bool *const outHasNext, int *const bigramEntryPos) const {
    const BigramEntry bigramEntry =
            mBigramDictContent->getBigramEntryAndAdvancePosition(bigramEntryPos);
    if (outBigramPos) {
        // Lookup target PtNode position.
        *outBigramPos = mTerminalPositionLookupTable->getTerminalPtNodePosition(
                bigramEntry.getTargetTerminalId());
    }
    if (outProbability) {
        if (bigramEntry.hasHistoricalInfo()) {
            *outProbability =
                    ForgettingCurveUtils::decodeProbability(bigramEntry.getHistoricalInfo());
        } else {
            *outProbability = bigramEntry.getProbability();
        }
    }
    if (outHasNext) {
        *outHasNext = bigramEntry.hasNext();
    }
}

bool Ver4BigramListPolicy::addNewEntry(const int terminalId, const int newTargetTerminalId,
        const int newProbability, const int timestamp, bool *const outAddedNewEntry) {
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
        const BigramEntry newBigramEntry(false /* hasNext */, NOT_A_PROBABILITY,
                newTargetTerminalId);
        const BigramEntry bigramEntryToWrite = createUpdatedBigramEntryFrom(&newBigramEntry,
                newProbability, timestamp);
        // Write an entry.
        const int writingPos =  mBigramDictContent->getBigramListHeadPos(terminalId);
        if (!mBigramDictContent->writeBigramEntry(&bigramEntryToWrite, writingPos)) {
            return false;
        }
        if (outAddedNewEntry) {
            *outAddedNewEntry = true;
        }
        return true;
    }

    const int entryPosToUpdate = getEntryPosToUpdate(newTargetTerminalId, bigramListPos);
    if (entryPosToUpdate != NOT_A_DICT_POS) {
        // Overwrite existing entry.
        const BigramEntry originalBigramEntry =
                mBigramDictContent->getBigramEntry(entryPosToUpdate);
        if (!originalBigramEntry.isValid()) {
            // Reuse invalid entry.
            if (outAddedNewEntry) {
                *outAddedNewEntry = true;
            }
        }
        const BigramEntry updatedBigramEntry =
                originalBigramEntry.updateTargetTerminalIdAndGetEntry(newTargetTerminalId);
        const BigramEntry bigramEntryToWrite = createUpdatedBigramEntryFrom(
                &updatedBigramEntry, newProbability, timestamp);
        return mBigramDictContent->writeBigramEntry(&bigramEntryToWrite, entryPosToUpdate);
    }

    // Add new entry to the bigram list.
    // Create new bigram list.
    if (!mBigramDictContent->createNewBigramList(terminalId)) {
        return false;
    }
    // Write new entry at a head position of the bigram list.
    int writingPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    const BigramEntry newBigramEntry(true /* hasNext */, NOT_A_PROBABILITY, newTargetTerminalId);
    const BigramEntry bigramEntryToWrite = createUpdatedBigramEntryFrom(
            &newBigramEntry, newProbability, timestamp);
    if (!mBigramDictContent->writeBigramEntryAndAdvancePosition(&bigramEntryToWrite, &writingPos)) {
        return false;
    }
    if (outAddedNewEntry) {
        *outAddedNewEntry = true;
    }
    // Append existing entries by copying.
    return mBigramDictContent->copyBigramList(bigramListPos, writingPos);
}

bool Ver4BigramListPolicy::removeEntry(const int terminalId, const int targetTerminalId) {
    const int bigramListPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (bigramListPos == NOT_A_DICT_POS) {
        // Bigram list doesn't exist.
        return false;
    }
    const int entryPosToUpdate = getEntryPosToUpdate(targetTerminalId, bigramListPos);
    if (entryPosToUpdate == NOT_A_DICT_POS) {
        // Bigram entry doesn't exist.
        return false;
    }
    const BigramEntry bigramEntry = mBigramDictContent->getBigramEntry(entryPosToUpdate);
    if (targetTerminalId != bigramEntry.getTargetTerminalId()) {
        // Bigram entry doesn't exist.
        return false;
    }
    // Remove bigram entry by marking it as invalid entry and overwriting the original entry.
    const BigramEntry updatedBigramEntry = bigramEntry.getInvalidatedEntry();
    return mBigramDictContent->writeBigramEntry(&updatedBigramEntry, entryPosToUpdate);
}

bool Ver4BigramListPolicy::updateAllBigramEntriesAndDeleteUselessEntries(const int terminalId,
        int *const outBigramCount) {
    const int bigramListPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (bigramListPos == NOT_A_DICT_POS) {
        // Bigram list doesn't exist.
        return true;
    }
    bool hasNext = true;
    int readingPos = bigramListPos;
    while (hasNext) {
        const int entryPos = readingPos;
        const BigramEntry bigramEntry =
                mBigramDictContent->getBigramEntryAndAdvancePosition(&readingPos);
        hasNext = bigramEntry.hasNext();
        if (!bigramEntry.isValid()) {
            continue;
        }
        const int targetPtNodePos = mTerminalPositionLookupTable->getTerminalPtNodePosition(
                bigramEntry.getTargetTerminalId());
        if (targetPtNodePos == NOT_A_DICT_POS) {
            // Invalidate bigram entry.
            const BigramEntry updatedBigramEntry = bigramEntry.getInvalidatedEntry();
            if (!mBigramDictContent->writeBigramEntry(&updatedBigramEntry, entryPos)) {
                return false;
            }
        } else if (bigramEntry.hasHistoricalInfo()) {
            const HistoricalInfo historicalInfo = ForgettingCurveUtils::createHistoricalInfoToSave(
                    bigramEntry.getHistoricalInfo());
            if (ForgettingCurveUtils::needsToKeep(&historicalInfo)) {
                const BigramEntry updatedBigramEntry =
                        bigramEntry.updateHistoricalInfoAndGetEntry(&historicalInfo);
                if (!mBigramDictContent->writeBigramEntry(&updatedBigramEntry, entryPos)) {
                    return false;
                }
                *outBigramCount += 1;
            } else {
                // Remove entry.
                const BigramEntry updatedBigramEntry = bigramEntry.getInvalidatedEntry();
                if (!mBigramDictContent->writeBigramEntry(&updatedBigramEntry, entryPos)) {
                    return false;
                }
            }
        } else {
            *outBigramCount += 1;
        }
    }
    return true;
}

int Ver4BigramListPolicy::getBigramEntryConut(const int terminalId) {
    const int bigramListPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (bigramListPos == NOT_A_DICT_POS) {
        // Bigram list doesn't exist.
        return 0;
    }
    int bigramCount = 0;
    bool hasNext = true;
    int readingPos = bigramListPos;
    while (hasNext) {
        const BigramEntry bigramEntry =
                mBigramDictContent->getBigramEntryAndAdvancePosition(&readingPos);
        hasNext = bigramEntry.hasNext();
        if (bigramEntry.isValid()) {
            bigramCount++;
        }
    }
    return bigramCount;
}

int Ver4BigramListPolicy::getEntryPosToUpdate(const int targetTerminalIdToFind,
        const int bigramListPos) const {
    bool hasNext = true;
    int invalidEntryPos = NOT_A_DICT_POS;
    int readingPos = bigramListPos;
    while (hasNext) {
        const int entryPos = readingPos;
        const BigramEntry bigramEntry =
                mBigramDictContent->getBigramEntryAndAdvancePosition(&readingPos);
        hasNext = bigramEntry.hasNext();
        if (bigramEntry.getTargetTerminalId() == targetTerminalIdToFind) {
            // Entry with same target is found.
            return entryPos;
        } else if (!bigramEntry.isValid()) {
            // Invalid entry that can be reused is found.
            invalidEntryPos = entryPos;
        }
    }
    return invalidEntryPos;
}

const BigramEntry Ver4BigramListPolicy::createUpdatedBigramEntryFrom(
        const BigramEntry *const originalBigramEntry, const int newProbability,
        const int timestamp) const {
    // TODO: Consolidate historical info and probability.
    if (mHeaderPolicy->hasHistoricalInfoOfWords()) {
        const HistoricalInfo updatedHistoricalInfo =
                ForgettingCurveUtils::createUpdatedHistoricalInfo(
                        originalBigramEntry->getHistoricalInfo(), newProbability, timestamp);
        return originalBigramEntry->updateHistoricalInfoAndGetEntry(&updatedHistoricalInfo);
    } else {
        return originalBigramEntry->updateProbabilityAndGetEntry(newProbability);
    }
}

} // namespace latinime
