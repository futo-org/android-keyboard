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

#ifndef LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H
#define LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_bigrams_structure_policy.h"
#include "suggest/policyimpl/dictionary/bigram/bigram_list_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/extendable_buffer.h"

namespace latinime {

/*
 * This is a dynamic version of BigramListPolicy and supports an additional buffer.
 */
class DynamicBigramListPolicy : public DictionaryBigramsStructurePolicy {
 public:
    DynamicBigramListPolicy(const BufferWithExtendableBuffer *const buffer)
            : mBuffer(buffer) {}

    ~DynamicBigramListPolicy() {}

    void getNextBigram(int *const outBigramPos, int *const outProbability, bool *const outHasNext,
            int *const pos) const {
        const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*pos);
        const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
        if (usesAdditionalBuffer) {
            *pos -= mBuffer->getOriginalBufferSize();
        }
        const BigramListReadingUtils::BigramFlags flags =
                BigramListReadingUtils::getFlagsAndForwardPointer(buffer, pos);
        *outBigramPos = BigramListReadingUtils::getBigramAddressAndForwardPointer(
                buffer, flags, pos);
        if (usesAdditionalBuffer) {
            *outBigramPos += mBuffer->getOriginalBufferSize();
        }
        *outProbability = BigramListReadingUtils::getProbabilityFromFlags(flags);
        *outHasNext = BigramListReadingUtils::hasNext(flags);
        if (usesAdditionalBuffer) {
            *pos += mBuffer->getOriginalBufferSize();
        }
    }

    void skipAllBigrams(int *const pos) const {
        const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*pos);
        const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
        if (usesAdditionalBuffer) {
            *pos -= mBuffer->getOriginalBufferSize();
        }
        BigramListReadingUtils::skipExistingBigrams(buffer, pos);
        if (usesAdditionalBuffer) {
            *pos += mBuffer->getOriginalBufferSize();
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicBigramListPolicy);

    const BufferWithExtendableBuffer *const mBuffer;
};
} // namespace latinime
#endif // LATINIME_DYNAMIC_BIGRAM_LIST_POLICY_H
