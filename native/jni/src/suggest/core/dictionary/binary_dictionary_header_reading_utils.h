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

#ifndef LATINIME_HEADER_READING_UTILS_H
#define LATINIME_HEADER_READING_UTILS_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

// TODO: Move to policyimpl.
class HeaderReadingUtils {
 public:
    typedef uint16_t DictionaryFlags;

    static const int MAX_OPTION_KEY_LENGTH;

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

    static bool readHeaderValue(const uint8_t *const dictBuf,
            const char *const key, int *outValue, const int outValueSize);

    static int readHeaderValueInt(const uint8_t *const dictBuf, const char *const key);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(HeaderReadingUtils);

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
    static const DictionaryFlags CONTAINS_BIGRAMS_FLAG;
};
}
#endif /* LATINIME_HEADER_READING_UTILS_H */
