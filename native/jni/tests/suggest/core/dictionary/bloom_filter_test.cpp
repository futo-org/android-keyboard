/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "suggest/core/dictionary/bloom_filter.h"

#include <gtest/gtest.h>

#include <cstdlib>
#include <ctime>
#include <unordered_set>
#include <vector>

namespace latinime {
namespace {

TEST(BloomFilterTest, TestFilter) {
    static const int ELEMENT_COUNT = 1000;
    srand(time(0));
    std::vector<int> elements;
    std::unordered_set<int> elementsThatHaveBeenSetInFilter;
    for (int i = 0; i < ELEMENT_COUNT; ++i) {
        elements.push_back(rand());
    }
    BloomFilter bloomFilter;
    for (const int elem : elements) {
        ASSERT_FALSE(bloomFilter.isInFilter(elem));
    }
    for (const int elem : elements) {
        if (rand() % 2 == 0) {
            bloomFilter.setInFilter(elem);
            elementsThatHaveBeenSetInFilter.insert(elem);
        }
    }
    for (const int elem : elements) {
        const bool existsInFilter = bloomFilter.isInFilter(elem);
        const bool hasBeenSetInFilter =
                elementsThatHaveBeenSetInFilter.find(elem) != elementsThatHaveBeenSetInFilter.end();
        if (hasBeenSetInFilter) {
            ASSERT_TRUE(existsInFilter);
        }
        if (!existsInFilter) {
            ASSERT_FALSE(hasBeenSetInFilter);
        }
    }
}

}  // namespace
}  // namespace latinime
