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

#ifndef LATINIME_DICT_TOOLKIT_UTF8_UTILS_H
#define LATINIME_DICT_TOOLKIT_UTF8_UTILS_H

#include <cstdint>
#include <string>
#include <vector>

#include "dict_toolkit_defines.h"
#include "utils/int_array_view.h"

namespace latinime {
namespace dicttoolkit {

class Utf8Utils {
public:
    static std::vector<int> getCodePoints(const std::string &utf8Str);
    static std::string getUtf8String(const CodePointArrayView codePoints);

private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Utf8Utils);

    // Values indexed by sequence size.
    static const size_t MAX_SEQUENCE_SIZE_FOR_A_CODE_POINT;
    static const uint8_t FIRST_BYTE_MARKER_MASKS[];
    static const uint8_t FIRST_BYTE_MARKERS[];
    static const uint8_t FIRST_BYTE_CODE_POINT_BITS_MASKS[];
    static const int MAX_ENCODED_CODE_POINT_VALUES[];

    static const uint8_t TRAILING_BYTE_CODE_POINT_BITS_MASK;
    static const uint8_t TRAILING_BYTE_MARKER;
    static const size_t CODE_POINT_BIT_COUNT_IN_TRAILING_BYTE;

    static int getSequenceSizeByCheckingFirstByte(const uint8_t firstByte);
    static int maskFirstByte(const uint8_t firstByte, const int encodeSize);
    static int maskTrailingByte(const uint8_t secondOrLaterByte);
    static int getSequenceSizeToEncodeCodePoint(const int codePoint);
};
} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_UTF8_UTILS_H
