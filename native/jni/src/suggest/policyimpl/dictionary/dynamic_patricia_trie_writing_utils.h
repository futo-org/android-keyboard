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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_WRITING_UTILS_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_WRITING_UTILS_H

#include <cstddef>

#include "defines.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"

namespace latinime {

class BufferWithExtendableBuffer;

class DynamicPatriciaTrieWritingUtils {
 public:
    static const int NODE_FLAG_FIELD_SIZE;

    static bool writeEmptyDictionary(BufferWithExtendableBuffer *const buffer, const int rootPos);

    static bool writeForwardLinkPositionAndAdvancePosition(
            BufferWithExtendableBuffer *const buffer, const int forwardLinkPos,
            int *const forwardLinkFieldPos);

    static bool writePtNodeArraySizeAndAdvancePosition(BufferWithExtendableBuffer *const buffer,
            const size_t arraySize, int *const arraySizeFieldPos);

    static bool writeFlagsAndAdvancePosition(BufferWithExtendableBuffer *const buffer,
            const DynamicPatriciaTrieReadingUtils::NodeFlags nodeFlags,
            int *const nodeFlagsFieldPos);

    static bool writeParentPosOffsetAndAdvancePosition(BufferWithExtendableBuffer *const buffer,
            const int parentPosition, const int basePos, int *const parentPosFieldPos);

    static bool writeCodePointsAndAdvancePosition(BufferWithExtendableBuffer *const buffer,
            const int *const codePoints, const int codePointCount, int *const codePointFieldPos);

    static bool writeProbabilityAndAdvancePosition(BufferWithExtendableBuffer *const buffer,
            const int probability, int *const probabilityFieldPos);

    static bool writeChildrenPositionAndAdvancePosition(BufferWithExtendableBuffer *const buffer,
            const int childrenPosition, int *const childrenPositionFieldPos);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTrieWritingUtils);

    static const size_t MAX_PTNODE_ARRAY_SIZE_TO_USE_SMALL_SIZE_FIELD;
    static const size_t MAX_PTNODE_ARRAY_SIZE;
    static const int SMALL_PTNODE_ARRAY_SIZE_FIELD_SIZE;
    static const int LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE;
    static const int LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE_FLAG;
    static const int DICT_OFFSET_FIELD_SIZE;
    static const int MAX_DICT_OFFSET_VALUE;
    static const int MIN_DICT_OFFSET_VALUE;
    static const int DICT_OFFSET_NEGATIVE_FLAG;
    static const int PROBABILITY_FIELD_SIZE;

    static bool writeDictOffset(BufferWithExtendableBuffer *const buffer, const int targetPos,
            const int basePos, int *const offsetFieldPos);
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_WRITING_UTILS_H */
