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

#include "suggest/policyimpl/dictionary/structure/v4/content/language_model_dict_content.h"

#include <gtest/gtest.h>

#include "utils/int_array_view.h"

namespace latinime {
namespace {

TEST(LanguageModelDictContentTest, TestUnigramProbability) {
    LanguageModelDictContent LanguageModelDictContent(false /* useHistoricalInfo */);

    const int flag = 0xFF;
    const int probability = 10;
    const int wordId = 100;
    const ProbabilityEntry probabilityEntry(flag, probability);
    LanguageModelDictContent.setProbabilityEntry(wordId, &probabilityEntry);
    const ProbabilityEntry entry =
            LanguageModelDictContent.getProbabilityEntry(wordId);
    EXPECT_EQ(flag, entry.getFlags());
    EXPECT_EQ(probability, entry.getProbability());
}

TEST(LanguageModelDictContentTest, TestUnigramProbabilityWithHistoricalInfo) {
    LanguageModelDictContent LanguageModelDictContent(true /* useHistoricalInfo */);

    const int flag = 0xF0;
    const int timestamp = 0x3FFFFFFF;
    const int level = 3;
    const int count = 10;
    const int wordId = 100;
    const HistoricalInfo historicalInfo(timestamp, level, count);
    const ProbabilityEntry probabilityEntry(flag, NOT_A_PROBABILITY, &historicalInfo);
    LanguageModelDictContent.setProbabilityEntry(wordId, &probabilityEntry);
    const ProbabilityEntry entry = LanguageModelDictContent.getProbabilityEntry(wordId);
    EXPECT_EQ(flag, entry.getFlags());
    EXPECT_EQ(timestamp, entry.getHistoricalInfo()->getTimeStamp());
    EXPECT_EQ(level, entry.getHistoricalInfo()->getLevel());
    EXPECT_EQ(count, entry.getHistoricalInfo()->getCount());
}

}  // namespace
}  // namespace latinime
