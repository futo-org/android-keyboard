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
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_writing_helper.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DictionaryShortcutsStructurePolicy;

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
            int *const bigramEntryPos) const;

    void skipAllBigrams(int *const bigramListPos) const;

    // Copy bigrams from the bigram list that starts at fromPos in mBuffer to toPos in
    // bufferToWrite and advance these positions after bigram lists. This method skips invalid
    // bigram entries and write the valid bigram entry count to outBigramsCount.
    bool copyAllBigrams(BufferWithExtendableBuffer *const bufferToWrite, int *const fromPos,
            int *const toPos, int *const outBigramsCount) const;

    bool updateAllBigramEntriesAndDeleteUselessEntries(int *const bigramListPos,
            int *const outBigramEntryCount);

    bool updateAllBigramTargetPtNodePositions(int *const bigramListPos,
            const DynamicPatriciaTrieWritingHelper::PtNodePositionRelocationMap *const
                    ptNodePositionRelocationMap, int *const outValidBigramEntryCount);

    bool addNewBigramEntryToBigramList(const int bigramTargetPos, const int probability,
            int *const bigramListPos, bool *const outAddedNewBigram);

    bool writeNewBigramEntry(const int bigramTargetPos, const int probability,
            int *const writingPos);

    // Return whether or not targetBigramPos is found.
    bool removeBigram(const int bigramListPos, const int bigramTargetPos);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicBigramListPolicy);

    static const int CONTINUING_BIGRAM_LINK_COUNT_LIMIT;
    static const int BIGRAM_ENTRY_COUNT_IN_A_BIGRAM_LIST_LIMIT;

    BufferWithExtendableBuffer *const mBuffer;
    const DictionaryShortcutsStructurePolicy *const mShortcutPolicy;

    // Follow bigram link and return the position of bigram target PtNode that is currently valid.
    int followBigramLinkAndGetCurrentBigramPtNodePos(const int originalBigramPos) const;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H
