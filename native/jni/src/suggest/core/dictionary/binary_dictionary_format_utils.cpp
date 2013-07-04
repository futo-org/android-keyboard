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

#include "suggest/core/dictionary/binary_dictionary_format_utils.h"

namespace latinime {

/**
 * Dictionary size
 */
// Any file smaller than this is not a dictionary.
const int BinaryDictionaryFormatUtils::DICTIONARY_MINIMUM_SIZE = 4;

/**
 * Format versions
 */

// The versions of Latin IME that only handle format version 1 only test for the magic
// number, so we had to change it so that version 2 files would be rejected by older
// implementations. On this occasion, we made the magic number 32 bits long.
const uint32_t BinaryDictionaryFormatUtils::HEADER_VERSION_2_MAGIC_NUMBER = 0x9BC13AFE;
// Magic number (4 bytes), version (2 bytes), options (2 bytes), header size (4 bytes) = 12
const int BinaryDictionaryFormatUtils::HEADER_VERSION_2_MINIMUM_SIZE = 12;

/* static */ BinaryDictionaryFormatUtils::FORMAT_VERSION
        BinaryDictionaryFormatUtils::detectFormatVersion(const uint8_t *const dict,
                const int dictSize) {
    // The magic number is stored big-endian.
    // If the dictionary is less than 4 bytes, we can't even read the magic number, so we don't
    // understand this format.
    if (dictSize < DICTIONARY_MINIMUM_SIZE) {
        return UNKNOWN_VERSION;
    }
    const uint32_t magicNumber = ByteArrayUtils::readUint32(dict, 0);
    switch (magicNumber) {
        case HEADER_VERSION_2_MAGIC_NUMBER:
            // Version 2 header are at least 12 bytes long.
            // If this header has the version 2 magic number but is less than 12 bytes long,
            // then it's an unknown format and we need to avoid confidently reading the next bytes.
            if (dictSize < HEADER_VERSION_2_MINIMUM_SIZE) {
                return UNKNOWN_VERSION;
            }
            // Version 2 header is as follows:
            // Magic number (4 bytes) 0x9B 0xC1 0x3A 0xFE
            // Version number (2 bytes)
            // Options (2 bytes)
            // Header size (4 bytes) : integer, big endian
            if (ByteArrayUtils::readUint16(dict, 4) == 2) {
                return VERSION_2;
            } else if (ByteArrayUtils::readUint16(dict, 4) == 3) {
                // TODO: Support version 3 dictionary.
                return UNKNOWN_VERSION;
            } else {
                return UNKNOWN_VERSION;
            }
        default:
            return UNKNOWN_VERSION;
    }
}

} // namespace latinime
