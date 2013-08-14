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

#ifndef LATINIME_PATRICIA_TRIE_READING_UTILS_H
#define LATINIME_PATRICIA_TRIE_READING_UTILS_H

#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/byte_array_utils.h"

namespace latinime {

class PatriciaTrieReadingUtils {
 public:
    typedef uint8_t NodeFlags;

    static AK_FORCE_INLINE int getGroupCountAndAdvancePosition(
            const uint8_t *const buffer, int *const pos) {
        const uint8_t firstByte = ByteArrayUtils::readUint8AndAdvancePosition(buffer, pos);
        if (firstByte < 0x80) {
            return firstByte;
        } else {
            return ((firstByte & 0x7F) << 8) ^ ByteArrayUtils::readUint8AndAdvancePosition(
                    buffer, pos);
        }
    }

    static AK_FORCE_INLINE NodeFlags getFlagsAndAdvancePosition(const uint8_t *const buffer,
            int *const pos) {
        return ByteArrayUtils::readUint8AndAdvancePosition(buffer, pos);
    }

    static AK_FORCE_INLINE int getCodePointAndAdvancePosition(const uint8_t *const buffer,
            int *const pos) {
        return ByteArrayUtils::readCodePointAndAdvancePosition(buffer, pos);
    }

    // Returns the number of read characters.
    static AK_FORCE_INLINE int getCharsAndAdvancePosition(const uint8_t *const buffer,
            const NodeFlags flags, const int maxLength, int *const outBuffer, int *const pos) {
        int length = 0;
        if (hasMultipleChars(flags)) {
            length = ByteArrayUtils::readStringAndAdvancePosition(buffer, maxLength, outBuffer,
                    pos);
        } else {
            if (maxLength > 0) {
                outBuffer[0] = getCodePointAndAdvancePosition(buffer, pos);
                length = 1;
            }
        }
        return length;
    }

    // Returns the number of skipped characters.
    static AK_FORCE_INLINE int skipCharacters(const uint8_t *const buffer, const NodeFlags flags,
            const int maxLength, int *const pos) {
        if (hasMultipleChars(flags)) {
            return ByteArrayUtils::advancePositionToBehindString(buffer, maxLength, pos);
        } else {
            if (maxLength > 0) {
                getCodePointAndAdvancePosition(buffer, pos);
                return 1;
            } else {
                return 0;
            }
        }
    }

    static AK_FORCE_INLINE int readProbabilityAndAdvancePosition(const uint8_t *const buffer,
            int *const pos) {
        return ByteArrayUtils::readUint8AndAdvancePosition(buffer, pos);
    }

    static int readChildrenPositionAndAdvancePosition(const uint8_t *const buffer,
            const NodeFlags flags, int *const pos);

    /**
     * Node Flags
     */
    static AK_FORCE_INLINE bool isBlacklisted(const NodeFlags flags) {
        return (flags & FLAG_IS_BLACKLISTED) != 0;
    }

    static AK_FORCE_INLINE bool isNotAWord(const NodeFlags flags) {
        return (flags & FLAG_IS_NOT_A_WORD) != 0;
    }

    static AK_FORCE_INLINE bool isTerminal(const NodeFlags flags) {
        return (flags & FLAG_IS_TERMINAL) != 0;
    }

    static AK_FORCE_INLINE bool hasShortcutTargets(const NodeFlags flags) {
        return (flags & FLAG_HAS_SHORTCUT_TARGETS) != 0;
    }

    static AK_FORCE_INLINE bool hasBigrams(const NodeFlags flags) {
        return (flags & FLAG_HAS_BIGRAMS) != 0;
    }

    static AK_FORCE_INLINE bool hasMultipleChars(const NodeFlags flags) {
        return (flags & FLAG_HAS_MULTIPLE_CHARS) != 0;
    }

    static AK_FORCE_INLINE bool hasChildrenInFlags(const NodeFlags flags) {
        return FLAG_GROUP_ADDRESS_TYPE_NOADDRESS != (MASK_GROUP_ADDRESS_TYPE & flags);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(PatriciaTrieReadingUtils);

    static const NodeFlags MASK_GROUP_ADDRESS_TYPE;
    static const NodeFlags FLAG_GROUP_ADDRESS_TYPE_NOADDRESS;
    static const NodeFlags FLAG_GROUP_ADDRESS_TYPE_ONEBYTE;
    static const NodeFlags FLAG_GROUP_ADDRESS_TYPE_TWOBYTES;
    static const NodeFlags FLAG_GROUP_ADDRESS_TYPE_THREEBYTES;

    static const NodeFlags FLAG_HAS_MULTIPLE_CHARS;
    static const NodeFlags FLAG_IS_TERMINAL;
    static const NodeFlags FLAG_HAS_SHORTCUT_TARGETS;
    static const NodeFlags FLAG_HAS_BIGRAMS;
    static const NodeFlags FLAG_IS_NOT_A_WORD;
    static const NodeFlags FLAG_IS_BLACKLISTED;
};
} // namespace latinime
#endif /* LATINIME_PATRICIA_TRIE_NODE_READING_UTILS_H */
