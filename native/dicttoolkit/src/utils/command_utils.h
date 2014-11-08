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

#ifndef LATINIME_DICT_TOOLKIT_COMMAND_UTILS_H
#define LATINIME_DICT_TOOLKIT_COMMAND_UTILS_H

#include <cstdio>
#include <string>

#include "dict_toolkit_defines.h"

namespace latinime {
namespace dicttoolkit {

enum class CommandType : int {
    Info,
    Diff,
    Makedict,
    Header,
    Help,
    Unknown
};

class CommandUtils {
public:
    static CommandType getCommandType(const std::string &commandName);

    static void printCommandUnknownMessage(const std::string &programName,
            const std::string &commandName) {
        fprintf(stderr, "Command '%s' is unknown. Try '%s %s' for more information.\n",
                commandName.c_str(), programName.c_str(), COMMAND_NAME_HELP);
    }

private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(CommandUtils);

    static const char *const COMMAND_NAME_INFO;
    static const char *const COMMAND_NAME_DIFF;
    static const char *const COMMAND_NAME_MAKEDICT;
    static const char *const COMMAND_NAME_HEADER;
    static const char *const COMMAND_NAME_HELP;
};
} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_COMMAND_UTILS_H
