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

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/probability_entry.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/trie_map.h"
#include "utils/byte_array_view.h"
#include "utils/int_array_view.h"

namespace latinime {

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

    ProbabilityEntry getNgramProbabilityEntry(const WordIdArrayView prevWordIds,
            const int wordId) const;

    bool setNgramProbabilityEntry(const WordIdArrayView prevWordIds, const int wordId,
            const ProbabilityEntry *const probabilityEntry);

 private:
    DISALLOW_COPY_AND_ASSIGN(LanguageModelDictContent);

    TrieMap mTrieMap;
    const bool mHasHistoricalInfo;

    bool runGCInner(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            const TrieMap::TrieMapRange trieMapRange, const int nextLevelBitmapEntryIndex,
            int *const outNgramCount);

    int getBitmapEntryIndex(const WordIdArrayView prevWordIds) const;
};
} // namespace latinime
#endif /* LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H */
