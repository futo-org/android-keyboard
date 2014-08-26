/*
 * Copyright (C) 2014, The Android Open Source Project
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

#include "suggest/policyimpl/dictionary/structure/v4/content/language_model_dict_content.h"

#include <algorithm>
#include <cstring>

#include "suggest/policyimpl/dictionary/utils/forgetting_curve_utils.h"

namespace latinime {

bool LanguageModelDictContent::save(FILE *const file) const {
    return mTrieMap.save(file);
}

bool LanguageModelDictContent::runGC(
        const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        const LanguageModelDictContent *const originalContent,
        int *const outNgramCount) {
    return runGCInner(terminalIdMap, originalContent->mTrieMap.getEntriesInRootLevel(),
            0 /* nextLevelBitmapEntryIndex */, outNgramCount);
}

ProbabilityEntry LanguageModelDictContent::getNgramProbabilityEntry(
        const WordIdArrayView prevWordIds, const int wordId) const {
    const int bitmapEntryIndex = getBitmapEntryIndex(prevWordIds);
    if (bitmapEntryIndex == TrieMap::INVALID_INDEX) {
        return ProbabilityEntry();
    }
    const TrieMap::Result result = mTrieMap.get(wordId, bitmapEntryIndex);
    if (!result.mIsValid) {
        // Not found.
        return ProbabilityEntry();
    }
    return ProbabilityEntry::decode(result.mValue, mHasHistoricalInfo);
}

bool LanguageModelDictContent::setNgramProbabilityEntry(const WordIdArrayView prevWordIds,
        const int wordId, const ProbabilityEntry *const probabilityEntry) {
    if (wordId == Ver4DictConstants::NOT_A_TERMINAL_ID) {
        return false;
    }
    const int bitmapEntryIndex = createAndGetBitmapEntryIndex(prevWordIds);
    if (bitmapEntryIndex == TrieMap::INVALID_INDEX) {
        return false;
    }
    return mTrieMap.put(wordId, probabilityEntry->encode(mHasHistoricalInfo), bitmapEntryIndex);
}

bool LanguageModelDictContent::removeNgramProbabilityEntry(const WordIdArrayView prevWordIds,
        const int wordId) {
    const int bitmapEntryIndex = getBitmapEntryIndex(prevWordIds);
    if (bitmapEntryIndex == TrieMap::INVALID_INDEX) {
        // Cannot find bitmap entry for the probability entry. The entry doesn't exist.
        return false;
    }
    return mTrieMap.remove(wordId, bitmapEntryIndex);
}

LanguageModelDictContent::EntryRange LanguageModelDictContent::getProbabilityEntries(
        const WordIdArrayView prevWordIds) const {
    const int bitmapEntryIndex = getBitmapEntryIndex(prevWordIds);
    return EntryRange(mTrieMap.getEntriesInSpecifiedLevel(bitmapEntryIndex), mHasHistoricalInfo);
}

bool LanguageModelDictContent::truncateEntries(const int *const entryCounts,
        const int *const maxEntryCounts, const HeaderPolicy *const headerPolicy) {
    for (int i = 0; i <= MAX_PREV_WORD_COUNT_FOR_N_GRAM; ++i) {
        if (entryCounts[i] <= maxEntryCounts[i]) {
            continue;
        }
        if (!turncateEntriesInSpecifiedLevel(headerPolicy, maxEntryCounts[i], i)) {
            return false;
        }
    }
    return true;
}

