/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_BLOOM_FILTER_H
#define LATINIME_BLOOM_FILTER_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

// This bloom filter is used for optimizing bigram retrieval.
// Execution times with previous word "this" are as follows:
//  without bloom filter (use only hash_map):
//   Total 147792.34 (sum of others 147771.57)
//  with bloom filter:
//   Total 145900.64 (sum of others 145874.30)
//  always read binary dictionary:
//   Total 148603.14 (sum of others 148579.90)
class BloomFilter {
 public:
    BloomFilter() {
        ASSERT(BIGRAM_FILTER_BYTE_SIZE * 8 >= BIGRAM_FILTER_MODULO);
    }

    // TODO: uint32_t position
    AK_FORCE_INLINE void setInFilter(const int32_t position) {
        const uint32_t bucket = static_cast<uint32_t>(position % BIGRAM_FILTER_MODULO);
        mFilter[bucket >> 3] |= static_cast<uint8_t>(1 << (bucket & 0x7));
    }

    // TODO: uint32_t position
    AK_FORCE_INLINE bool isInFilter(const int32_t position) const {
        const uint32_t bucket = static_cast<uint32_t>(position % BIGRAM_FILTER_MODULO);
        return (mFilter[bucket >> 3] & static_cast<uint8_t>(1 << (bucket & 0x7))) != 0;
    }

 private:
    // Size, in bytes, of the bloom filter index for bigrams
    // 128 gives us 1024 buckets. The probability of false positive is (1 - e ** (-kn/m))**k,
    // where k is the number of hash functions, n the number of bigrams, and m the number of
    // bits we can test.
    // At the moment 100 is the maximum number of bigrams for a word with the current
    // dictionaries, so n = 100. 1024 buckets give us m = 1024.
    // With 1 hash function, our false positive rate is about 9.3%, which should be enough for
    // our uses since we are only using this to increase average performance. For the record,
    // k = 2 gives 3.1% and k = 3 gives 1.6%. With k = 1, making m = 2048 gives 4.8%,
    // and m = 4096 gives 2.4%.
    // This is assigned here because it is used for array size.
    static const int BIGRAM_FILTER_BYTE_SIZE = 128;
    static const int BIGRAM_FILTER_MODULO;

    uint8_t mFilter[BIGRAM_FILTER_BYTE_SIZE];
};
} // namespace latinime
#endif // LATINIME_BLOOM_FILTER_H
