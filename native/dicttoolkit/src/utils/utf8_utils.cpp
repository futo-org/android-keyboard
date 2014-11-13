/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "utils/utf8_utils.h"

#include "utils/char_utils.h"

namespace latinime {
namespace dicttoolkit {

const size_t Utf8Utils::MAX_SEQUENCE_SIZE_FOR_A_CODE_POINT = 4;
const uint8_t Utf8Utils::FIRST_BYTE_MARKER_MASKS[] = {0, 0x80, 0xE0, 0xF0, 0xF8};
const uint8_t Utf8Utils::FIRST_BYTE_MARKERS[] = {0, 0x00, 0xC0, 0xE0, 0xF0};
const uint8_t Utf8Utils::FIRST_BYTE_CODE_POINT_BITS_MASKS[] = {0, 0x7F, 0x1F, 0x0F, 0x03};
const int Utf8Utils::MAX_ENCODED_CODE_POINT_VALUES[] = {-1, 0x7F, 0x7FF, 0xFFFF, 0x10FFFF};

const uint8_t Utf8Utils::TRAILING_BYTE_CODE_POINT_BITS_MASK = 0x3F;
const uint8_t Utf8Utils::TRAILING_BYTE_MARKER = 0x80;
const size_t Utf8Utils::CODE_POINT_BIT_COUNT_IN_TRAILING_BYTE = 6;

/* static */ std::vector<int> Utf8Utils::getCodePoints(const std::string &utf8Str) {
    std::vector<int> codePoints;
    int remainingByteCountForCurrentCodePoint = 0;
    int currentCodePointSequenceSize = 0;
    int codePoint = 0;
    for (const char c : utf8Str) {
        if (remainingByteCountForCurrentCodePoint == 0) {
            currentCodePointSequenceSize = getSequenceSizeByCheckingFirstByte(c);
            if (currentCodePointSequenceSize <= 0) {
                AKLOGE("%x is an invalid utf8 first byte value.", c);
                return std::vector<int>();
            }
            remainingByteCountForCurrentCodePoint = currentCodePointSequenceSize;
            codePoint = maskFirstByte(c, remainingByteCountForCurrentCodePoint);
        } else {
            codePoint <<= CODE_POINT_BIT_COUNT_IN_TRAILING_BYTE;
            codePoint += maskTrailingByte(c);
        }
        remainingByteCountForCurrentCodePoint--;
        if (remainingByteCountForCurrentCodePoint == 0) {
            if (codePoint <= MAX_ENCODED_CODE_POINT_VALUES[currentCodePointSequenceSize - 1]) {
                AKLOGE("%d bytes encode for codePoint(%x) is a redundant UTF-8 sequence.",
                        currentCodePointSequenceSize,  codePoint);
                return std::vector<int>();
            }
            codePoints.push_back(codePoint);
        }
    }
    return codePoints;
}

/* static */ int Utf8Utils::getSequenceSizeByCheckingFirstByte(const uint8_t firstByte) {
    for (size_t i = 1; i <= MAX_SEQUENCE_SIZE_FOR_A_CODE_POINT; ++i) {
        if ((firstByte & FIRST_BYTE_MARKER_MASKS[i]) == FIRST_BYTE_MARKERS[i]) {
            return i;
        }
    }
    // Not a valid utf8 char first byte.
    return -1;
}

/* static */ AK_FORCE_INLINE int Utf8Utils::maskFirstByte(const uint8_t firstByte,
        const int sequenceSize) {
    return firstByte & FIRST_BYTE_CODE_POINT_BITS_MASKS[sequenceSize];
}

/* static */ AK_FORCE_INLINE int Utf8Utils::maskTrailingByte(const uint8_t secondOrLaterByte) {
    return secondOrLaterByte & TRAILING_BYTE_CODE_POINT_BITS_MASK;
}

/* static */ std::string Utf8Utils::getUtf8String(const CodePointArrayView codePoints) {
    std::string utf8String;
    for (const int codePoint : codePoints) {
        const int sequenceSize = getSequenceSizeToEncodeCodePoint(codePoint);
        if (sequenceSize <= 0) {
            AKLOGE("Cannot encode code point (%d).", codePoint);
            return std::string();
        }
        const int trailingByteCount = sequenceSize - 1;
        // Output first byte.
        const int value = codePoint >> (trailingByteCount * CODE_POINT_BIT_COUNT_IN_TRAILING_BYTE);
        utf8String.push_back(static_cast<char>(value | FIRST_BYTE_MARKERS[sequenceSize]));
        // Output second and later bytes.
        for (int i = 1; i < sequenceSize; ++i) {
            const int shiftAmount = (trailingByteCount - i) * CODE_POINT_BIT_COUNT_IN_TRAILING_BYTE;
            const int value = (codePoint >> shiftAmount) & TRAILING_BYTE_CODE_POINT_BITS_MASK;
            utf8String.push_back(static_cast<char>(value | TRAILING_BYTE_MARKER));
        }
    }
    return utf8String;
}

/* static */ int Utf8Utils::getSequenceSizeToEncodeCodePoint(const int codePoint) {
    if (codePoint < 0) {
        return -1;
    }
    for (size_t i = 1; i <= MAX_SEQUENCE_SIZE_FOR_A_CODE_POINT; ++i) {
        if (codePoint <= MAX_ENCODED_CODE_POINT_VALUES[i]) {
            return i;
        }
    }
    return -1;
}

} // namespace dicttoolkit
} // namespace latinime
