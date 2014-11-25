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

int initArgv(char *mutableCommandLine, char **argv) {
    bool readingSeparator = false;
    int argc = 1;
    argv[0] = mutableCommandLine;
    const size_t length = strlen(mutableCommandLine);
    for (size_t i = 0; i < length; ++i) {
        if (mutableCommandLine[i] != ' ' && readingSeparator) {
            readingSeparator = false;
            argv[argc] = mutableCommandLine + i;
            ++argc;
        } else if (mutableCommandLine[i] == ' ' && !readingSeparator) {
            readingSeparator = true;
            mutableCommandLine[i] = '\0';
        }
    }
    argv[argc] = nullptr;
    return argc;
}

TEST(ArgumentsParserTests, TestParseArguments) {
    std::unordered_map<std::string, OptionSpec> optionSpecs;
    optionSpecs["a"] = OptionSpec::switchOption("description");
    optionSpecs["b"] = OptionSpec::keyValueOption("valueName", "default", "description");
    const std::vector<ArgumentSpec> argumentSpecs = {
        ArgumentSpec::singleArgument("arg0", "description"),
        ArgumentSpec::variableLengthArguments("arg1", 0 /* minCount */,  2 /* maxCount */,
                "description"),
    };
    const ArgumentsParser parser =
            ArgumentsParser(std::move(optionSpecs), std::move(argumentSpecs));

    {
        char kMutableCommandLine[1024] = "command arg";
        char *argv[128] = {};
        const int argc = initArgv(kMutableCommandLine, argv);
        ASSERT_EQ(2, argc);
        const ArgumentsAndOptions argumentsAndOptions = parser.parseArguments(
                argc, argv, false /* printErrorMessages */);
        EXPECT_FALSE(argumentsAndOptions.hasOption("a"));
        EXPECT_EQ("default", argumentsAndOptions.getOptionValue("b"));
        EXPECT_EQ("arg", argumentsAndOptions.getSingleArgument("arg0"));
        EXPECT_FALSE(argumentsAndOptions.hasArgument("arg1"));
    }
    {
        char kArgumentBuffer[1024] = "command -a arg arg";
        char *argv[128] = {};
        const int argc = initArgv(kArgumentBuffer, argv);
        ASSERT_EQ(4, argc);
        const ArgumentsAndOptions argumentsAndOptions = parser.parseArguments(
                argc, argv, false /* printErrorMessages */);
        EXPECT_TRUE(argumentsAndOptions.hasOption("a"));
        EXPECT_EQ("default", argumentsAndOptions.getOptionValue("b"));
        EXPECT_EQ("arg", argumentsAndOptions.getSingleArgument("arg0"));
        EXPECT_TRUE(argumentsAndOptions.hasArgument("arg1"));
        EXPECT_EQ(1u, argumentsAndOptions.getVariableLengthArguments("arg1").size());
    }
    {
        char kArgumentBuffer[1024] = "command -b value arg arg1 arg2";
        char *argv[128] = {};
        const int argc = initArgv(kArgumentBuffer, argv);
        ASSERT_EQ(6, argc);
        const ArgumentsAndOptions argumentsAndOptions = parser.parseArguments(
                argc, argv, false /* printErrorMessages */);
        EXPECT_FALSE(argumentsAndOptions.hasOption("a"));
        EXPECT_EQ("value", argumentsAndOptions.getOptionValue("b"));
        EXPECT_EQ("arg", argumentsAndOptions.getSingleArgument("arg0"));
        const std::vector<std::string> &arg1 =
                argumentsAndOptions.getVariableLengthArguments("arg1");
        EXPECT_EQ(2u, arg1.size());
        EXPECT_EQ("arg1", arg1[0]);
        EXPECT_EQ("arg2", arg1[1]);
    }
}

} // namespace
} // namespace dicttoolkit
} // namespace latinime
