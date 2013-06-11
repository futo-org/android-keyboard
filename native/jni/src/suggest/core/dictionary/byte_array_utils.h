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

#ifndef LATINIME_BYTE_ARRAY_UTILS_H
#define LATINIME_BYTE_ARRAY_UTILS_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

/**
 * Utility methods for reading byte arrays.
 */
class ByteArrayUtils {
 public:
    /**
     * Integer
     *
     * Each method read a corresponding size integer in a big endian manner.
     */
    static AK_FORCE_INLINE uint32_t readUint32(const uint8_t *const buffer, const int pos) {
        return (buffer[pos] << 24) ^ (buffer[pos + 1] << 16)
                ^ (buffer[pos + 2] << 8) ^ buffer[pos + 3];
    }

    static AK_FORCE_INLINE uint32_t readUint24(const uint8_t *const buffer, const int pos) {
        return (buffer[pos] << 16) ^ (buffer[pos + 1] << 8) ^ buffer[pos + 2];
    }

    static AK_FORCE_INLINE uint16_t readUint16(const uint8_t *const buffer, const int pos) {
        return (buffer[pos] << 8) ^ buffer[pos + 1];
    }

    static AK_FORCE_INLINE uint8_t readUint8(const uint8_t *const buffer, const int pos) {
        return buffer[pos];
    }

    static AK_FORCE_INLINE uint32_t readUint32andAdvancePosition(
            const uint8_t *const buffer, int *const pos) {
        const uint32_t value = readUint32(buffer, *pos);
        *pos += 4;
        return value;
    }

    static AK_FORCE_INLINE uint32_t readUint24andAdvancePosition(
            const uint8_t *const buffer, int *const pos) {
        const uint32_t value = readUint24(buffer, *pos);
        *pos += 3;
        return value;
    }

    static AK_FORCE_INLINE uint16_t readUint16andAdvancePosition(
            const uint8_t *const buffer, int *const pos) {
        const uint16_t value = readUint16(buffer, *pos);
        *pos += 2;
        return value;
    }

    static AK_FORCE_INLINE uint8_t readUint8andAdvancePosition(
            const uint8_t *const buffer, int *const pos) {
        return buffer[(*pos)++];
    }

    /**
     * Code Point
     *
     * 1 byte = bbbbbbbb match
     * case 000xxxxx: xxxxx << 16 + next byte << 8 + next byte
     * else: if 00011111 (= 0x1F) : this is the terminator. This is a relevant choice because
     *       unicode code points range from 0 to 0x10FFFF, so any 3-byte value starting with
     *       00011111 would be outside unicode.
     * else: iso-latin-1 code
     * This allows for the whole unicode range to be encoded, including chars outside of
     * the BMP. Also everything in the iso-latin-1 charset is only 1 byte, except control
     * characters which should never happen anyway (and still work, but take 3 bytes).
     */
    static AK_FORCE_INLINE int readCodePoint(const uint8_t *const buffer, const int pos) {
        int p = pos;
        return readCodePointAndAdvancePosition(buffer, &p);
    }

    static AK_FORCE_INLINE int readCodePointAndAdvancePosition(
            const uint8_t *const buffer, int *const pos) {
        const uint8_t firstByte = readUint8(buffer, *pos);
        if (firstByte < MINIMAL_ONE_BYTE_CHARACTER_VALUE) {
            if (firstByte == CHARACTER_ARRAY_TERMINATOR) {
                *pos += 1;
                return NOT_A_CODE_POINT;
            } else {
                return readUint24andAdvancePosition(buffer, pos);
            }
        } else {
            *pos += 1;
            return firstByte;
        }
    }

    /**
     * String (array of code points)
     *
     * Reads code points until the terminator is found.
     */
    // Returns the length of the string.
    static int readStringAndAdvancePosition(const uint8_t *const buffer,
            const int maxLength, int *const outBuffer, int *const pos) {
        int length = 0;
        int codePoint = readCodePointAndAdvancePosition(buffer, pos);
        while (NOT_A_CODE_POINT != codePoint && length < maxLength) {
            outBuffer[length++] = codePoint;
            codePoint = readCodePointAndAdvancePosition(buffer, pos);
        }
        return length;
    }

    // Advances the position and returns the length of the string.
    static int advancePositionToBehindString(
            const uint8_t *const buffer, const int maxLength, int *const pos) {
        int length = 0;
        int codePoint = readCodePointAndAdvancePosition(buffer, pos);
        while (NOT_A_CODE_POINT != codePoint && length < maxLength) {
            codePoint = readCodePointAndAdvancePosition(buffer, pos);
        }
        return length;
    }

    // Returns an integer less than, equal to, or greater than zero when string starting from pos
    // in buffer is less than, match, or is greater than charArray.
    static AK_FORCE_INLINE int compareStringInBufferWithCharArray(const uint8_t *const buffer,
            const char *const charArray, const int maxLength, int *const pos) {
        int index = 0;
        int codePoint = readCodePointAndAdvancePosition(buffer, pos);
        const uint8_t *const uint8CharArrayForComparison =
                reinterpret_cast<const uint8_t *>(charArray);
        while (NOT_A_CODE_POINT != codePoint
                && '\0' != uint8CharArrayForComparison[index] && index < maxLength) {
            if (codePoint != uint8CharArrayForComparison[index]) {
                // Different character is found.
                // Skip the rest of the string in the buffer.
                advancePositionToBehindString(buffer, maxLength - index, pos);
                return codePoint - uint8CharArrayForComparison[index];
            }
            // Advance
            codePoint = readCodePointAndAdvancePosition(buffer, pos);
            ++index;
        }
        if (NOT_A_CODE_POINT != codePoint && index < maxLength) {
            // Skip the rest of the string in the buffer.
            advancePositionToBehindString(buffer, maxLength - index, pos);
        }
        if (NOT_A_CODE_POINT == codePoint && '\0' == uint8CharArrayForComparison[index]) {
            // When both of the last characters are terminals, we consider the string in the buffer
            // matches the given char array
            return 0;
        } else {
            return codePoint - uint8CharArrayForComparison[index];
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ByteArrayUtils);

    static const uint8_t MINIMAL_ONE_BYTE_CHARACTER_VALUE;
    static const uint8_t CHARACTER_ARRAY_TERMINATOR;
};
} // namespace latinime
#endif /* LATINIME_BYTE_ARRAY_UTILS_H */
