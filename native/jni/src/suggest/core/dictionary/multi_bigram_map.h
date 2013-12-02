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

#ifndef LATINIME_MULTI_BIGRAM_MAP_H
#define LATINIME_MULTI_BIGRAM_MAP_H

#include <cstddef>

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_bigrams_iterator.h"
#include "suggest/core/dictionary/bloom_filter.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "utils/hash_map_compat.h"

namespace latinime {

// Class for caching bigram maps for multiple previous word contexts. This is useful since the
// algorithm needs to look up the set of bigrams for every word pair that occurs in every
// multi-word suggestion.
class MultiBigramMap {
 public:
    MultiBigramMap() : mBigramMaps() {}
    ~MultiBigramMap() {}

    // Look up the bigram probability for the given word pair from the cached bigram maps.
    // Also caches the bigrams if there is space remaining and they have not been cached already.
    int getBigramProbability(const DictionaryStructureWithBufferPolicy *const structurePolicy,
            const int wordPosition, const int nextWordPosition, const int unigramProbability);

    void clear() {
        mBigramMaps.clear();
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(MultiBigramMap);

    class BigramMap {
     public:
        BigramMap() : mBigramMap(DEFAULT_HASH_MAP_SIZE_FOR_EACH_BIGRAM_MAP), mBloomFilter() {}
        ~BigramMap() {}

        void init(const DictionaryStructureWithBufferPolicy *const structurePolicy,
                const int nodePos);

        int getBigramProbability(
                const DictionaryStructureWithBufferPolicy *const structurePolicy,
                const int nextWordPosition, const int unigramProbability) const;

     private:
        // NOTE: The BigramMap class doesn't use DISALLOW_COPY_AND_ASSIGN() because its default
        // copy constructor is needed for use in hash_map.
        static const int DEFAULT_HASH_MAP_SIZE_FOR_EACH_BIGRAM_MAP;
        hash_map_compat<int, int> mBigramMap;
        BloomFilter mBloomFilter;
    };

    void addBigramsForWordPosition(
            const DictionaryStructureWithBufferPolicy *const structurePolicy, const int position);

    int readBigramProbabilityFromBinaryDictionary(
            const DictionaryStructureWithBufferPolicy *const structurePolicy, const int nodePos,
            const int nextWordPosition, const int unigramProbability);

    static const size_t MAX_CACHED_PREV_WORDS_IN_BIGRAM_MAP;
    hash_map_compat<int, BigramMap> mBigramMaps;
};
} // namespace latinime
#endif // LATINIME_MULTI_BIGRAM_MAP_H
