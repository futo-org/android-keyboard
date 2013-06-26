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

#ifndef LATINIME_BINARY_DICTIONARY_HEADER_H
#define LATINIME_BINARY_DICTIONARY_HEADER_H

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_header_reading_utils.h"

namespace latinime {

class BinaryDictionaryInfo;

/**
 * This class abstracts dictionary header structures and provide interface to access dictionary
 * header information.
 */
class BinaryDictionaryHeader {
 public:
    explicit BinaryDictionaryHeader(const BinaryDictionaryInfo *const binaryDictionaryInfo);

    AK_FORCE_INLINE int getSize() const {
        return mSize;
    }

    AK_FORCE_INLINE bool supportsDynamicUpdate() const {
        return BinaryDictionaryHeaderReadingUtils::supportsDynamicUpdate(mDictionaryFlags);
    }

    AK_FORCE_INLINE bool requiresGermanUmlautProcessing() const {
        return BinaryDictionaryHeaderReadingUtils::requiresGermanUmlautProcessing(mDictionaryFlags);
    }

    AK_FORCE_INLINE bool requiresFrenchLigatureProcessing() const {
        return BinaryDictionaryHeaderReadingUtils::requiresFrenchLigatureProcessing(
                mDictionaryFlags);
    }

    AK_FORCE_INLINE float getMultiWordCostMultiplier() const {
        return mMultiWordCostMultiplier;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(BinaryDictionaryHeader);

    static const char *const MULTIPLE_WORDS_DEMOTION_RATE_KEY;
    static const float DEFAULT_MULTI_WORD_COST_MULTIPLIER;
    static const float MULTI_WORD_COST_MULTIPLIER_SCALE;

    const BinaryDictionaryInfo *const mBinaryDictionaryInfo;
    const BinaryDictionaryHeaderReadingUtils::DictionaryFlags mDictionaryFlags;
    const int mSize;
    const float mMultiWordCostMultiplier;

    float readMultiWordCostMultiplier() const;
};
} // namespace latinime
#endif // LATINIME_BINARY_DICTIONARY_HEADER_H
