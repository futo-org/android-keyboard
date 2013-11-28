/*
 * Copyright (C) 2013, The Android Open Source Project
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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_WRITER_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_WRITER_H

#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_params.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_writer.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_node_reader.h"
#include "suggest/policyimpl/dictionary/structure/v3/dynamic_patricia_trie_reading_helper.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DynamicBigramListPolicy;
class DynamicShortcutListPolicy;

/*
 * This class is used for helping to writes nodes of dynamic patricia trie.
 */
class DynamicPatriciaTrieNodeWriter : public PtNodeWriter {
 public:
    DynamicPatriciaTrieNodeWriter(BufferWithExtendableBuffer *const buffer,
            const DynamicPatriciaTrieNodeReader *const ptNodeReader,
            DynamicBigramListPolicy *const bigramPolicy,
            DynamicShortcutListPolicy *const shortcutPolicy, const bool needsToDecayWhenUpdating)
            : mBuffer(buffer), mPtNodeReader(ptNodeReader), mReadingHelper(mBuffer, ptNodeReader),
              mBigramPolicy(bigramPolicy), mShortcutPolicy(shortcutPolicy),
              mNeedsToDecayWhenUpdating(needsToDecayWhenUpdating) {}

    virtual ~DynamicPatriciaTrieNodeWriter() {}

    virtual bool markPtNodeAsDeleted(const PtNodeParams *const toBeUpdatedPtNodeParams);

    virtual bool markPtNodeAsMoved(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const int movedPos, const int bigramLinkedNodePos);

    virtual bool updatePtNodeProbability(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const int newProbability, const int timestamp);

    virtual bool updateChildrenPosition(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const int newChildrenPosition);

    virtual bool writePtNodeAndAdvancePosition(const PtNodeParams *const ptNodeParams,
            int *const ptNodeWritingPos);

    virtual bool writeNewTerminalPtNodeAndAdvancePosition(const PtNodeParams *const ptNodeParams,
            const int timestamp, int *const ptNodeWritingPos);

    virtual bool addNewBigramEntry(const PtNodeParams *const sourcePtNodeParams,
            const PtNodeParams *const targetPtNodeParam, const int probability, const int timestamp,
            bool *const outAddedNewBigram);

    virtual bool removeBigramEntry(const PtNodeParams *const sourcePtNodeParams,
            const PtNodeParams *const targetPtNodeParam);

    virtual bool updateAllBigramEntriesAndDeleteUselessEntries(
            const PtNodeParams *const sourcePtNodeParams, int *const outBigramEntryCount);

    virtual bool updateAllPositionFields(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const DictPositionRelocationMap *const dictPositionRelocationMap,
            int *const outBigramEntryCount);

 private:
    DISALLOW_COPY_AND_ASSIGN(DynamicPatriciaTrieNodeWriter);

    bool writePtNodeAndGetProbabilityFieldPosAndAdvancePosition(
            const PtNodeParams *const ptNodeParams, int *const outProbabilityFieldPos,
            int *const ptNodeWritingPos);

    int getUpdatedProbability(const int originalProbability, const int newProbability) const;

    static const int CHILDREN_POSITION_FIELD_SIZE;

    BufferWithExtendableBuffer *const mBuffer;
    const DynamicPatriciaTrieNodeReader *const mPtNodeReader;
    DynamicPatriciaTrieReadingHelper mReadingHelper;
    DynamicBigramListPolicy *const mBigramPolicy;
    DynamicShortcutListPolicy *const mShortcutPolicy;
    const bool mNeedsToDecayWhenUpdating;
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_WRITER_H */
