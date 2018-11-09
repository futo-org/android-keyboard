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

#include "offdevice_intermediate_dict/offdevice_intermediate_dict.h"

#include <gtest/gtest.h>

#include <vector>

#include "dictionary/property/word_property.h"
#include "utils/int_array_view.h"

namespace latinime {
namespace dicttoolkit {
namespace {

const std::vector<int> getCodePointVector(const char *str) {
    std::vector<int> codePoints;
    while (*str) {
        codePoints.push_back(*str);
        ++str;
    }
    return codePoints;
}

const WordProperty getDummpWordProperty(const std::vector<int> &&codePoints) {
    return WordProperty(std::move(codePoints), UnigramProperty(), std::vector<NgramProperty>());
}

TEST(OffdeviceIntermediateDictTest, TestAddWordProperties) {
    OffdeviceIntermediateDict dict = OffdeviceIntermediateDict(
            OffdeviceIntermediateDictHeader(OffdeviceIntermediateDictHeader::AttributeMap()));
    EXPECT_EQ(nullptr, dict.getWordProperty(CodePointArrayView()));

    const WordProperty wordProperty0 = getDummpWordProperty(getCodePointVector("abcd"));
    EXPECT_TRUE(dict.addWord(wordProperty0));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty0.getCodePoints()));

    const WordProperty wordProperty1 = getDummpWordProperty(getCodePointVector("efgh"));
    EXPECT_TRUE(dict.addWord(wordProperty1));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty1.getCodePoints()));

    const WordProperty wordProperty2 = getDummpWordProperty(getCodePointVector("ab"));
    EXPECT_TRUE(dict.addWord(wordProperty2));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty2.getCodePoints()));

    const WordProperty wordProperty3 = getDummpWordProperty(getCodePointVector("abcdefg"));
    EXPECT_TRUE(dict.addWord(wordProperty3));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty3.getCodePoints()));

    const WordProperty wordProperty4 = getDummpWordProperty(getCodePointVector("efef"));
    EXPECT_TRUE(dict.addWord(wordProperty4));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty4.getCodePoints()));

    const WordProperty wordProperty5 = getDummpWordProperty(getCodePointVector("ef"));
    EXPECT_TRUE(dict.addWord(wordProperty5));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty5.getCodePoints()));

    const WordProperty wordProperty6 = getDummpWordProperty(getCodePointVector("abcd"));
    EXPECT_FALSE(dict.addWord(wordProperty6)) << "Adding the same word multiple times should fail.";

    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty0.getCodePoints()));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty1.getCodePoints()));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty2.getCodePoints()));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty3.getCodePoints()));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty4.getCodePoints()));
    EXPECT_NE(nullptr, dict.getWordProperty(wordProperty5.getCodePoints()));
}

} // namespace
} // namespace dicttoolkit
} // namespace latinime
