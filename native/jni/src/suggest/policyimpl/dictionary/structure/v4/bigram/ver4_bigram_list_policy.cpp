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

#include "suggest/policyimpl/dictionary/structure/v4/bigram/ver4_bigram_list_policy.h"

#include "suggest/core/dictionary/property/bigram_property.h"
#include "suggest/policyimpl/dictionary/header/header_policy.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/bigram/bigram_list_read_write_utils.h"
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
                    ForgettingCurveUtils::decodeProbability(bigramEntry.getHistoricalInfo(),
                            mHeaderPolicy);
        } else {
            *outProbability = bigramEntry.getProbability();
        }
    }
    if (outHasNext) {
        *outHasNext = bigramEntry.hasNext();
    }
}

bool Ver4BigramListPolicy::addNewEntry(const int terminalId, const int newTargetTerminalId,
        const BigramProperty *const bigramProperty, bool *const outAddedNewEntry) {
    // 1. The word has no bigrams yet.
    // 2. The word has bigrams, and there is the target in the list.
    // 3. The word has bigrams, and there is an invalid entry that can be reclaimed.
    // 4. The word has bigrams. We have to append new bigram entry to the list.
    // 5. Same as 4, but the list is the last entry of the content file.
    if (outAddedNewEntry) {
        *outAddedNewEntry = false;
    }
    const int bigramListPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (bigramListPos == NOT_A_DICT_POS) {
        // Case 1. PtNode that doesn't have a bigram list.
        // Create new bigram list.
        if (!mBigramDictContent->createNewBigramList(terminalId)) {
            return false;
        }
        const BigramEntry newBigramEntry(false /* hasNext */, NOT_A_PROBABILITY,
                newTargetTerminalId);
        const BigramEntry bigramEntryToWrite = createUpdatedBigramEntryFrom(&newBigramEntry,
                bigramProperty);
        // Write an entry.
        int writingPos =  mBigramDictContent->getBigramListHeadPos(terminalId);
        if (!mBigramDictContent->writeBigramEntryAndAdvancePosition(&bigramEntryToWrite,
                &writingPos)) {
            AKLOGE("Cannot write bigram entry. pos: %d.", writingPos);
            return false;
        }
        if (!mBigramDictContent->writeTerminator(writingPos)) {
            AKLOGE("Cannot write bigram list terminator. pos: %d.", writingPos);
            return false;
        }
        if (outAddedNewEntry) {
            *outAddedNewEntry = true;
        }
        return true;
    }

    int tailEntryPos = NOT_A_DICT_POS;
    const int entryPosToUpdate = getEntryPosToUpdate(newTargetTerminalId, bigramListPos,
            &tailEntryPos);
    if (entryPosToUpdate == NOT_A_DICT_POS) {
        // Case 4, 5. Add new entry to the bigram list.
        const int contentTailPos = mBigramDictContent->getContentTailPos();
        // If the tail entry is at the tail of content buffer, the new entry can be written without
        // link (Case 5).
        const bool canAppendEntry =
                contentTailPos == tailEntryPos + mBigramDictContent->getBigramEntrySize();
        const int newEntryPos = canAppendEntry ? tailEntryPos : contentTailPos;
        int writingPos = newEntryPos;
        // Write new entry at the tail position of the bigram content.
        const BigramEntry newBigramEntry(false /* hasNext */, NOT_A_PROBABILITY,
                newTargetTerminalId);
        const BigramEntry bigramEntryToWrite = createUpdatedBigramEntryFrom(
                &newBigramEntry, bigramProperty);
        if (!mBigramDictContent->writeBigramEntryAndAdvancePosition(&bigramEntryToWrite,
                &writingPos)) {
            AKLOGE("Cannot write bigram entry. pos: %d.", writingPos);
            return false;
        }
        if (!mBigramDictContent->writeTerminator(writingPos)) {
            AKLOGE("Cannot write bigram list terminator. pos: %d.", writingPos);
            return false;
        }
        if (!canAppendEntry) {
            // Update link of the current tail entry.
            if (!mBigramDictContent->writeLink(newEntryPos, tailEntryPos)) {
                AKLOGE("Cannot update bigram entry link. pos: %d, linked entry pos: %d.",
                        tailEntryPos, newEntryPos);
                return false;
            }
        }
        if (outAddedNewEntry) {
            *outAddedNewEntry = true;
        }
        return true;
    }

    // Case 2. Overwrite the existing entry. Case 3. Reclaim and reuse the existing invalid entry.
    const BigramEntry originalBigramEntry = mBigramDictContent->getBigramEntry(entryPosToUpdate);
    if (!originalBigramEntry.isValid()) {
        // Case 3. Reuse the existing invalid entry. outAddedNewEntry is false when an existing
        // entry is updated.
        if (outAddedNewEntry) {
            *outAddedNewEntry = true;
        }
    }
    const BigramEntry updatedBigramEntry =
            originalBigramEntry.updateTargetTerminalIdAndGetEntry(newTargetTerminalId);
    const BigramEntry bigramEntryToWrite = createUpdatedBigramEntryFrom(
            &updatedBigramEntry, bigramProperty);
    return mBigramDictContent->writeBigramEntry(&bigramEntryToWrite, entryPosToUpdate);
}

bool Ver4BigramListPolicy::removeEntry(const int terminalId, const int targetTerminalId) {
    const int bigramListPos = mBigramDictContent->getBigramListHeadPos(terminalId);
    if (bigramListPos == NOT_A_DICT_POS) {
        // Bigram list doesn't exist.
        return false;
    }
    const int entryPosToUpdate = getEntryPosToUpdate(targetTerminalId, bigramListPos,
            nullptr /* outTailEntryPos */);
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
        const BigramEntry bigramEntry =
                mBigramDictContent->getBigramEntryAndAdvancePosition(&readingPos);
        const int entryPos = readingPos - mBigramDictContent->getBigramEntrySize();
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
                    bigramEntry.getHistoricalInfo(), mHeaderPolicy);
            if (ForgettingCurveUtils::needsToKeep(&historicalInfo, mHeaderPolicy)) {
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
        const int bigramListPos, int *const outTailEntryPos) const {
    if (outTailEntryPos) {
        *outTailEntryPos = NOT_A_DICT_POS;
    }
    int invalidEntryPos = NOT_A_DICT_POS;
    int readingPos = bigramListPos;
    while (true) {
        const BigramEntry bigramEntry =
                mBigramDictContent->getBigramEntryAndAdvancePosition(&readingPos);
        const int entryPos = readingPos - mBigramDictContent->getBigramEntrySize();
        if (!bigramEntry.hasNext()) {
            if (outTailEntryPos) {
                *outTailEntryPos = entryPos;
            }
            break;
        }
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
        const BigramEntry *const originalBigramEntry,
        const BigramProperty *const bigramProperty) const {
    // TODO: Consolidate historical info and probability.
    if (mHeaderPolicy->hasHistoricalInfoOfWords()) {
        const HistoricalInfo historicalInfoForUpdate(bigramProperty->getTimestamp(),
                bigramProperty->getLevel(), bigramProperty->getCount());
        const HistoricalInfo updatedHistoricalInfo =
                ForgettingCurveUtils::createUpdatedHistoricalInfo(
                        originalBigramEntry->getHistoricalInfo(), bigramProperty->getProbability(),
                        &historicalInfoForUpdate, mHeaderPolicy);
        return originalBigramEntry->updateHistoricalInfoAndGetEntry(&updatedHistoricalInfo);
    } else {
        return originalBigramEntry->updateProbabilityAndGetEntry(bigramProperty->getProbability());
    }
}

} // namespace latinime
