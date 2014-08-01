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

#include "suggest/core/dictionary/multi_bigram_map.h"

#include <cstddef>
#include <unordered_map>

namespace latinime {

// Max number of bigram maps (previous word contexts) to be cached. Increasing this number
// could improve bigram lookup speed for multi-word suggestions, but at the cost of more memory
// usage. Also, there are diminishing returns since the most frequently used bigrams are
// typically near the beginning of the input and are thus the first ones to be cached. Note
// that these bigrams are reset for each new composing word.
const size_t MultiBigramMap::MAX_CACHED_PREV_WORDS_IN_BIGRAM_MAP = 25;

// Most common previous word contexts currently have 100 bigrams
const int MultiBigramMap::BigramMap::DEFAULT_HASH_MAP_SIZE_FOR_EACH_BIGRAM_MAP = 100;

// Look up the bigram probability for the given word pair from the cached bigram maps.
// Also caches the bigrams if there is space remaining and they have not been cached already.
int MultiBigramMap::getBigramProbability(
        const DictionaryStructureWithBufferPolicy *const structurePolicy,
        const int *const prevWordsPtNodePos, const int nextWordPosition,
        const int unigramProbability) {
    if (!prevWordsPtNodePos || prevWordsPtNodePos[0] == NOT_A_DICT_POS) {
        return structurePolicy->getProbability(unigramProbability, NOT_A_PROBABILITY);
    }
    std::unordered_map<int, BigramMap>::const_iterator mapPosition =
            mBigramMaps.find(prevWordsPtNodePos[0]);
    if (mapPosition != mBigramMaps.end()) {
        return mapPosition->second.getBigramProbability(structurePolicy, nextWordPosition,
                unigramProbability);
    }
    if (mBigramMaps.size() < MAX_CACHED_PREV_WORDS_IN_BIGRAM_MAP) {
        addBigramsForWordPosition(structurePolicy, prevWordsPtNodePos);
        return mBigramMaps[prevWordsPtNodePos[0]].getBigramProbability(structurePolicy,
                nextWordPosition, unigramProbability);
    }
    return readBigramProbabilityFromBinaryDictionary(structurePolicy, prevWordsPtNodePos,
            nextWordPosition, unigramProbability);
}

void MultiBigramMap::BigramMap::init(
        const DictionaryStructureWithBufferPolicy *const structurePolicy,
        const int *const prevWordsPtNodePos) {
    structurePolicy->iterateNgramEntries(prevWordsPtNodePos, this /* listener */);
}

int MultiBigramMap::BigramMap::getBigramProbability(
        const DictionaryStructureWithBufferPolicy *const structurePolicy,
        const int nextWordPosition, const int unigramProbability) const {
    int bigramProbability = NOT_A_PROBABILITY;
    if (mBloomFilter.isInFilter(nextWordPosition)) {
        const std::unordered_map<int, int>::const_iterator bigramProbabilityIt =
                mBigramMap.find(nextWordPosition);
        if (bigramProbabilityIt != mBigramMap.end()) {
            bigramProbability = bigramProbabilityIt->second;
        }
    }
    return structurePolicy->getProbability(unigramProbability, bigramProbability);
}

void MultiBigramMap::BigramMap::onVisitEntry(const int ngramProbability,
        const int targetPtNodePos) {
    if (targetPtNodePos == NOT_A_DICT_POS) {
        return;
    }
    mBigramMap[targetPtNodePos] = ngramProbability;
    mBloomFilter.setInFilter(targetPtNodePos);
}

void MultiBigramMap::addBigramsForWordPosition(
        const DictionaryStructureWithBufferPolicy *const structurePolicy,
        const int *const prevWordsPtNodePos) {
    if (prevWordsPtNodePos) {
        mBigramMaps[prevWordsPtNodePos[0]].init(structurePolicy, prevWordsPtNodePos);
    }
}

int MultiBigramMap::readBigramProbabilityFromBinaryDictionary(
        const DictionaryStructureWithBufferPolicy *const structurePolicy,
        const int *const prevWordsPtNodePos, const int nextWordPosition,
        const int unigramProbability) {
    const int bigramProbability = structurePolicy->getProbabilityOfPtNode(prevWordsPtNodePos,
            nextWordPosition);
    if (bigramProbability != NOT_A_PROBABILITY) {
        return bigramProbability;
    }
    return structurePolicy->getProbability(unigramProbability, NOT_A_PROBABILITY);
}

} // namespace latinime
