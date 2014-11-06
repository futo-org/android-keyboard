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

namespace latinime {
namespace dicttoolkit {

const char *const CommandUtils::COMMAND_NAME_INFO = "info";
const char *const CommandUtils::COMMAND_NAME_DIFF = "diff";
const char *const CommandUtils::COMMAND_NAME_MAKEDICT = "makedict";
const char *const CommandUtils::COMMAND_NAME_HEADER = "header";
const char *const CommandUtils::COMMAND_NAME_HELP = "help";

/* static */ CommandType CommandUtils::getCommandType(const std::string &commandName) {
    if (commandName == COMMAND_NAME_INFO) {
        return CommandType::Info;
    } else if (commandName == COMMAND_NAME_DIFF) {
        return CommandType::Diff;
    } else if (commandName == COMMAND_NAME_MAKEDICT) {
        return CommandType::Makedict;
    } else if (commandName == COMMAND_NAME_HEADER) {
        return CommandType::Header;
    } else if (commandName == COMMAND_NAME_HELP) {
        return CommandType::Help;
    } else {
        return CommandType::Unknown;
    }
}

} // namespace dicttoolkit
} // namespace latinime
