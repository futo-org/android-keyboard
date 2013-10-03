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

#ifndef LATINIME_HEADER_READ_WRITE_UTILS_H
#define LATINIME_HEADER_READ_WRITE_UTILS_H

#include <map>
#include <stdint.h>
#include <vector>

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/format_utils.h"

namespace latinime {

class BufferWithExtendableBuffer;

class HeaderReadWriteUtils {
 public:
    typedef uint16_t DictionaryFlags;
    typedef std::map<std::vector<int>, std::vector<int> > AttributeMap;

    static int getHeaderSize(const uint8_t *const dictBuf);

    static DictionaryFlags getFlags(const uint8_t *const dictBuf);

    static AK_FORCE_INLINE bool supportsDynamicUpdate(const DictionaryFlags flags) {
        return (flags & SUPPORTS_DYNAMIC_UPDATE_FLAG) != 0;
    }

    static AK_FORCE_INLINE bool requiresGermanUmlautProcessing(const DictionaryFlags flags) {
        return (flags & GERMAN_UMLAUT_PROCESSING_FLAG) != 0;
    }

    static AK_FORCE_INLINE bool requiresFrenchLigatureProcessing(const DictionaryFlags flags) {
        return (flags & FRENCH_LIGATURE_PROCESSING_FLAG) != 0;
    }

    static AK_FORCE_INLINE int getHeaderOptionsPosition() {
        return HEADER_MAGIC_NUMBER_SIZE + HEADER_DICTIONARY_VERSION_SIZE + HEADER_FLAG_SIZE
                + HEADER_SIZE_FIELD_SIZE;
    }

    static DictionaryFlags createAndGetDictionaryFlagsUsingAttributeMap(
            const HeaderReadWriteUtils::AttributeMap *const attributeMap);

    static void fetchAllHeaderAttributes(const uint8_t *const dictBuf,
            AttributeMap *const headerAttributes);

    static bool writeDictionaryVersion(BufferWithExtendableBuffer *const buffer,
            const FormatUtils::FORMAT_VERSION version, int *const writingPos);

    static bool writeDictionaryFlags(BufferWithExtendableBuffer *const buffer,
            const DictionaryFlags flags, int *const writingPos);

    static bool writeDictionaryHeaderSize(BufferWithExtendableBuffer *const buffer,
            const int size, int *const writingPos);

    static bool writeHeaderAttributes(BufferWithExtendableBuffer *const buffer,
            const AttributeMap *const headerAttributes, int *const writingPos);

    /**
     * Methods for header attributes.
     */
    static void setBoolAttribute(AttributeMap *const headerAttributes,
            const char *const key, const bool value);

    static void setIntAttribute(AttributeMap *const headerAttributes,
            const char *const key, const int value);

    static bool readBoolAttributeValue(const AttributeMap *const headerAttributes,
            const char *const key, const bool defaultValue);

    static int readIntAttributeValue(const AttributeMap *const headerAttributes,
            const char *const key, const int defaultValue);

    static void insertCharactersIntoVector(const char *const characters,
            AttributeMap::key_type *const key);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(HeaderReadWriteUtils);

    static const int MAX_ATTRIBUTE_KEY_LENGTH;
    static const int MAX_ATTRIBUTE_VALUE_LENGTH;

    static const int HEADER_MAGIC_NUMBER_SIZE;
    static const int HEADER_DICTIONARY_VERSION_SIZE;
    static const int HEADER_FLAG_SIZE;
    static const int HEADER_SIZE_FIELD_SIZE;

    static const DictionaryFlags NO_FLAGS;
    // Flags for special processing
    // Those *must* match the flags in makedict (FormatSpec#*_PROCESSING_FLAGS) or
    // something very bad (like, the apocalypse) will happen. Please update both at the same time.
    static const DictionaryFlags GERMAN_UMLAUT_PROCESSING_FLAG;
    static const DictionaryFlags SUPPORTS_DYNAMIC_UPDATE_FLAG;
    static const DictionaryFlags FRENCH_LIGATURE_PROCESSING_FLAG;

    static const char *const SUPPORTS_DYNAMIC_UPDATE_KEY;
    static const char *const REQUIRES_GERMAN_UMLAUT_PROCESSING_KEY;
    static const char *const REQUIRES_FRENCH_LIGATURE_PROCESSING_KEY;

    static void setIntAttributeInner(AttributeMap *const headerAttributes,
            const AttributeMap::key_type *const key, const int value);

    static int readIntAttributeValueInner(const AttributeMap *const headerAttributes,
            const AttributeMap::key_type *const key, const int defaultValue);
};
}
#endif /* LATINIME_HEADER_READ_WRITE_UTILS_H */
