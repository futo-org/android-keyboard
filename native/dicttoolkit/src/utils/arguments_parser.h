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

#ifndef LATINIME_DICT_TOOLKIT_ARGUMENTS_PARSER_H
#define LATINIME_DICT_TOOLKIT_ARGUMENTS_PARSER_H

#include <string>
#include <unordered_map>
#include <vector>

#include "dict_toolkit_defines.h"
#include "utils/arguments_and_options.h"

namespace latinime {
namespace dicttoolkit {

class OptionSpec {
 public:
    // Default constructor and assignment operator is enabled to be used with std::unordered_map.
    OptionSpec() = default;
    OptionSpec &operator=(const OptionSpec &) = default;

    static OptionSpec keyValueOption(const std::string &valueName, const std::string &defaultValue,
            const std::string &description) {
        return OptionSpec(true /* needsValue */, valueName, defaultValue, description);
    }

    static OptionSpec switchOption(const std::string &description) {
        return OptionSpec(false /* needsValue */, "" /* valueName */, "" /* defaultValue */,
                description);
    }

    bool needsValue() const { return mNeedsValue; }
    const std::string &getValueName() const { return mValueName; }
    const std::string &getDefaultValue() const { return mDefaultValue; }
    const std::string &getDescription() const { return mDescription; }

 private:
    OptionSpec(const bool needsValue, const std::string &valueName, const std::string &defaultValue,
            const std::string &description)
            : mNeedsValue(needsValue), mValueName(valueName), mDefaultValue(defaultValue),
              mDescription(description) {}

    // Whether the option have to be used with a value or just a switch.
    // e.g. 'f' in "command -f /path/to/file" is mNeedsValue == true.
    //      'f' in "command -f -t" is mNeedsValue == false.
    bool mNeedsValue;
    // Name of the value used to show usage.
    std::string mValueName;
    std::string mDefaultValue;
    std::string mDescription;
};

class ArgumentSpec {
 public:
    static const size_t UNLIMITED_COUNT;

    static ArgumentSpec singleArgument(const std::string &name, const std::string &description) {
        return ArgumentSpec(name, 1 /* minCount */, 1 /* maxCount */, description);
    }

    static ArgumentSpec variableLengthArguments(const std::string &name, const size_t minCount,
            const size_t maxCount, const std::string &description) {
        return ArgumentSpec(name, minCount, maxCount, description);
    }

    const std::string &getName() const { return mName; }
    size_t getMinCount() const { return mMinCount; }
    size_t getMaxCount() const { return mMaxCount; }
    const std::string &getDescription() const { return mDescription; }

 private:
    DISALLOW_DEFAULT_CONSTRUCTOR(ArgumentSpec);

    ArgumentSpec(const std::string &name, const size_t minCount, const size_t maxCount,
            const std::string &description)
            : mName(name), mMinCount(minCount), mMaxCount(maxCount), mDescription(description) {}

    const std::string mName;
    const size_t mMinCount;
    const size_t mMaxCount;
    const std::string mDescription;
};

class ArgumentsParser {
 public:
    ArgumentsParser(const std::unordered_map<std::string, OptionSpec> &&optionSpecs,
            const std::vector<ArgumentSpec> &&argumentSpecs)
            : mOptionSpecs(std::move(optionSpecs)), mArgumentSpecs(std::move(argumentSpecs)) {}

    const ArgumentsAndOptions parseArguments(const int argc, char **argv,
            const bool printErrorMessage) const;
    bool validateSpecs() const;
    void printUsage(const std::string &commandName, const std::string &description) const;

 private:
    DISALLOW_DEFAULT_CONSTRUCTOR(ArgumentsParser);
    DISALLOW_ASSIGNMENT_OPERATOR(ArgumentsParser);

    const std::unordered_map<std::string, OptionSpec> mOptionSpecs;
    const std::vector<ArgumentSpec> mArgumentSpecs;
};

} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_ARGUMENTS_PARSER_H
