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

#ifndef LATINIME_BINARY_DICTIONARY_TERMINAL_ATTRIBUTES_READING_UTILS_H
#define LATINIME_BINARY_DICTIONARY_TERMINAL_ATTRIBUTES_READING_UTILS_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/byte_array_utils.h"

namespace latinime {

class BinaryDictionaryTerminalAttributesReadingUtils {
 public:
    typedef uint8_t TerminalAttributeFlags;
    typedef TerminalAttributeFlags BigramFlags;

    static AK_FORCE_INLINE TerminalAttributeFlags getFlagsAndForwardPointer(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, int *const pos) {
        return ByteArrayUtils::readUint8andAdvancePosition(
                binaryDictionaryInfo->getDictRoot(), pos);
    }

    static AK_FORCE_INLINE int getProbabilityFromFlags(const TerminalAttributeFlags flags) {
        return flags & MASK_ATTRIBUTE_PROBABILITY;
    }

    static AK_FORCE_INLINE bool hasNext(const TerminalAttributeFlags flags) {
        return (flags & FLAG_ATTRIBUTE_HAS_NEXT) != 0;
    }

    // Bigrams reading methods
    static AK_FORCE_INLINE void skipExistingBigrams(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, int *const pos) {
        BigramFlags flags = getFlagsAndForwardPointer(binaryDictionaryInfo, pos);
        while (hasNext(flags)) {
            *pos += attributeAddressSize(flags);
            flags = getFlagsAndForwardPointer(binaryDictionaryInfo, pos);
        }
        *pos += attributeAddressSize(flags);
    }

    static int getBigramAddressAndForwardPointer(
            const BinaryDictionaryInfo *const binaryDictionaryInfo, const BigramFlags flags,
                    int *const pos);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(BinaryDictionaryTerminalAttributesReadingUtils);

    static const TerminalAttributeFlags MASK_ATTRIBUTE_ADDRESS_TYPE;
    static const TerminalAttributeFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
    static const TerminalAttributeFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
    static const TerminalAttributeFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
    static const TerminalAttributeFlags FLAG_ATTRIBUTE_OFFSET_NEGATIVE;
    static const TerminalAttributeFlags FLAG_ATTRIBUTE_HAS_NEXT;
    static const TerminalAttributeFlags MASK_ATTRIBUTE_PROBABILITY;
    static const int ATTRIBUTE_ADDRESS_SHIFT;

    static AK_FORCE_INLINE bool isOffsetNegative(const TerminalAttributeFlags flags) {
        return (flags & FLAG_ATTRIBUTE_OFFSET_NEGATIVE) != 0;
    }

    static AK_FORCE_INLINE int attributeAddressSize(const TerminalAttributeFlags flags) {
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
#endif /* LATINIME_BINARY_DICTIONARY_TERMINAL_ATTRIBUTES_READING_UTILS_H */
