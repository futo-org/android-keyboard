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

#include "utils/utf8_utils.h"

#include <gtest/gtest.h>

#include <vector>

#include "utils/int_array_view.h"

namespace latinime {
namespace dicttoolkit {
namespace {

TEST(Utf8UtilsTests, TestGetCodePoints) {
    {
        const std::vector<int> codePoints = Utf8Utils::getCodePoints("");
        EXPECT_EQ(0u, codePoints.size());
    }
    {
        const std::vector<int> codePoints = Utf8Utils::getCodePoints("test");
        EXPECT_EQ(4u, codePoints.size());
        EXPECT_EQ('t', codePoints[0]);
        EXPECT_EQ('e', codePoints[1]);
        EXPECT_EQ('s', codePoints[2]);
        EXPECT_EQ('t', codePoints[3]);
    }
    {
        const std::vector<int> codePoints = Utf8Utils::getCodePoints(u8"\u3042a\u03C2\u0410");
        EXPECT_EQ(4u, codePoints.size());
        EXPECT_EQ(0x3042, codePoints[0]); // HIRAGANA LETTER A
        EXPECT_EQ('a', codePoints[1]);
        EXPECT_EQ(0x03C2, codePoints[2]); // CYRILLIC CAPITAL LETTER A
        EXPECT_EQ(0x0410, codePoints[3]); // GREEK SMALL LETTER FINAL SIGMA
    }
    {
        const std::vector<int> codePoints = Utf8Utils::getCodePoints(u8"\U0001F36A?\U0001F752");
        EXPECT_EQ(3u, codePoints.size());
        EXPECT_EQ(0x1F36A, codePoints[0]); // COOKIE
        EXPECT_EQ('?', codePoints[1]);
        EXPECT_EQ(0x1F752, codePoints[2]); // ALCHEMICAL SYMBOL FOR STARRED TRIDENT
    }

    // Redundant UTF-8 sequences must be rejected.
    EXPECT_TRUE(Utf8Utils::getCodePoints("\xC0\xAF").empty());
    EXPECT_TRUE(Utf8Utils::getCodePoints("\xE0\x80\xAF").empty());
    EXPECT_TRUE(Utf8Utils::getCodePoints("\xF0\x80\x80\xAF").empty());
}

TEST(Utf8UtilsTests, TestGetUtf8String) {
    {
        const std::vector<int> codePoints = {'t', 'e', 's', 't'};
        EXPECT_EQ("test", Utf8Utils::getUtf8String(CodePointArrayView(codePoints)));
    }
    {
        const std::vector<int> codePoints = {
                0x00E0 /* LATIN SMALL LETTER A WITH GRAVE */,
                0x03C2 /* GREEK SMALL LETTER FINAL SIGMA */,
                0x0430 /* CYRILLIC SMALL LETTER A */,
                0x3042 /* HIRAGANA LETTER A */,
                0x1F36A /* COOKIE */,
                0x1F752 /* ALCHEMICAL SYMBOL FOR STARRED TRIDENT */
        };
        EXPECT_EQ(u8"\u00E0\u03C2\u0430\u3042\U0001F36A\U0001F752",
                Utf8Utils::getUtf8String(CodePointArrayView(codePoints)));
    }
}

} // namespace
} // namespace dicttoolkit
} // namespace latinime
