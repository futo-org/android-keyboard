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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_UPDATING_HELPER_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_UPDATING_HELPER_H

#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_params.h"
#include "utils/hash_map_compat.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DynamicPatriciaTrieReadingHelper;
class PtNodeReader;
class PtNodeWriter;

// TODO: Move to pt_common.
class DynamicPatriciaTrieUpdatingHelper {
 public:
    DynamicPatriciaTrieUpdatingHelper(BufferWithExtendableBuffer *const buffer,
            const PtNodeReader *const ptNodeReader, PtNodeWriter *const ptNodeWriter)
            : mBuffer(buffer), mPtNodeReader(ptNodeReader), mPtNodeWriter(ptNodeWriter) {}

    ~DynamicPatriciaTrieUpdatingHelper() {}

    // Add a word to the dictionary. If the word already exists, update the probability.
    bool addUnigramWord(DynamicPatriciaTrieReadingHelper *const readingHelper,
            const int *const wordCodePoints, const int codePointCount, const int probability,
            bool *const outAddedNewUnigram);

    // Add a bigram relation from word0Pos to word1Pos.
    bool addBigramWords(const int word0Pos, const int word1Pos, const int probability,
            bool *const outAddedNewBigram);

    // Remove a bigram relation from word0Pos to word1Pos.
    bool removeBigramWords(const int word0Pos, const int word1Pos);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTrieUpdatingHelper);

    static const int CHILDREN_POSITION_FIELD_SIZE;

    BufferWithExtendableBuffer *const mBuffer;
    const PtNodeReader *const mPtNodeReader;
    PtNodeWriter *const mPtNodeWriter;

    bool createAndInsertNodeIntoPtNodeArray(const int parentPos, const int *const nodeCodePoints,
            const int nodeCodePointCount, const int probability, int *const forwardLinkFieldPos);

    bool setPtNodeProbability(const PtNodeParams *const originalPtNodeParams, const int probability,
            bool *const outAddedNewUnigram);

    bool createChildrenPtNodeArrayAndAChildPtNode(const PtNodeParams *const parentPtNodeParams,
            const int probability, const int *const codePoints, const int codePointCount);

    bool createNewPtNodeArrayWithAChildPtNode(const int parentPos, const int *const nodeCodePoints,
            const int nodeCodePointCount, const int probability);

    bool reallocatePtNodeAndAddNewPtNodes(
            const PtNodeParams *const reallocatingPtNodeParams, const int overlappingCodePointCount,
            const int probabilityOfNewPtNode, const int *const newNodeCodePoints,
            const int newNodeCodePointCount);

    const PtNodeParams getUpdatedPtNodeParams(const PtNodeParams *const originalPtNodeParams,
            const bool isTerminal, const int parentPos, const int codePointCount,
            const int *const codePoints, const int probability) const;

    const PtNodeParams getPtNodeParamsForNewPtNode(const bool isTerminal, const int parentPos,
            const int codePointCount, const int *const codePoints, const int probability) const;
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_UPDATING_HELPER_H */
