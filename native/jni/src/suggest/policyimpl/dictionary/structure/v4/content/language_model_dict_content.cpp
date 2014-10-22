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

const int LanguageModelDictContent::DUMMY_PROBABILITY_FOR_VALID_WORDS = 1;

bool LanguageModelDictContent::save(FILE *const file) const {
    return mTrieMap.save(file);
}

bool LanguageModelDictContent::runGC(
        const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        const LanguageModelDictContent *const originalContent) {
    return runGCInner(terminalIdMap, originalContent->mTrieMap.getEntriesInRootLevel(),
            0 /* nextLevelBitmapEntryIndex */);
}

const WordAttributes LanguageModelDictContent::getWordAttributes(const WordIdArrayView prevWordIds,
        const int wordId, const HeaderPolicy *const headerPolicy) const {
    int bitmapEntryIndices[MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1];
    bitmapEntryIndices[0] = mTrieMap.getRootBitmapEntryIndex();
    int maxPrevWordCount = 0;
    for (size_t i = 0; i < prevWordIds.size(); ++i) {
        const int nextBitmapEntryIndex =
                mTrieMap.get(prevWordIds[i], bitmapEntryIndices[i]).mNextLevelBitmapEntryIndex;
        if (nextBitmapEntryIndex == TrieMap::INVALID_INDEX) {
            break;
        }
        maxPrevWordCount = i + 1;
        bitmapEntryIndices[i + 1] = nextBitmapEntryIndex;
    }

    for (int i = maxPrevWordCount; i >= 0; --i) {
        const TrieMap::Result result = mTrieMap.get(wordId, bitmapEntryIndices[i]);
        if (!result.mIsValid) {
            continue;
        }
        const ProbabilityEntry probabilityEntry =
                ProbabilityEntry::decode(result.mValue, mHasHistoricalInfo);
        int probability = NOT_A_PROBABILITY;
        if (mHasHistoricalInfo) {
            const int rawProbability = ForgettingCurveUtils::decodeProbability(
                    probabilityEntry.getHistoricalInfo(), headerPolicy);
            if (rawProbability == NOT_A_PROBABILITY) {
                // The entry should not be treated as a valid entry.
                continue;
            }
            if (i == 0) {
                // unigram
                probability = rawProbability;
            } else {
                const ProbabilityEntry prevWordProbabilityEntry = getNgramProbabilityEntry(
                        prevWordIds.skip(1 /* n */).limit(i - 1), prevWordIds[0]);
                if (!prevWordProbabilityEntry.isValid()) {
                    continue;
                }
                if (prevWordProbabilityEntry.representsBeginningOfSentence()) {
                    probability = rawProbability;
                } else {
                    const int prevWordRawProbability = ForgettingCurveUtils::decodeProbability(
                            prevWordProbabilityEntry.getHistoricalInfo(), headerPolicy);
                    probability = std::min(MAX_PROBABILITY - prevWordRawProbability
                            + rawProbability, MAX_PROBABILITY);
                }
            }
        } else {
            probability = probabilityEntry.getProbability();
        }
        // TODO: Some flags in unigramProbabilityEntry should be overwritten by flags in
        // probabilityEntry.
        const ProbabilityEntry unigramProbabilityEntry = getProbabilityEntry(wordId);
        return WordAttributes(probability, unigramProbabilityEntry.isBlacklisted(),
                unigramProbabilityEntry.isNotAWord(),
                unigramProbabilityEntry.isPossiblyOffensive());
    }
    // Cannot find the word.
    return WordAttributes();
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

std::vector<LanguageModelDictContent::DumppedFullEntryInfo>
        LanguageModelDictContent::exportAllNgramEntriesRelatedToWord(
                const HeaderPolicy *const headerPolicy, const int wordId) const {
    const TrieMap::Result result = mTrieMap.getRoot(wordId);
    if (!result.mIsValid || result.mNextLevelBitmapEntryIndex == TrieMap::INVALID_INDEX) {
        // The word doesn't have any related ngram entries.
        return std::vector<DumppedFullEntryInfo>();
    }
    std::vector<int> prevWordIds = { wordId };
    std::vector<DumppedFullEntryInfo> entries;
    exportAllNgramEntriesRelatedToWordInner(headerPolicy, result.mNextLevelBitmapEntryIndex,
            &prevWordIds, &entries);
    return entries;
}

void LanguageModelDictContent::exportAllNgramEntriesRelatedToWordInner(
        const HeaderPolicy *const headerPolicy, const int bitmapEntryIndex,
        std::vector<int> *const prevWordIds,
        std::vector<DumppedFullEntryInfo> *const outBummpedFullEntryInfo) const {
    for (const auto &entry : mTrieMap.getEntriesInSpecifiedLevel(bitmapEntryIndex)) {
        const int wordId = entry.key();
        const ProbabilityEntry probabilityEntry =
                ProbabilityEntry::decode(entry.value(), mHasHistoricalInfo);
        if (probabilityEntry.isValid()) {
            const WordAttributes wordAttributes = getWordAttributes(
                    WordIdArrayView(*prevWordIds), wordId, headerPolicy);
            outBummpedFullEntryInfo->emplace_back(*prevWordIds, wordId,
                    wordAttributes, probabilityEntry);
        }
        if (entry.hasNextLevelMap()) {
            prevWordIds->push_back(wordId);
            exportAllNgramEntriesRelatedToWordInner(headerPolicy,
                    entry.getNextLevelBitmapEntryIndex(), prevWordIds, outBummpedFullEntryInfo);
            prevWordIds->pop_back();
        }
    }
}

bool LanguageModelDictContent::truncateEntries(const EntryCounts &currentEntryCounts,
        const EntryCounts &maxEntryCounts, const HeaderPolicy *const headerPolicy,
        MutableEntryCounters *const outEntryCounters) {
    for (int prevWordCount = 0; prevWordCount <= MAX_PREV_WORD_COUNT_FOR_N_GRAM; ++prevWordCount) {
        const int totalWordCount = prevWordCount + 1;
        if (currentEntryCounts.getNgramCount(totalWordCount)
                <= maxEntryCounts.getNgramCount(totalWordCount)) {
            outEntryCounters->setNgramCount(totalWordCount,
                    currentEntryCounts.getNgramCount(totalWordCount));
            continue;
        }
        int entryCount = 0;
        if (!turncateEntriesInSpecifiedLevel(headerPolicy,
                maxEntryCounts.getNgramCount(totalWordCount), prevWordCount, &entryCount)) {
            return false;
        }
        outEntryCounters->setNgramCount(totalWordCount, entryCount);
    }
    return true;
}

bool LanguageModelDictContent::updateAllEntriesOnInputWord(const WordIdArrayView prevWordIds,
        const int wordId, const bool isValid, const HistoricalInfo historicalInfo,
        const HeaderPolicy *const headerPolicy, MutableEntryCounters *const entryCountersToUpdate) {
    if (!mHasHistoricalInfo) {
        AKLOGE("updateAllEntriesOnInputWord is called for dictionary without historical info.");
        return false;
    }
    const ProbabilityEntry originalUnigramProbabilityEntry = getProbabilityEntry(wordId);
    const ProbabilityEntry updatedUnigramProbabilityEntry = createUpdatedEntryFrom(
            originalUnigramProbabilityEntry, isValid, historicalInfo, headerPolicy);
    if (!setProbabilityEntry(wordId, &updatedUnigramProbabilityEntry)) {
        return false;
    }
    for (size_t i = 0; i < prevWordIds.size(); ++i) {
        if (prevWordIds[i] == NOT_A_WORD_ID) {
            break;
        }
        // TODO: Optimize this code.
        const WordIdArrayView limitedPrevWordIds = prevWordIds.limit(i + 1);
        const ProbabilityEntry originalNgramProbabilityEntry = getNgramProbabilityEntry(
                limitedPrevWordIds, wordId);
        const ProbabilityEntry updatedNgramProbabilityEntry = createUpdatedEntryFrom(
                originalNgramProbabilityEntry, isValid, historicalInfo, headerPolicy);
        if (!setNgramProbabilityEntry(limitedPrevWordIds, wordId, &updatedNgramProbabilityEntry)) {
            return false;
        }
        if (!originalNgramProbabilityEntry.isValid()) {
            entryCountersToUpdate->incrementNgramCount(i + 2);
        }
    }
    return true;
}

const ProbabilityEntry LanguageModelDictContent::createUpdatedEntryFrom(
        const ProbabilityEntry &originalProbabilityEntry, const bool isValid,
        const HistoricalInfo historicalInfo, const HeaderPolicy *const headerPolicy) const {
    const HistoricalInfo updatedHistoricalInfo = ForgettingCurveUtils::createUpdatedHistoricalInfo(
            originalProbabilityEntry.getHistoricalInfo(), isValid ?
                    DUMMY_PROBABILITY_FOR_VALID_WORDS : NOT_A_PROBABILITY,
            &historicalInfo, headerPolicy);
    if (originalProbabilityEntry.isValid()) {
        return ProbabilityEntry(originalProbabilityEntry.getFlags(), &updatedHistoricalInfo);
    } else {
        return ProbabilityEntry(0 /* flags */, &updatedHistoricalInfo);
    }
}

bool LanguageModelDictContent::runGCInner(
        const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        const TrieMap::TrieMapRange trieMapRange, const int nextLevelBitmapEntryIndex) {
    for (auto &entry : trieMapRange) {
        const auto it = terminalIdMap->find(entry.key());
        if (it == terminalIdMap->end() || it->second == Ver4DictConstants::NOT_A_TERMINAL_ID) {
            // The word has been removed.
            continue;
        }
        if (!mTrieMap.put(it->second, entry.value(), nextLevelBitmapEntryIndex)) {
            return false;
        }
        if (entry.hasNextLevelMap()) {
            if (!runGCInner(terminalIdMap, entry.getEntriesInNextLevel(),
                    mTrieMap.getNextLevelBitmapEntryIndex(it->second, nextLevelBitmapEntryIndex))) {
                return false;
            }
        }
    }
    return true;
}

int LanguageModelDictContent::createAndGetBitmapEntryIndex(const WordIdArrayView prevWordIds) {
    int lastBitmapEntryIndex = mTrieMap.getRootBitmapEntryIndex();
    for (const int wordId : prevWordIds) {
        const TrieMap::Result result = mTrieMap.get(wordId, lastBitmapEntryIndex);
        if (result.mIsValid && result.mNextLevelBitmapEntryIndex != TrieMap::INVALID_INDEX) {
            lastBitmapEntryIndex = result.mNextLevelBitmapEntryIndex;
            continue;
        }
        if (!result.mIsValid) {
            if (!mTrieMap.put(wordId, ProbabilityEntry().encode(mHasHistoricalInfo),
                    lastBitmapEntryIndex)) {
                AKLOGE("Failed to update trie map. wordId: %d, lastBitmapEntryIndex %d", wordId,
                        lastBitmapEntryIndex);
                return TrieMap::INVALID_INDEX;
            }
        }
        lastBitmapEntryIndex = mTrieMap.getNextLevelBitmapEntryIndex(wordId,
                lastBitmapEntryIndex);
    }
    return lastBitmapEntryIndex;
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

bool LanguageModelDictContent::updateAllProbabilityEntriesForGCInner(const int bitmapEntryIndex,
        const int prevWordCount, const HeaderPolicy *const headerPolicy,
        MutableEntryCounters *const outEntryCounters) {
    for (const auto &entry : mTrieMap.getEntriesInSpecifiedLevel(bitmapEntryIndex)) {
        if (prevWordCount > MAX_PREV_WORD_COUNT_FOR_N_GRAM) {
            AKLOGE("Invalid prevWordCount. prevWordCount: %d, MAX_PREV_WORD_COUNT_FOR_N_GRAM: %d.",
                    prevWordCount, MAX_PREV_WORD_COUNT_FOR_N_GRAM);
            return false;
        }
        const ProbabilityEntry probabilityEntry =
                ProbabilityEntry::decode(entry.value(), mHasHistoricalInfo);
        if (prevWordCount > 0 && probabilityEntry.isValid()
                && !mTrieMap.getRoot(entry.key()).mIsValid) {
            // The entry is related to a word that has been removed. Remove the entry.
            if (!mTrieMap.remove(entry.key(), bitmapEntryIndex)) {
                return false;
            }
            continue;
        }
        if (mHasHistoricalInfo && !probabilityEntry.representsBeginningOfSentence()
                && probabilityEntry.isValid()) {
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
            outEntryCounters->incrementNgramCount(prevWordCount + 1);
        }
        if (!entry.hasNextLevelMap()) {
            continue;
        }
        if (!updateAllProbabilityEntriesForGCInner(entry.getNextLevelBitmapEntryIndex(),
                prevWordCount + 1, headerPolicy, outEntryCounters)) {
            return false;
        }
    }
    return true;
}

bool LanguageModelDictContent::turncateEntriesInSpecifiedLevel(
        const HeaderPolicy *const headerPolicy, const int maxEntryCount, const int targetLevel,
        int *const outEntryCount) {
    std::vector<int> prevWordIds;
    std::vector<EntryInfoToTurncate> entryInfoVector;
    if (!getEntryInfo(headerPolicy, targetLevel, mTrieMap.getRootBitmapEntryIndex(),
            &prevWordIds, &entryInfoVector)) {
        return false;
    }
    if (static_cast<int>(entryInfoVector.size()) <= maxEntryCount) {
        *outEntryCount = static_cast<int>(entryInfoVector.size());
        return true;
    }
    *outEntryCount = maxEntryCount;
    const int entryCountToRemove = static_cast<int>(entryInfoVector.size()) - maxEntryCount;
    std::partial_sort(entryInfoVector.begin(), entryInfoVector.begin() + entryCountToRemove,
            entryInfoVector.end(),
            EntryInfoToTurncate::Comparator());
    for (int i = 0; i < entryCountToRemove; ++i) {
        const EntryInfoToTurncate &entryInfo = entryInfoVector[i];
        if (!removeNgramProbabilityEntry(
                WordIdArrayView(entryInfo.mPrevWordIds, entryInfo.mPrevWordCount), entryInfo.mKey)) {
            return false;
        }
    }
    return true;
}

bool LanguageModelDictContent::getEntryInfo(const HeaderPolicy *const headerPolicy,
        const int targetLevel, const int bitmapEntryIndex,  std::vector<int> *const prevWordIds,
        std::vector<EntryInfoToTurncate> *const outEntryInfo) const {
    const int prevWordCount = prevWordIds->size();
    for (const auto &entry : mTrieMap.getEntriesInSpecifiedLevel(bitmapEntryIndex)) {
        if (prevWordCount < targetLevel) {
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
                probabilityEntry.getHistoricalInfo()->getTimestamp(),
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
    if (left.mPrevWordCount != right.mPrevWordCount) {
        return left.mPrevWordCount > right.mPrevWordCount;
    }
    for (int i = 0; i < left.mPrevWordCount; ++i) {
        if (left.mPrevWordIds[i] != right.mPrevWordIds[i]) {
            return left.mPrevWordIds[i] < right.mPrevWordIds[i];
        }
    }
    // left and rigth represent the same entry.
    return false;
}

LanguageModelDictContent::EntryInfoToTurncate::EntryInfoToTurncate(const int probability,
        const int timestamp, const int key, const int prevWordCount, const int *const prevWordIds)
        : mProbability(probability), mTimestamp(timestamp), mKey(key),
          mPrevWordCount(prevWordCount) {
    memmove(mPrevWordIds, prevWordIds, mPrevWordCount * sizeof(mPrevWordIds[0]));
}

} // namespace latinime
