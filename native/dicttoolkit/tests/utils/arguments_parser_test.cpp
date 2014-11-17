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

#include "utils/arguments_parser.h"

#include <gtest/gtest.h>

namespace latinime {
namespace dicttoolkit {
namespace {

TEST(ArgumentsParserTests, TestValitadeSpecs) {
    {
        std::unordered_map<std::string, OptionSpec> optionSpecs;
        std::vector<ArgumentSpec> argumentSpecs;
        EXPECT_TRUE(
                ArgumentsParser(std::move(optionSpecs), std::move(argumentSpecs)).validateSpecs());
    }
    {
        std::unordered_map<std::string, OptionSpec> optionSpecs;
        optionSpecs["a"] = OptionSpec::keyValueOption("valueName", "default", "description");
        const std::vector<ArgumentSpec> argumentSpecs = {
            ArgumentSpec::singleArgument("name", "description"),
            ArgumentSpec::variableLengthArguments("name2", 0 /* minCount */,  1 /* maxCount */,
                    "description2")
        };
        EXPECT_TRUE(
                ArgumentsParser(std::move(optionSpecs), std::move(argumentSpecs)).validateSpecs());
    }
    {
        const std::vector<ArgumentSpec> argumentSpecs = {
            ArgumentSpec::variableLengthArguments("name", 0 /* minCount */,  0 /* maxCount */,
                    "description")
        };
        EXPECT_FALSE(ArgumentsParser(std::unordered_map<std::string, OptionSpec>(),
                std::move(argumentSpecs)).validateSpecs());
    }
    {
        const std::vector<ArgumentSpec> argumentSpecs = {
            ArgumentSpec::singleArgument("name", "description"),
            ArgumentSpec::variableLengthArguments("name", 0 /* minCount */,  1 /* maxCount */,
                    "description")
        };
        EXPECT_FALSE(ArgumentsParser(std::unordered_map<std::string, OptionSpec>(),
                std::move(argumentSpecs)).validateSpecs());
    }
    {
        const std::vector<ArgumentSpec> argumentSpecs = {
            ArgumentSpec::variableLengthArguments("name", 0 /* minCount */,  1 /* maxCount */,
                    "description"),
            ArgumentSpec::singleArgument("name2", "description2")
        };
        EXPECT_FALSE(ArgumentsParser(std::unordered_map<std::string, OptionSpec>(),
                std::move(argumentSpecs)).validateSpecs());
    }
}

} // namespace
} // namespace dicttoolkit
} // namespace latinime
