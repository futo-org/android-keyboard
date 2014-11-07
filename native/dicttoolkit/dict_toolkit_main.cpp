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

#include <cstdio>

#include "dict_toolkit_defines.h"
#include "utils/command_utils.h"

void usage(int argc, char **argv) {
    fprintf(stderr, "Usage: %s <command> [arguments]\n", argc > 0 ? argv[0] : "dicttoolkit");
}

int main(int argc, char **argv) {
    if (argc < MIN_ARG_COUNT) {
        usage(argc, argv);
        return 1;
    }
    using namespace latinime::dicttoolkit;
    const CommandType commandType = CommandUtils::getCommandType(argv[1]);
    if (commandType == CommandType::Unknown) {
        CommandUtils::printCommandUnknownMessage(argv[0], argv[1]);
        return 1;
    }
    const auto executor = CommandUtils::getCommandExecutor(commandType);
    return executor(argc - 1, argv + 1);
}