bool LanguageModelDictContent::runGCInner(
        const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        const TrieMap::TrieMapRange trieMapRange,
        const int nextLevelBitmapEntryIndex, int *const outNgramCount) {
    for (auto &entry : trieMapRange) {
        const auto it = terminalIdMap->find(entry.key());
        if (it == terminalIdMap->end() || it->second == Ver4DictConstants::NOT_A_TERMINAL_ID) {
            // The word has been removed.
            continue;
        }
        if (!mTrieMap.put(it->second, entry.value(), nextLevelBitmapEntryIndex)) {
            return false;
        }
        if (outNgramCount) {
            *outNgramCount += 1;
        }
        if (entry.hasNextLevelMap()) {
            if (!runGCInner(terminalIdMap, entry.getEntriesInNextLevel(),
                    mTrieMap.getNextLevelBitmapEntryIndex(it->second, nextLevelBitmapEntryIndex),
                    outNgramCount)) {
                return false;
            }
        }
    }
    return true;
}

int LanguageModelDictContent::createAndGetBitmapEntryIndex(const WordIdArrayView prevWordIds) {
    if (prevWordIds.empty()) {
        return mTrieMap.getRootBitmapEntryIndex();
    }
    const int lastBitmapEntryIndex =
            getBitmapEntryIndex(prevWordIds.limit(prevWordIds.size() - 1));
    if (lastBitmapEntryIndex == TrieMap::INVALID_INDEX) {
        return TrieMap::INVALID_INDEX;
    }
    return mTrieMap.getNextLevelBitmapEntryIndex(prevWordIds[prevWordIds.size() - 1],
            lastBitmapEntryIndex);
}

int LanguageModelDictContent::getBitmapEntryIndex(const WordIdArrayView prevWordIds) const {
    int bitmapEntryIndex = mTrieMap.getRootBitmapEntryIndex();
    for (const int wordId : prevWordIds) {
        const TrieMap::Result result = mTrieMap.get(wordId, bitmapEntryIndex);
        if (!result.mIsValid) {
            return TrieMap::INVALID_INDEX;
        }
        bitmapEntryIndex = result.mNextLevelBitmapEntryIndex;
    }
    return bitmapEntryIndex;
}

bool LanguageModelDictContent::updateAllProbabilityEntriesInner(const int bitmapEntryIndex,
        const int level, const HeaderPolicy *const headerPolicy, int *const outEntryCounts) {
    for (const auto &entry : mTrieMap.getEntriesInSpecifiedLevel(bitmapEntryIndex)) {
        if (level > MAX_PREV_WORD_COUNT_FOR_N_GRAM) {
            AKLOGE("Invalid level. level: %d, MAX_PREV_WORD_COUNT_FOR_N_GRAM: %d.",
                    level, MAX_PREV_WORD_COUNT_FOR_N_GRAM);
            return false;
        }
        const ProbabilityEntry probabilityEntry =
                ProbabilityEntry::decode(entry.value(), mHasHistoricalInfo);
        if (mHasHistoricalInfo && !probabilityEntry.representsBeginningOfSentence()) {
            const HistoricalInfo historicalInfo = ForgettingCurveUtils::createHistoricalInfoToSave(
                    probabilityEntry.getHistoricalInfo(), headerPolicy);
            if (ForgettingCurveUtils::needsToKeep(&historicalInfo, headerPolicy)) {
                // Update the entry.
                const ProbabilityEntry updatedEntry(probabilityEntry.getFlags(), &historicalInfo);
                if (!mTrieMap.put(entry.key(), updatedEntry.encode(mHasHistoricalInfo),
                        bitmapEntryIndex)) {
                    return false;
                }
            } else {
                // Remove the entry.
                if (!mTrieMap.remove(entry.key(), bitmapEntryIndex)) {
                    return false;
                }
                continue;
            }
        }
        if (!probabilityEntry.representsBeginningOfSentence()) {
            outEntryCounts[level] += 1;
        }
        if (!entry.hasNextLevelMap()) {
            continue;
        }
        if (!updateAllProbabilityEntriesInner(entry.getNextLevelBitmapEntryIndex(), level + 1,
                headerPolicy, outEntryCounts)) {
            return false;
        }
    }
    return true;
}

