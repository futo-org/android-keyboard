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

#ifndef LATINIME_HEADER_POLICY_H
#define LATINIME_HEADER_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/policyimpl/dictionary/header/header_reading_utils.h"

namespace latinime {

class HeaderPolicy : public DictionaryHeaderStructurePolicy {
 public:
    explicit HeaderPolicy(const uint8_t *const dictBuf)
            : mDictBuf(dictBuf), mDictionaryFlags(HeaderReadingUtils::getFlags(dictBuf)),
              mSize(HeaderReadingUtils::getHeaderSize(dictBuf)),
              mMultiWordCostMultiplier(readMultiWordCostMultiplier()) {}

    ~HeaderPolicy() {}

    AK_FORCE_INLINE int getSize() const {
        return mSize;
    }

    AK_FORCE_INLINE bool supportsDynamicUpdate() const {
        return HeaderReadingUtils::supportsDynamicUpdate(mDictionaryFlags);
    }

    AK_FORCE_INLINE bool requiresGermanUmlautProcessing() const {
        return HeaderReadingUtils::requiresGermanUmlautProcessing(mDictionaryFlags);
    }

    AK_FORCE_INLINE bool requiresFrenchLigatureProcessing() const {
        return HeaderReadingUtils::requiresFrenchLigatureProcessing(
                mDictionaryFlags);
    }

    AK_FORCE_INLINE float getMultiWordCostMultiplier() const {
        return mMultiWordCostMultiplier;
    }

    AK_FORCE_INLINE void readHeaderValueOrQuestionMark(const char *const key,
            int *outValue, int outValueSize) const {
        if (outValueSize <= 0) return;
        if (outValueSize == 1) {
            outValue[0] = '\0';
            return;
        }
        if (!HeaderReadingUtils::readHeaderValue(mDictBuf,
                key, outValue, outValueSize)) {
            outValue[0] = '?';
            outValue[1] = '\0';
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(HeaderPolicy);

    static const char *const MULTIPLE_WORDS_DEMOTION_RATE_KEY;
    static const float DEFAULT_MULTI_WORD_COST_MULTIPLIER;
    static const float MULTI_WORD_COST_MULTIPLIER_SCALE;

    const uint8_t *const mDictBuf;
    const HeaderReadingUtils::DictionaryFlags mDictionaryFlags;
    const int mSize;
    const float mMultiWordCostMultiplier;

    float readMultiWordCostMultiplier() const;
};

} // namespace latinime
#endif /* LATINIME_HEADER_POLICY_H */
