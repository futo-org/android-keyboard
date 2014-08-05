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

#include "suggest/policyimpl/dictionary/structure/v4/content/probability_entry.h"

#include <gtest/gtest.h>

#include "defines.h"

namespace latinime {
namespace {

TEST(ProbabilityEntryTest, TestEncodeDecode) {
    const int flag = 0xFF;
    const int probability = 10;

    const ProbabilityEntry entry(flag, probability);
    const uint64_t encodedEntry = entry.encode(false /* hasHistoricalInfo */);
    const ProbabilityEntry decodedEntry =
            ProbabilityEntry::decode(encodedEntry, false /* hasHistoricalInfo */);
    EXPECT_EQ(0xFF0Aull, encodedEntry);
    EXPECT_EQ(flag, decodedEntry.getFlags());
    EXPECT_EQ(probability, decodedEntry.getProbability());
}

TEST(ProbabilityEntryTest, TestEncodeDecodeWithHistoricalInfo) {
    const int flag = 0xF0;
    const int timestamp = 0x3FFFFFFF;
    const int level = 3;
    const int count = 10;

    const HistoricalInfo historicalInfo(timestamp, level, count);
    const ProbabilityEntry entry(flag, NOT_A_PROBABILITY, &historicalInfo);

    const uint64_t encodedEntry = entry.encode(true /* hasHistoricalInfo */);
    EXPECT_EQ(0xF03FFFFFFF030Aull, encodedEntry);
    const ProbabilityEntry decodedEntry =
            ProbabilityEntry::decode(encodedEntry, true /* hasHistoricalInfo */);

    EXPECT_EQ(flag, decodedEntry.getFlags());
    EXPECT_EQ(timestamp, decodedEntry.getHistoricalInfo()->getTimeStamp());
    EXPECT_EQ(level, decodedEntry.getHistoricalInfo()->getLevel());
    EXPECT_EQ(count, decodedEntry.getHistoricalInfo()->getCount());
}

}  // namespace
}  // namespace latinime
