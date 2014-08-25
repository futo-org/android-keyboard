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

#ifndef LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H
#define LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H

#include <cstdio>
#include <vector>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/probability_entry.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/trie_map.h"
#include "utils/byte_array_view.h"
#include "utils/int_array_view.h"

namespace latinime {

class HeaderPolicy;

/**
 * Class representing language model.
 *
 * This class provides methods to get and store unigram/n-gram probability information and flags.
 */
class LanguageModelDictContent {
 public:
    LanguageModelDictContent(const ReadWriteByteArrayView trieMapBuffer,
            const bool hasHistoricalInfo)
            : mTrieMap(trieMapBuffer), mHasHistoricalInfo(hasHistoricalInfo) {}

    explicit LanguageModelDictContent(const bool hasHistoricalInfo)
            : mTrieMap(), mHasHistoricalInfo(hasHistoricalInfo) {}

    bool isNearSizeLimit() const {
        return mTrieMap.isNearSizeLimit();
    }

    bool save(FILE *const file) const;

    bool runGC(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            const LanguageModelDictContent *const originalContent,
            int *const outNgramCount);

    ProbabilityEntry getProbabilityEntry(const int wordId) const {
        return getNgramProbabilityEntry(WordIdArrayView(), wordId);
    }

    bool setProbabilityEntry(const int wordId, const ProbabilityEntry *const probabilityEntry) {
        return setNgramProbabilityEntry(WordIdArrayView(), wordId, probabilityEntry);
    }

    bool removeProbabilityEntry(const int wordId) {
        return removeNgramProbabilityEntry(WordIdArrayView(), wordId);
    }

    ProbabilityEntry getNgramProbabilityEntry(const WordIdArrayView prevWordIds,
            const int wordId) const;

    bool setNgramProbabilityEntry(const WordIdArrayView prevWordIds, const int wordId,
            const ProbabilityEntry *const probabilityEntry);

    bool removeNgramProbabilityEntry(const WordIdArrayView prevWordIds, const int wordId);

    bool updateAllProbabilityEntries(const HeaderPolicy *const headerPolicy,
            int *const outEntryCounts) {
        for (int i = 0; i <= MAX_PREV_WORD_COUNT_FOR_N_GRAM; ++i) {
            outEntryCounts[i] = 0;
        }
        return updateAllProbabilityEntriesInner(mTrieMap.getRootBitmapEntryIndex(), 0 /* level */,
                headerPolicy, outEntryCounts);
    }

    // entryCounts should be created by updateAllProbabilityEntries.
    bool truncateEntries(const int *const entryCounts, const int *const maxEntryCounts,
            const HeaderPolicy *const headerPolicy);

 private:
    DISALLOW_COPY_AND_ASSIGN(LanguageModelDictContent);

    class EntryInfoToTurncate {
     public:
        class Comparator {
         public:
            bool operator()(const EntryInfoToTurncate &left,
                    const EntryInfoToTurncate &right) const;
         private:
            DISALLOW_ASSIGNMENT_OPERATOR(Comparator);
        };

        EntryInfoToTurncate(const int probability, const int timestamp, const int key,
                const int entryLevel, const int *const prevWordIds);

        int mProbability;
        int mTimestamp;
        int mKey;
        int mEntryLevel;
        int mPrevWordIds[MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1];

     private:
        DISALLOW_DEFAULT_CONSTRUCTOR(EntryInfoToTurncate);
    };

    TrieMap mTrieMap;
    const bool mHasHistoricalInfo;

    bool runGCInner(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            const TrieMap::TrieMapRange trieMapRange, const int nextLevelBitmapEntryIndex,
            int *const outNgramCount);
    int createAndGetBitmapEntryIndex(const WordIdArrayView prevWordIds);
    int getBitmapEntryIndex(const WordIdArrayView prevWordIds) const;
    bool updateAllProbabilityEntriesInner(const int bitmapEntryIndex, const int level,
            const HeaderPolicy *const headerPolicy, int *const outEntryCounts);
    bool turncateEntriesInSpecifiedLevel(const HeaderPolicy *const headerPolicy,
            const int maxEntryCount, const int targetLevel);
    bool getEntryInfo(const HeaderPolicy *const headerPolicy, const int targetLevel,
            const int bitmapEntryIndex, std::vector<int> *const prevWordIds,
            std::vector<EntryInfoToTurncate> *const outEntryInfo) const;
};
} // namespace latinime
#endif /* LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H */
