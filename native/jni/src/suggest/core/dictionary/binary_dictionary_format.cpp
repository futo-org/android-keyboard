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

#include "suggest/core/dictionary/binary_dictionary_format.h"

namespace latinime {

/**
 * Dictionary size
 */
// Any file smaller than this is not a dictionary.
const int BinaryDictionaryFormat::DICTIONARY_MINIMUM_SIZE = 4;

/**
 * Format versions
 */
// Originally, format version 1 had a 16-bit magic number, then the version number `01'
// then options that must be 0. Hence the first 32-bits of the format are always as follow
// and it's okay to consider them a magic number as a whole.
const uint32_t BinaryDictionaryFormat::FORMAT_VERSION_1_MAGIC_NUMBER = 0x78B10100;
const int BinaryDictionaryFormat::FORMAT_VERSION_1_HEADER_SIZE = 5;

// The versions of Latin IME that only handle format version 1 only test for the magic
// number, so we had to change it so that version 2 files would be rejected by older
// implementations. On this occasion, we made the magic number 32 bits long.
const uint32_t BinaryDictionaryFormat::FORMAT_VERSION_2_MAGIC_NUMBER = 0x9BC13AFE;
// Magic number (4 bytes), version (2 bytes), options (2 bytes), header size (4 bytes) = 12
const int BinaryDictionaryFormat::FORMAT_VERSION_2_MINIMUM_SIZE = 12;
const int BinaryDictionaryFormat::VERSION_2_MAGIC_NUMBER_SIZE = 4;
const int BinaryDictionaryFormat::VERSION_2_DICTIONARY_VERSION_SIZE = 2;
const int BinaryDictionaryFormat::VERSION_2_DICTIONARY_FLAG_SIZE = 2;

/* static */ BinaryDictionaryFormat::FORMAT_VERSION BinaryDictionaryFormat::detectFormatVersion(
        const uint8_t *const dict, const int dictSize) {
    // The magic number is stored big-endian.
    // If the dictionary is less than 4 bytes, we can't even read the magic number, so we don't
    // understand this format.
    if (dictSize < DICTIONARY_MINIMUM_SIZE) {
        return UNKNOWN_VERSION;
    }
    const uint32_t magicNumber = ByteArrayUtils::readUint32(dict, 0);
    switch (magicNumber) {
    case FORMAT_VERSION_1_MAGIC_NUMBER:
        // Format 1 header is exactly 5 bytes long and looks like:
        // Magic number (2 bytes) 0x78 0xB1
        // Version number (1 byte) 0x01
        // Options (2 bytes) must be 0x00 0x00
        return VERSION_1;
    case FORMAT_VERSION_2_MAGIC_NUMBER:
        // Version 2 dictionaries are at least 12 bytes long.
        // If this dictionary has the version 2 magic number but is less than 12 bytes long,
        // then it's an unknown format and we need to avoid confidently reading the next bytes.
        if (dictSize < FORMAT_VERSION_2_MINIMUM_SIZE) {
            return UNKNOWN_VERSION;
        }
        // Format 2 header is as follows:
        // Magic number (4 bytes) 0x9B 0xC1 0x3A 0xFE
        // Version number (2 bytes) 0x00 0x02
        // Options (2 bytes)
        // Header size (4 bytes) : integer, big endian
        if (ByteArrayUtils::readUint16(dict, 4) == 2) {
            return VERSION_2;
        } else {
            return UNKNOWN_VERSION;
        }
    default:
        return UNKNOWN_VERSION;
    }
}

} // namespace latinime
