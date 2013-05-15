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

#include <cstring>
#include <stdint.h>

#include "defines.h"
#include "binary_format.h"
#include "hash_map_compat.h"

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
    int getBigramProbability(const uint8_t *const dicRoot, const int wordPosition,
            const int nextWordPosition, const int unigramProbability) {
        hash_map_compat<int, BigramMap>::const_iterator mapPosition =
                mBigramMaps.find(wordPosition);
        if (mapPosition != mBigramMaps.end()) {
            return mapPosition->second.getBigramProbability(nextWordPosition, unigramProbability);
        }
        if (mBigramMaps.size() < MAX_CACHED_PREV_WORDS_IN_BIGRAM_MAP) {
            addBigramsForWordPosition(dicRoot, wordPosition);
            return mBigramMaps[wordPosition].getBigramProbability(
                    nextWordPosition, unigramProbability);
        }
        return BinaryFormat::getBigramProbability(
                dicRoot, wordPosition, nextWordPosition, unigramProbability);
    }

    void clear() {
        mBigramMaps.clear();
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(MultiBigramMap);

    class BigramMap {
     public:
        BigramMap() : mBigramMap(DEFAULT_HASH_MAP_SIZE_FOR_EACH_BIGRAM_MAP) {}
        ~BigramMap() {}

        void init(const uint8_t *const dicRoot, int position) {
            BinaryFormat::fillBigramProbabilityToHashMap(dicRoot, position, &mBigramMap);
        }

        inline int getBigramProbability(const int nextWordPosition, const int unigramProbability)
                const {
           return BinaryFormat::getBigramProbabilityFromHashMap(
                   nextWordPosition, &mBigramMap, unigramProbability);
        }

     private:
        // Note: Default copy constructor needed for use in hash_map.
        hash_map_compat<int, int> mBigramMap;
    };

    void addBigramsForWordPosition(const uint8_t *const dicRoot, const int position) {
        mBigramMaps[position].init(dicRoot, position);
    }

    hash_map_compat<int, BigramMap> mBigramMaps;
};
} // namespace latinime
#endif // LATINIME_MULTI_BIGRAM_MAP_H
