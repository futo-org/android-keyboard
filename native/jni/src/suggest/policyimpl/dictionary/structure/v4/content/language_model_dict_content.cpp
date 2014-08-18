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

} // namespace latinime
