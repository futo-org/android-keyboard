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

#include "command_executors/diff_executor.h"

#include <cstdio>

namespace latinime {
namespace dicttoolkit {

const char *const DiffExecutor::COMMAND_NAME = "diff";

/* static */ int DiffExecutor::run(const int argc, char **argv) {
    fprintf(stderr, "Command '%s' has not been implemented yet.\n", COMMAND_NAME);
    return 0;
}

/* static */ void DiffExecutor::printUsage() {
    printf("*** %s\n", COMMAND_NAME);
    getArgumentsParser().printUsage(COMMAND_NAME, "Shows differences between two dictionaries.");
}

/* static */ const ArgumentsParser DiffExecutor::getArgumentsParser() {
    std::unordered_map<std::string, OptionSpec> optionSpecs;
    optionSpecs["p"] = OptionSpec::switchOption("(plumbing) produce output suitable for a script");

    const std::vector<ArgumentSpec> argumentSpecs = {
        ArgumentSpec::singleArgument("dict1", "dictionary file"),
        ArgumentSpec::singleArgument("dict2", "dictionary file")
    };

    return ArgumentsParser(std::move(optionSpecs), std::move(argumentSpecs));
}

} // namespace dicttoolkit
} // namespace latinime
