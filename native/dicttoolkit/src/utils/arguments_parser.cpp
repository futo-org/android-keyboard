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

namespace latinime {
namespace dicttoolkit {

const int ArgumentSpec::UNLIMITED_COUNT = -1;

bool ArgumentsParser::validateSpecs() const {
    for (size_t i = 0; i < mArgumentSpecs.size() ; ++i) {
        if (mArgumentSpecs[i].getMinCount() != mArgumentSpecs[i].getMaxCount()
                && i != mArgumentSpecs.size() - 1) {
            AKLOGE("Variable length argument must be at the end.");
            return false;
        }
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
        if (spec.takeValue()) {
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
        if (spec.takeValue()) {
            printf(" <%s>", spec.getValueName().c_str());
        }
        printf("\t\t\t%s", spec.getDescription().c_str());
        if (spec.takeValue() && !spec.getDefaultValue().empty()) {
            printf("\tdefault: %s", spec.getDefaultValue().c_str());
        }
        printf("\n");
    }
    for (const auto &argSpec : mArgumentSpecs) {
        printf(" <%s>\t\t\t%s\n", argSpec.getName().c_str(), argSpec.getDescription().c_str());
    }
    printf("\n\n");
}

const ArgumentsAndOptions ArgumentsParser::parseArguments(const int argc, char **argv) const {
    // TODO: Implement
    return ArgumentsAndOptions();
}

} // namespace dicttoolkit
} // namespace latinime
