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

#ifndef LATINIME_DICT_TOOLKIT_ARGUMENTS_AND_OPTIONS_H
#define LATINIME_DICT_TOOLKIT_ARGUMENTS_AND_OPTIONS_H

#include <string>
#include <unordered_map>
#include <vector>

#include "dict_toolkit_defines.h"

namespace latinime {
namespace dicttoolkit {

class ArgumentsAndOptions {
 public:
    ArgumentsAndOptions() : mIsValid(false), mOptions(), mArguments() {}

    ArgumentsAndOptions(std::unordered_map<std::string, std::string> &&options,
            std::unordered_map<std::string, std::vector<std::string>> &&arguments)
            : mIsValid(true), mOptions(std::move(options)), mArguments(std::move(arguments)) {}

    bool isValid() const {
        return mIsValid;
    }

    bool hasOption(const std::string &optionName) const {
        return mOptions.find(optionName) != mOptions.end();
    }

    const std::string &getOptionValue(const std::string &optionName) const {
        const auto &it = mOptions.find(optionName);
        ASSERT(it != mOptions.end());
        return it->second;
    }

    bool hasArgument(const std::string &name) const {
        const auto &it = mArguments.find(name);
        return it != mArguments.end() && !it->second.empty();
    }

    const std::string &getSingleArgument(const std::string &name) const {
        const auto &it = mArguments.find(name);
        ASSERT(it != mArguments.end() && !it->second.empty());
        return it->second.front();
    }

    const std::vector<std::string> &getVariableLengthArguments(const std::string &name) const {
        const auto &it = mArguments.find(name);
        ASSERT(it != mArguments.end());
        return it->second;
    }

 private:
    DISALLOW_ASSIGNMENT_OPERATOR(ArgumentsAndOptions);

    const bool mIsValid;
    const std::unordered_map<std::string, std::string> mOptions;
    const std::unordered_map<std::string, std::vector<std::string>> mArguments;
};
} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_ARGUMENTS_AND_OPTIONS_H
