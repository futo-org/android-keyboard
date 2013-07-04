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

#ifndef LATINIME_BINARY_DICTIONARY_FORMAT_UTILS_H
#define LATINIME_BINARY_DICTIONARY_FORMAT_UTILS_H

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
class BinaryDictionaryFormatUtils {
 public:
    enum FORMAT_VERSION {
        VERSION_2,
        VERSION_3,
        UNKNOWN_VERSION
    };

    static FORMAT_VERSION detectFormatVersion(const uint8_t *const dict, const int dictSize);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(BinaryDictionaryFormatUtils);

    static const int DICTIONARY_MINIMUM_SIZE;
    static const uint32_t HEADER_VERSION_2_MAGIC_NUMBER;
    static const int HEADER_VERSION_2_MINIMUM_SIZE;
};
} // namespace latinime
#endif /* LATINIME_BINARY_DICTIONARY_FORMAT_UTILS_H */
