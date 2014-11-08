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

#include "utils/command_utils.h"

#include <gtest/gtest.h>

namespace latinime {
namespace dicttoolkit {
namespace {

TEST(CommandUtilsTests, TestGetCommandType) {
    EXPECT_EQ(CommandUtils::getCommandType(""), CommandType::Unknown);
    EXPECT_EQ(CommandUtils::getCommandType("abc"), CommandType::Unknown);
    EXPECT_EQ(CommandUtils::getCommandType("info"), CommandType::Info);
    EXPECT_EQ(CommandUtils::getCommandType("diff"), CommandType::Diff);
    EXPECT_EQ(CommandUtils::getCommandType("makedict"), CommandType::Makedict);
    EXPECT_EQ(CommandUtils::getCommandType("header"), CommandType::Header);
    EXPECT_EQ(CommandUtils::getCommandType("help"), CommandType::Help);
}

} // namespace
} // namespace dicttoolkit
} // namespace latinime
