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

#include <unordered_set>

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

    // Remove
    EXPECT_TRUE(LanguageModelDictContent.removeProbabilityEntry(wordId));
    EXPECT_FALSE(LanguageModelDictContent.getProbabilityEntry(wordId).isValid());
    EXPECT_FALSE(LanguageModelDictContent.removeProbabilityEntry(wordId));
    EXPECT_TRUE(LanguageModelDictContent.setProbabilityEntry(wordId, &probabilityEntry));
    EXPECT_TRUE(LanguageModelDictContent.getProbabilityEntry(wordId).isValid());
}

TEST(LanguageModelDictContentTest, TestUnigramProbabilityWithHistoricalInfo) {
    LanguageModelDictContent LanguageModelDictContent(true /* useHistoricalInfo */);

    const int flag = 0xF0;
    const int timestamp = 0x3FFFFFFF;
    const int level = 3;
    const int count = 10;
    const int wordId = 100;
    const HistoricalInfo historicalInfo(timestamp, level, count);
    const ProbabilityEntry probabilityEntry(flag, &historicalInfo);
    LanguageModelDictContent.setProbabilityEntry(wordId, &probabilityEntry);
    const ProbabilityEntry entry = LanguageModelDictContent.getProbabilityEntry(wordId);
    EXPECT_EQ(flag, entry.getFlags());
    EXPECT_EQ(timestamp, entry.getHistoricalInfo()->getTimeStamp());
    EXPECT_EQ(level, entry.getHistoricalInfo()->getLevel());
    EXPECT_EQ(count, entry.getHistoricalInfo()->getCount());

    // Remove
    EXPECT_TRUE(LanguageModelDictContent.removeProbabilityEntry(wordId));
    EXPECT_FALSE(LanguageModelDictContent.getProbabilityEntry(wordId).isValid());
    EXPECT_FALSE(LanguageModelDictContent.removeProbabilityEntry(wordId));
    EXPECT_TRUE(LanguageModelDictContent.setProbabilityEntry(wordId, &probabilityEntry));
    EXPECT_TRUE(LanguageModelDictContent.removeProbabilityEntry(wordId));
}

TEST(LanguageModelDictContentTest, TestIterateProbabilityEntry) {
    LanguageModelDictContent languageModelDictContent(false /* useHistoricalInfo */);

    const ProbabilityEntry originalEntry(0xFC, 100);

    const int wordIds[] = { 1, 2, 3, 4, 5 };
    for (const int wordId : wordIds) {
        languageModelDictContent.setProbabilityEntry(wordId, &originalEntry);
    }
    std::unordered_set<int> wordIdSet(std::begin(wordIds), std::end(wordIds));
    for (const auto entry : languageModelDictContent.getProbabilityEntries(WordIdArrayView())) {
        EXPECT_EQ(originalEntry.getFlags(), entry.getProbabilityEntry().getFlags());
        EXPECT_EQ(originalEntry.getProbability(), entry.getProbabilityEntry().getProbability());
        wordIdSet.erase(entry.getWordId());
    }
    EXPECT_TRUE(wordIdSet.empty());
}

}  // namespace
}  // namespace latinime
