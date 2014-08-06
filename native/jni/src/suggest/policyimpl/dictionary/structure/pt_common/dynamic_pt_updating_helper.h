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

#ifndef LATINIME_DYNAMIC_PT_UPDATING_HELPER_H
#define LATINIME_DYNAMIC_PT_UPDATING_HELPER_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_params.h"
#include "utils/int_array_view.h"

namespace latinime {

class BigramProperty;
class BufferWithExtendableBuffer;
class DynamicPtReadingHelper;
class PtNodeReader;
class PtNodeWriter;
class UnigramProperty;

class DynamicPtUpdatingHelper {
 public:
    DynamicPtUpdatingHelper(BufferWithExtendableBuffer *const buffer,
            const PtNodeReader *const ptNodeReader, PtNodeWriter *const ptNodeWriter)
            : mBuffer(buffer), mPtNodeReader(ptNodeReader), mPtNodeWriter(ptNodeWriter) {}

    ~DynamicPtUpdatingHelper() {}

    // Add a word to the dictionary. If the word already exists, update the probability.
    bool addUnigramWord(DynamicPtReadingHelper *const readingHelper,
            const int *const wordCodePoints, const int codePointCount,
            const UnigramProperty *const unigramProperty, bool *const outAddedNewUnigram);

    // Add an n-gram entry.
    bool addNgramEntry(const PtNodePosArrayView prevWordsPtNodePos, const int wordPos,
            const BigramProperty *const bigramProperty, bool *const outAddedNewEntry);

    // Remove an n-gram entry.
    bool removeNgramEntry(const PtNodePosArrayView prevWordsPtNodePos, const int wordPos);

    // Add a shortcut target.
    bool addShortcutTarget(const int wordPos, const int *const targetCodePoints,
            const int targetCodePointCount, const int shortcutProbability);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPtUpdatingHelper);

    static const int CHILDREN_POSITION_FIELD_SIZE;

    BufferWithExtendableBuffer *const mBuffer;
    const PtNodeReader *const mPtNodeReader;
    PtNodeWriter *const mPtNodeWriter;

    bool createAndInsertNodeIntoPtNodeArray(const int parentPos, const int *const nodeCodePoints,
            const int nodeCodePointCount, const UnigramProperty *const unigramProperty,
            int *const forwardLinkFieldPos);

    bool setPtNodeProbability(const PtNodeParams *const originalPtNodeParams,
            const UnigramProperty *const unigramProperty, bool *const outAddedNewUnigram);

    bool createChildrenPtNodeArrayAndAChildPtNode(const PtNodeParams *const parentPtNodeParams,
            const UnigramProperty *const unigramProperty, const int *const codePoints,
            const int codePointCount);

    bool createNewPtNodeArrayWithAChildPtNode(const int parentPos, const int *const nodeCodePoints,
            const int nodeCodePointCount, const UnigramProperty *const unigramProperty);

    bool reallocatePtNodeAndAddNewPtNodes(
            const PtNodeParams *const reallocatingPtNodeParams, const int overlappingCodePointCount,
            const UnigramProperty *const unigramProperty, const int *const newNodeCodePoints,
            const int newNodeCodePointCount);

    const PtNodeParams getUpdatedPtNodeParams(const PtNodeParams *const originalPtNodeParams,
            const bool isNotAWord, const bool isBlacklisted, const bool isTerminal,
            const int parentPos, const int codePointCount,
            const int *const codePoints, const int probability) const;

    const PtNodeParams getPtNodeParamsForNewPtNode(const bool isNotAWord, const bool isBlacklisted,
            const bool isTerminal, const int parentPos,
            const int codePointCount, const int *const codePoints, const int probability) const;
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_UPDATING_HELPER_H */
