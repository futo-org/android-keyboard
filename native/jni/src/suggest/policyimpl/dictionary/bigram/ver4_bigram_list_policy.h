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

#ifndef LATINIME_VER4_BIGRAM_LIST_POLICY_H
#define LATINIME_VER4_BIGRAM_LIST_POLICY_H

#include "defines.h"
#include "suggest/core/policy/dictionary_bigrams_structure_policy.h"
#include "suggest/policyimpl/dictionary/bigram/bigram_list_read_write_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/bigram_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"

namespace latinime {

class Ver4BigramListPolicy : public DictionaryBigramsStructurePolicy {
 public:
    Ver4BigramListPolicy(const BigramDictContent *const bigramDictContent,
            const TerminalPositionLookupTable *const terminalPositionLookupTable)
            : mBigramDictContent(bigramDictContent),
              mTerminalPositionLookupTable(terminalPositionLookupTable) {}

    void getNextBigram(int *const outBigramPos, int *const outProbability,
            bool *const outHasNext, int *const bigramEntryPos) const {
        int bigramFlags = 0;
        int targetTerminalId = Ver4DictConstants::NOT_A_TERMINAL_ID;
        mBigramDictContent->getBigramEntryAndAdvancePosition(&bigramFlags, &targetTerminalId,
                bigramEntryPos);
        if (outProbability) {
            *outProbability = BigramListReadWriteUtils::getProbabilityFromFlags(bigramFlags);
        }
        if (outHasNext) {
            *outHasNext = BigramListReadWriteUtils::hasNext(bigramFlags);
        }
        if (outBigramPos) {
            // Lookup target PtNode position.
            *outBigramPos =
                    mTerminalPositionLookupTable->getTerminalPtNodePosition(targetTerminalId);
        }
    }

    void skipAllBigrams(int *const pos) const {
        // Do nothing because we don't need to skip bigram lists in ver4 dictionaries.
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Ver4BigramListPolicy);

    const BigramDictContent *const mBigramDictContent;
    const TerminalPositionLookupTable *const mTerminalPositionLookupTable;
};
} // namespace latinime
#endif /* LATINIME_VER4_BIGRAM_LIST_POLICY_H */
