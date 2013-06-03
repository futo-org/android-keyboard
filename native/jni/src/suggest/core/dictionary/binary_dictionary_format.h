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

#ifndef LATINIME_BINARY_DICTIONARY_FORMAT_H
#define LATINIME_BINARY_DICTIONARY_FORMAT_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/dictionary/byte_array_utils.h"

namespace latinime {

/**
 * Methods to handle binary dictionary format version.
 *
 * Currently, we have a file with a similar name, binary_format.h. binary_format.h contains binary
 * reading methods and utility methods for various purposes.
 * On the other hand, this file deals with only about dictionary format version.
 */
class BinaryDictionaryFormat {
 public:
    // TODO: Remove obsolete version logic
    enum FORMAT_VERSION {
        VERSION_1,
        VERSION_2,
        UNKNOWN_VERSION
    };

    static FORMAT_VERSION detectFormatVersion(const uint8_t *const dict, const int dictSize);

    static AK_FORCE_INLINE int getHeaderSize(
            const uint8_t *const dict, const FORMAT_VERSION format) {
        switch (format) {
        case VERSION_1:
            return FORMAT_VERSION_1_HEADER_SIZE;
        case VERSION_2:
            // See the format of the header in the comment in detectFormat() above
            return ByteArrayUtils::readUint32(dict, 8);
        default:
            return S_INT_MAX;
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(BinaryDictionaryFormat);

    static const int DICTIONARY_MINIMUM_SIZE;
    static const uint32_t FORMAT_VERSION_1_MAGIC_NUMBER;
    static const int FORMAT_VERSION_1_HEADER_SIZE;
    static const uint32_t FORMAT_VERSION_2_MAGIC_NUMBER;
    static const int FORMAT_VERSION_2_MINIMUM_SIZE;
    static const int VERSION_2_MAGIC_NUMBER_SIZE;
    static const int VERSION_2_DICTIONARY_VERSION_SIZE ;
    static const int VERSION_2_DICTIONARY_FLAG_SIZE;
};
} // namespace latinime
#endif /* LATINIME_BINARY_DICTIONARY_FORMAT_H */
