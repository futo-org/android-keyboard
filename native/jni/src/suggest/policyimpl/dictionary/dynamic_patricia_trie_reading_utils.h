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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_READING_UTILS_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_READING_UTILS_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/byte_array_utils.h"

namespace latinime {

class DynamicPatriciaTrieReadingUtils {
 public:
    typedef uint8_t NodeFlags;

    static AK_FORCE_INLINE int getForwardLinkPosition(const uint8_t *const buffer, const int pos) {
        int linkAddressPos = pos;
        return ByteArrayUtils::readSint24AndAdvancePosition(buffer, &linkAddressPos);
    }

    static AK_FORCE_INLINE bool isValidForwardLinkPosition(const int forwardLinkAddress) {
        return forwardLinkAddress != 0;
    }

    static AK_FORCE_INLINE int getParentPosAndAdvancePosition(const uint8_t *const buffer,
            int *const pos) {
        const int base = *pos;
        return base + ByteArrayUtils::readSint24AndAdvancePosition(buffer, pos);
    }

    static int readChildrenPositionAndAdvancePosition(const uint8_t *const buffer,
            const NodeFlags flags, int *const pos);

    /**
     * Node Flags
     */
    static AK_FORCE_INLINE bool isMoved(const NodeFlags flags) {
        return FLAG_IS_MOVED == (MASK_MOVED & flags);
    }

    static AK_FORCE_INLINE bool isDeleted(const NodeFlags flags) {
        return FLAG_IS_DELETED == (MASK_MOVED & flags);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTrieReadingUtils);

    static const NodeFlags MASK_MOVED;
    static const NodeFlags FLAG_IS_NOT_MOVED;
    static const NodeFlags FLAG_IS_MOVED;
    static const NodeFlags FLAG_IS_DELETED;
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_READING_UTILS_H */
