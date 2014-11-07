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

#include <cstdio>

#include "command_executors/diff_executor.h"
#include "command_executors/header_executor.h"
#include "command_executors/help_executor.h"
#include "command_executors/info_executor.h"
#include "command_executors/makedict_executor.h"

namespace latinime {
namespace dicttoolkit {

/* static */ CommandType CommandUtils::getCommandType(const std::string &commandName) {
    if (commandName == InfoExecutor::COMMAND_NAME) {
        return CommandType::Info;
    } else if (commandName == DiffExecutor::COMMAND_NAME) {
        return CommandType::Diff;
    } else if (commandName == MakedictExecutor::COMMAND_NAME) {
        return CommandType::Makedict;
    } else if (commandName == HeaderExecutor::COMMAND_NAME) {
        return CommandType::Header;
    } else if (commandName == HelpExecutor::COMMAND_NAME) {
        return CommandType::Help;
    } else {
        return CommandType::Unknown;
    }
}

/* static */ void CommandUtils::printCommandUnknownMessage(const std::string &programName,
        const std::string &commandName) {
    fprintf(stderr, "Command '%s' is unknown. Try '%s %s' for more information.\n",
            commandName.c_str(), programName.c_str(), HelpExecutor::COMMAND_NAME);
}

/* static */ std::function<int(int, char **)> CommandUtils::getCommandExecutor(
        const CommandType commandType) {
    switch (commandType) {
        case CommandType::Info:
            return InfoExecutor::run;
        case CommandType::Diff:
            return DiffExecutor::run;
        case CommandType::Makedict:
            return MakedictExecutor::run;
        case CommandType::Header:
            return HeaderExecutor::run;
        case CommandType::Help:
            return HelpExecutor::run;
        default:
            return [] (int, char **) -> int {
                printf("Command executor not found.");
                return 1;
            };
    }
}

} // namespace dicttoolkit
} // namespace latinime
