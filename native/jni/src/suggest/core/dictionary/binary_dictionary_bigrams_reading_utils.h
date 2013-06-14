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

#ifndef LATINIME_BINARY_DICTIONARY_BIGRAM_READING_UTILS_H
#define LATINIME_BINARY_DICTIONARY_BIGRAM_READING_UTILS_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/byte_array_utils.h"

namespace latinime {

class BinaryDictionaryBigramsReadingUtils {
 public:
    typedef uint8_t BigramFlags;

    static AK_FORCE_INLINE void skipExistingBigrams(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, int *const pos) {
        BigramFlags flags = getFlagsAndForwardPointer(binaryDictionaryInfo, pos);
        while (hasNext(flags)) {
            *pos += attributeAddressSize(flags);
            flags = getFlagsAndForwardPointer(binaryDictionaryInfo, pos);
        }
        *pos += attributeAddressSize(flags);
    }

    static AK_FORCE_INLINE BigramFlags getFlagsAndForwardPointer(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, int *const pos) {
        return ByteArrayUtils::readUint8andAdvancePosition(
                binaryDictionaryInfo->getDictRoot(), pos);
    }

    static AK_FORCE_INLINE int getBigramProbability(const BigramFlags flags) {
        return flags & MASK_ATTRIBUTE_PROBABILITY;
    }

    static AK_FORCE_INLINE bool isOffsetNegative(const BigramFlags flags) {
        return (flags & FLAG_ATTRIBUTE_OFFSET_NEGATIVE) != 0;
    }

    static AK_FORCE_INLINE bool hasNext(const BigramFlags flags) {
        return (flags & FLAG_ATTRIBUTE_HAS_NEXT) != 0;
    }

    static int getBigramAddressAndForwardPointer(
            const BinaryDictionaryInfo *const binaryDictionaryInfo,
            const BigramFlags flags, int *const pos);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(BinaryDictionaryBigramsReadingUtils);

    static const BigramFlags MASK_ATTRIBUTE_ADDRESS_TYPE;
    static const BigramFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
    static const BigramFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
    static const BigramFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
    static const BigramFlags FLAG_ATTRIBUTE_OFFSET_NEGATIVE;
    static const BigramFlags FLAG_ATTRIBUTE_HAS_NEXT;
    static const BigramFlags MASK_ATTRIBUTE_PROBABILITY;
    static const int ATTRIBUTE_ADDRESS_SHIFT;

    static AK_FORCE_INLINE int attributeAddressSize(const BigramFlags flags) {
        return (flags & MASK_ATTRIBUTE_ADDRESS_TYPE) >> ATTRIBUTE_ADDRESS_SHIFT;
        /* Note: this is a value-dependant optimization of what may probably be
           more readably written this way:
           switch (flags * BinaryFormat::MASK_ATTRIBUTE_ADDRESS_TYPE) {
           case FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE: return 1;
           case FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES: return 2;
           case FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTE: return 3;
           default: return 0;
           }
        */
    }
};
}
#endif /* LATINIME_BINARY_DICTIONARY_BIGRAM_READING_UTILS_H */
