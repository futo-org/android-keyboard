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

#include "suggest/policyimpl/dictionary/header/header_reading_utils.h"

#include <vector>

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"
#include "suggest/policyimpl/dictionary/utils/byte_array_utils.h"

namespace latinime {

const int HeaderReadWriteUtils::MAX_ATTRIBUTE_KEY_LENGTH = 256;
const int HeaderReadWriteUtils::MAX_ATTRIBUTE_VALUE_LENGTH = 256;

const int HeaderReadWriteUtils::HEADER_MAGIC_NUMBER_SIZE = 4;
const int HeaderReadWriteUtils::HEADER_DICTIONARY_VERSION_SIZE = 2;
const int HeaderReadWriteUtils::HEADER_FLAG_SIZE = 2;
const int HeaderReadWriteUtils::HEADER_SIZE_FIELD_SIZE = 4;

const HeaderReadWriteUtils::DictionaryFlags HeaderReadWriteUtils::NO_FLAGS = 0;
// Flags for special processing
// Those *must* match the flags in makedict (FormatSpec#*_PROCESSING_FLAG) or
// something very bad (like, the apocalypse) will happen. Please update both at the same time.
const HeaderReadWriteUtils::DictionaryFlags
        HeaderReadWriteUtils::GERMAN_UMLAUT_PROCESSING_FLAG = 0x1;
const HeaderReadWriteUtils::DictionaryFlags
        HeaderReadWriteUtils::SUPPORTS_DYNAMIC_UPDATE_FLAG = 0x2;
const HeaderReadWriteUtils::DictionaryFlags
        HeaderReadWriteUtils::FRENCH_LIGATURE_PROCESSING_FLAG = 0x4;

/* static */ int HeaderReadWriteUtils::getHeaderSize(const uint8_t *const dictBuf) {
    // See the format of the header in the comment in
    // BinaryDictionaryFormatUtils::detectFormatVersion()
    return ByteArrayUtils::readUint32(dictBuf, HEADER_MAGIC_NUMBER_SIZE
            + HEADER_DICTIONARY_VERSION_SIZE + HEADER_FLAG_SIZE);
}

/* static */ HeaderReadWriteUtils::DictionaryFlags
        HeaderReadWriteUtils::getFlags(const uint8_t *const dictBuf) {
    return ByteArrayUtils::readUint16(dictBuf,
            HEADER_MAGIC_NUMBER_SIZE + HEADER_DICTIONARY_VERSION_SIZE);
}

/* static */ void HeaderReadWriteUtils::fetchAllHeaderAttributes(const uint8_t *const dictBuf,
        AttributeMap *const headerAttributes) {
    const int headerSize = getHeaderSize(dictBuf);
    int pos = getHeaderOptionsPosition();
    if (pos == NOT_A_DICT_POS) {
        // The header doesn't have header options.
        return;
    }
    int keyBuffer[MAX_ATTRIBUTE_KEY_LENGTH];
    int valueBuffer[MAX_ATTRIBUTE_VALUE_LENGTH];
    while (pos < headerSize) {
        const int keyLength = ByteArrayUtils::readStringAndAdvancePosition(dictBuf,
                MAX_ATTRIBUTE_KEY_LENGTH, keyBuffer, &pos);
        std::vector<int> key;
        key.insert(key.end(), keyBuffer, keyBuffer + keyLength);
        const int valueLength = ByteArrayUtils::readStringAndAdvancePosition(dictBuf,
                MAX_ATTRIBUTE_VALUE_LENGTH, valueBuffer, &pos);
        std::vector<int> value;
        value.insert(value.end(), valueBuffer, valueBuffer + valueLength);
        headerAttributes->insert(AttributeMap::value_type(key, value));
    }
}

/* static */ bool HeaderReadWriteUtils::writeDictionaryVersion(
        BufferWithExtendableBuffer *const buffer, const FormatUtils::FORMAT_VERSION version,
        int *const writingPos) {
    if (!buffer->writeUintAndAdvancePosition(FormatUtils::MAGIC_NUMBER, HEADER_MAGIC_NUMBER_SIZE,
            writingPos)) {
        return false;
    }
    switch (version) {
        case FormatUtils::VERSION_2:
            // Version 2 dictionary writing is not supported.
            return false;
        case FormatUtils::VERSION_3:
            return buffer->writeUintAndAdvancePosition(3 /* data */,
                    HEADER_DICTIONARY_VERSION_SIZE, writingPos);
        default:
            return false;
    }
}

/* static */ bool HeaderReadWriteUtils::writeDictionaryFlags(
        BufferWithExtendableBuffer *const buffer, const DictionaryFlags flags,
        int *const writingPos) {
    return buffer->writeUintAndAdvancePosition(flags, HEADER_FLAG_SIZE, writingPos);
}

/* static */ bool HeaderReadWriteUtils::writeDictionaryHeaderSize(
        BufferWithExtendableBuffer *const buffer, const int size, int *const writingPos) {
    return buffer->writeUintAndAdvancePosition(size, HEADER_SIZE_FIELD_SIZE, writingPos);
}

/* static */ bool HeaderReadWriteUtils::writeHeaderAttributes(
        BufferWithExtendableBuffer *const buffer, const AttributeMap *const headerAttributes,
        int *const writingPos) {
    for (AttributeMap::const_iterator it = headerAttributes->begin();
            it != headerAttributes->end(); ++it) {
        // Write a key.
        if (!buffer->writeCodePointsAndAdvancePosition(&(it->first.at(0)), it->first.size(),
                true /* writesTerminator */, writingPos)) {
            return false;
        }
        // Write a value.
        if (!buffer->writeCodePointsAndAdvancePosition(&(it->second.at(0)), it->second.size(),
                true /* writesTerminator */, writingPos)) {
            return false;
        }
    }
    return true;
}

} // namespace latinime