bool LanguageModelDictContent::turncateEntriesInSpecifiedLevel(
        const HeaderPolicy *const headerPolicy, const int maxEntryCount, const int targetLevel) {
    std::vector<int> prevWordIds;
    std::vector<EntryInfoToTurncate> entryInfoVector;
    if (!getEntryInfo(headerPolicy, targetLevel, mTrieMap.getRootBitmapEntryIndex(),
            &prevWordIds, &entryInfoVector)) {
        return false;
    }
    if (static_cast<int>(entryInfoVector.size()) <= maxEntryCount) {
        return true;
    }
    const int entryCountToRemove = static_cast<int>(entryInfoVector.size()) - maxEntryCount;
    std::partial_sort(entryInfoVector.begin(), entryInfoVector.begin() + entryCountToRemove,
            entryInfoVector.end(),
            EntryInfoToTurncate::Comparator());
    for (int i = 0; i < entryCountToRemove; ++i) {
        const EntryInfoToTurncate &entryInfo = entryInfoVector[i];
        if (!removeNgramProbabilityEntry(
                WordIdArrayView(entryInfo.mPrevWordIds, entryInfo.mEntryLevel), entryInfo.mKey)) {
            return false;
        }
    }
    return true;
}

bool LanguageModelDictContent::getEntryInfo(const HeaderPolicy *const headerPolicy,
        const int targetLevel, const int bitmapEntryIndex,  std::vector<int> *const prevWordIds,
        std::vector<EntryInfoToTurncate> *const outEntryInfo) const {
    const int currentLevel = prevWordIds->size();
    for (const auto &entry : mTrieMap.getEntriesInSpecifiedLevel(bitmapEntryIndex)) {
        if (currentLevel < targetLevel) {
            if (!entry.hasNextLevelMap()) {
                continue;
            }
            prevWordIds->push_back(entry.key());
            if (!getEntryInfo(headerPolicy, targetLevel, entry.getNextLevelBitmapEntryIndex(),
                    prevWordIds, outEntryInfo)) {
                return false;
            }
            prevWordIds->pop_back();
            continue;
        }
        const ProbabilityEntry probabilityEntry =
                ProbabilityEntry::decode(entry.value(), mHasHistoricalInfo);
        const int probability = (mHasHistoricalInfo) ?
                ForgettingCurveUtils::decodeProbability(probabilityEntry.getHistoricalInfo(),
                        headerPolicy) : probabilityEntry.getProbability();
        outEntryInfo->emplace_back(probability,
                probabilityEntry.getHistoricalInfo()->getTimeStamp(),
                entry.key(), targetLevel, prevWordIds->data());
    }
    return true;
}

bool LanguageModelDictContent::EntryInfoToTurncate::Comparator::operator()(
        const EntryInfoToTurncate &left, const EntryInfoToTurncate &right) const {
    if (left.mProbability != right.mProbability) {
        return left.mProbability < right.mProbability;
    }
    if (left.mTimestamp != right.mTimestamp) {
        return left.mTimestamp > right.mTimestamp;
    }
    if (left.mKey != right.mKey) {
        return left.mKey < right.mKey;
    }
    if (left.mEntryLevel != right.mEntryLevel) {
        return left.mEntryLevel > right.mEntryLevel;
    }
    for (int i = 0; i < left.mEntryLevel; ++i) {
        if (left.mPrevWordIds[i] != right.mPrevWordIds[i]) {
            return left.mPrevWordIds[i] < right.mPrevWordIds[i];
        }
    }
    // left and rigth represent the same entry.
    return false;
}

LanguageModelDictContent::EntryInfoToTurncate::EntryInfoToTurncate(const int probability,
        const int timestamp, const int key, const int entryLevel, const int *const prevWordIds)
        : mProbability(probability), mTimestamp(timestamp), mKey(key), mEntryLevel(entryLevel) {
    memmove(mPrevWordIds, prevWordIds, mEntryLevel * sizeof(mPrevWordIds[0]));
}

} // namespace latinime
