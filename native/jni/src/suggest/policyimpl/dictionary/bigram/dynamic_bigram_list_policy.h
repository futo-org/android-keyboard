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

#ifndef LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H
#define LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_bigrams_structure_policy.h"
#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"
#include "suggest/policyimpl/dictionary/bigram/bigram_list_read_write_utils.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_helper.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

/*
 * This is a dynamic version of BigramListPolicy and supports an additional buffer.
 */
class DynamicBigramListPolicy : public DictionaryBigramsStructurePolicy {
 public:
    DynamicBigramListPolicy(BufferWithExtendableBuffer *const buffer,
            const DictionaryShortcutsStructurePolicy *const shortcutPolicy)
            : mBuffer(buffer), mShortcutPolicy(shortcutPolicy) {}

    ~DynamicBigramListPolicy() {}

    void getNextBigram(int *const outBigramPos, int *const outProbability, bool *const outHasNext,
            int *const pos) const;

    void skipAllBigrams(int *const pos) const;

    // Copy bigrams from the bigram list that starts at fromPos to toPos and advance these
    // positions after bigram lists. This method skips invalid bigram entries.
    bool copyAllBigrams(int *const fromPos, int *const toPos);

    bool addNewBigramEntryToBigramList(const int bigramPos, const int probability, int *const pos);

    bool writeNewBigramEntry(const int bigramPos, const int probability,
            int *const writingPos);

    // Return if targetBigramPos is found or not.
    bool removeBigram(const int bigramListPos, const int targetBigramPos);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicBigramListPolicy);

    static const int BIGRAM_LINK_COUNT_LIMIT;

    BufferWithExtendableBuffer *const mBuffer;
    const DictionaryShortcutsStructurePolicy *const mShortcutPolicy;

    // Follow bigram link and return the position of bigram target PtNode that is currently valid.
    int followBigramLinkAndGetCurrentBigramPtNodePos(const int originalBigramPos) const;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H
