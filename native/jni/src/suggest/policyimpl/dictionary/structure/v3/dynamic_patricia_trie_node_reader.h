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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H

#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_params.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_reader.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DictionaryBigramsStructurePolicy;
class DictionaryShortcutsStructurePolicy;

/*
 * This class is used for helping to read nodes of dynamic patricia trie. This class handles moved
 * node and reads node attributes.
 */
class DynamicPatriciaTrieNodeReader : public PtNodeReader {
 public:
    DynamicPatriciaTrieNodeReader(const BufferWithExtendableBuffer *const buffer,
            const DictionaryBigramsStructurePolicy *const bigramsPolicy,
            const DictionaryShortcutsStructurePolicy *const shortcutsPolicy)
            : mBuffer(buffer), mBigramsPolicy(bigramsPolicy),
              mShortcutsPolicy(shortcutsPolicy) {}

    ~DynamicPatriciaTrieNodeReader() {}

    virtual const PtNodeParams fetchNodeInfoInBufferFromPtNodePos(const int ptNodePos) const {
        return fetchPtNodeInfoFromBufferAndProcessMovedPtNode(ptNodePos,
                NOT_A_DICT_POS /* siblingNodePos */, NOT_A_DICT_POS /* bigramLinkedNodePos */);
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(DynamicPatriciaTrieNodeReader);

    const BufferWithExtendableBuffer *const mBuffer;
    const DictionaryBigramsStructurePolicy *const mBigramsPolicy;
    const DictionaryShortcutsStructurePolicy *const mShortcutsPolicy;

   const  PtNodeParams fetchPtNodeInfoFromBufferAndProcessMovedPtNode(const int ptNodePos,
            const int siblingNodePos, const int bigramLinkedNodePos) const;
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_NODE_READER_H */
