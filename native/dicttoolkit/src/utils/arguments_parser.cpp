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

#include <unordered_set>

namespace latinime {
namespace dicttoolkit {

const size_t ArgumentSpec::UNLIMITED_COUNT = S_INT_MAX;

bool ArgumentsParser::validateSpecs() const {
    std::unordered_set<std::string> argumentNameSet;
    for (size_t i = 0; i < mArgumentSpecs.size() ; ++i) {
        if (mArgumentSpecs[i].getMinCount() == 0 && mArgumentSpecs[i].getMaxCount() == 0) {
            AKLOGE("minCount = maxCount = 0 for %s.", mArgumentSpecs[i].getName().c_str());
            return false;
        }
        if (mArgumentSpecs[i].getMinCount() != mArgumentSpecs[i].getMaxCount()
                && i != mArgumentSpecs.size() - 1) {
            AKLOGE("Variable length argument must be at the end.",
                    mArgumentSpecs[i].getName().c_str()v  );
            return false;
        }
        if (argumentNameSet.count(mArgumentSpecs[i].getName()) > 0) {
            AKLOGE("Multiple arguments have the same name \"%s\".",
                    mArgumentSpecs[i].getName().c_str());
            return false;
        }
        argumentNameSet.insert(mArgumentSpecs[i].getName());
    }
    return true;
}

void ArgumentsParser::printUsage(const std::string &commandName,
        const std::string &description) const {
    printf("Usage: %s", commandName.c_str());
    for (const auto &option : mOptionSpecs) {
        const std::string &optionName = option.first;
        const OptionSpec &spec = option.second;
        printf(" [-%s", optionName.c_str());
        if (spec.needsValue()) {
            printf(" <%s>", spec.getValueName().c_str());
        }
        printf("]");
    }
    for (const auto &argSpec : mArgumentSpecs) {
        if (argSpec.getMinCount() == 0 && argSpec.getMaxCount() == 1) {
            printf(" [<%s>]", argSpec.getName().c_str());
        } else if (argSpec.getMinCount() == 1 && argSpec.getMaxCount() == 1) {
            printf(" <%s>", argSpec.getName().c_str());
        } else if (argSpec.getMinCount() == 0) {
            printf(" [<%s>...]", argSpec.getName().c_str());
        } else if (argSpec.getMinCount() == 1) {
            printf(" <%s>...", argSpec.getName().c_str());
        }
    }
    printf("\n%s\n\n", description.c_str());
    for (const auto &option : mOptionSpecs) {
        const std::string &optionName = option.first;
        const OptionSpec &spec = option.second;
        printf(" -%s", optionName.c_str());
        if (spec.needsValue()) {
            printf(" <%s>", spec.getValueName().c_str());
        }
        printf("\t\t\t%s", spec.getDescription().c_str());
        if (spec.needsValue() && !spec.getDefaultValue().empty()) {
            printf("\tdefault: %s", spec.getDefaultValue().c_str());
        }
        printf("\n");
    }
    for (const auto &argSpec : mArgumentSpecs) {
        printf(" <%s>\t\t\t%s\n", argSpec.getName().c_str(), argSpec.getDescription().c_str());
    }
    printf("\n\n");
}

const ArgumentsAndOptions ArgumentsParser::parseArguments(const int argc, char **argv,
        const bool printErrorMessage) const {
    if (argc <= 0) {
        AKLOGE("Invalid argc (%d).", argc);
        ASSERT(false);
        return ArgumentsAndOptions();
    }
    std::unordered_map<std::string, std::string> options;
    for (const auto &entry : mOptionSpecs) {
        const std::string &optionName = entry.first;
        const OptionSpec &optionSpec = entry.second;
        if (optionSpec.needsValue() && !optionSpec.getDefaultValue().empty()) {
            // Set default value.
            options[optionName] = optionSpec.getDefaultValue();
        }
    }
    std::unordered_map<std::string, std::vector<std::string>> arguments;
    auto argumentSpecIt = mArgumentSpecs.cbegin();
    for (int i = 1; i < argc; ++i) {
        const std::string arg = argv[i];
        if (arg.length() > 1 && arg[0] == '-') {
            // option
            const std::string optionName = arg.substr(1);
            const auto it = mOptionSpecs.find(optionName);
            if (it == mOptionSpecs.end()) {
                if (printErrorMessage) {
                    fprintf(stderr, "Unknown option: '%s'\n", optionName.c_str());
                }
                return ArgumentsAndOptions();
            }
            std::string optionValue;
            if (it->second.needsValue()) {
                ++i;
                if (i >= argc) {
                    if (printErrorMessage) {
                        fprintf(stderr, "Missing argument for option '%s'\n", optionName.c_str());
                    }
                    return ArgumentsAndOptions();
                }
                optionValue = argv[i];
            }
            options[optionName] = optionValue;
        } else {
            // argument
            if (argumentSpecIt == mArgumentSpecs.end()) {
                if (printErrorMessage) {
                    fprintf(stderr, "Too many arguments.\n");
                }
                return ArgumentsAndOptions();
            }
            arguments[argumentSpecIt->getName()].push_back(arg);
            if (arguments[argumentSpecIt->getName()].size() >= argumentSpecIt->getMaxCount()) {
                ++argumentSpecIt;
            }
        }
    }

    if (argumentSpecIt != mArgumentSpecs.end()) {
        const auto &it = arguments.find(argumentSpecIt->getName());
        const size_t minCount = argumentSpecIt->getMinCount();
        const size_t actualcount = it == arguments.end() ? 0 : it->second.size();
        if (minCount > actualcount) {
            if (printErrorMessage) {
                fprintf(stderr, "Not enough arguments. %zd argumant(s) required for <%s>\n",
                        minCount, argumentSpecIt->getName().c_str());
            }
            return ArgumentsAndOptions();
        }
    }
    return ArgumentsAndOptions(std::move(options), std::move(arguments));
}

} // namespace dicttoolkit
} // namespace latinime
