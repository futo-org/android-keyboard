/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include "suggest/policyimpl/dictionary/bigram/bigram_list_reading_utils.h"

#include "suggest/core/dictionary/byte_array_utils.h"

namespace latinime {

const BigramListReadingUtils::BigramFlags BigramListReadingUtils::MASK_ATTRIBUTE_ADDRESS_TYPE =
        0x30;
const BigramListReadingUtils::BigramFlags
        BigramListReadingUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE = 0x10;
const BigramListReadingUtils::BigramFlags
        BigramListReadingUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES = 0x20;
const BigramListReadingUtils::BigramFlags
        BigramListReadingUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES = 0x30;
const BigramListReadingUtils::BigramFlags
        BigramListReadingUtils::FLAG_ATTRIBUTE_OFFSET_NEGATIVE = 0x40;
// Flag for presence of more attributes
const BigramListReadingUtils::BigramFlags BigramListReadingUtils::FLAG_ATTRIBUTE_HAS_NEXT = 0x80;
// Mask for attribute probability, stored on 4 bits inside the flags byte.
const BigramListReadingUtils::BigramFlags
        BigramListReadingUtils::MASK_ATTRIBUTE_PROBABILITY = 0x0F;
const int BigramListReadingUtils::ATTRIBUTE_ADDRESS_SHIFT = 4;

/* static */ int BigramListReadingUtils::getBigramAddressAndForwardPointer(
        const uint8_t *const bigramsBuf, const BigramFlags flags, int *const pos) {
    int offset = 0;
    const int origin = *pos;
    switch (MASK_ATTRIBUTE_ADDRESS_TYPE & flags) {
        case FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE:
            offset = ByteArrayUtils::readUint8AndAdvancePosition(bigramsBuf, pos);
            break;
        case FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES:
            offset = ByteArrayUtils::readUint16AndAdvancePosition(bigramsBuf, pos);
            break;
        case FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES:
            offset = ByteArrayUtils::readUint24AndAdvancePosition(bigramsBuf, pos);
            break;
    }
    if (isOffsetNegative(flags)) {
        return origin - offset;
    } else {
        return origin + offset;
    }
}

} // namespace latinime
