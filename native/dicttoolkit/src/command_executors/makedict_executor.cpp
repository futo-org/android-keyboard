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

#include "command_executors/makedict_executor.h"

#include <cstdio>

namespace latinime {
namespace dicttoolkit {

const char *const MakedictExecutor::COMMAND_NAME = "makedict";

/* static */ int MakedictExecutor::run(const int argc, char **argv) {
    const ArgumentsAndOptions argumentsAndOptions =
            getArgumentsParser().parseArguments(argc, argv, true /* printErrorMessages */);
    if (!argumentsAndOptions.isValid()) {
        printUsage();
        return 1;
    }
    fprintf(stderr, "Command '%s' has not been implemented yet.\n", COMMAND_NAME);
    return 0;
}

/* static */ void MakedictExecutor::printUsage() {
    printf("*** %s\n", COMMAND_NAME);
    getArgumentsParser().printUsage(COMMAND_NAME,
            "Converts a source dictionary file to one or several outputs.\n"
            "Source can be a binary dictionary file or a combined format file.\n"
            "Binary version 2 (Jelly Bean), 4, and combined format outputs are supported.");
}

/* static */const ArgumentsParser MakedictExecutor::getArgumentsParser() {
    std::unordered_map<std::string, OptionSpec> optionSpecs;
    optionSpecs["o"] = OptionSpec::keyValueOption("format", "2",
            "output format version: 2/4/combined");
    optionSpecs["t"] = OptionSpec::keyValueOption("mode", "off",
            "code point table switch: on/off/auto");

    const std::vector<ArgumentSpec> argumentSpecs = {
        ArgumentSpec::singleArgument("src_dict", "source dictionary file"),
        ArgumentSpec::singleArgument("dest_dict", "output dictionary file")
    };

    return ArgumentsParser(std::move(optionSpecs), std::move(argumentSpecs));
}

} // namespace dicttoolkit
} // namespace latinime
