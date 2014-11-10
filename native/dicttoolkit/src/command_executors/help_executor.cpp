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

#include "command_executors/help_executor.h"

#include <cstdio>
#include <functional>
#include <vector>

#include "command_executors/diff_executor.h"
#include "command_executors/header_executor.h"
#include "command_executors/info_executor.h"
#include "command_executors/makedict_executor.h"
#include "utils/command_utils.h"

namespace latinime {
namespace dicttoolkit {

const char *const HelpExecutor::COMMAND_NAME = "help";

/* static */ int HelpExecutor::run(const int argc, char **argv) {
    printf("Available commands:\n\n");
    const std::vector<std::function<void(void)>> printUsageMethods = {DiffExecutor::printUsage,
            HeaderExecutor::printUsage, InfoExecutor::printUsage, MakedictExecutor::printUsage,
            printUsage};
    for (const auto &printUsageMethod : printUsageMethods) {
        printUsageMethod();
    }
    return 0;
}

/* static */ void HelpExecutor::printUsage() {
    printf("*** %s\n", COMMAND_NAME);
    printf("Usage: %s\n", COMMAND_NAME);
    printf("Show this help list.\n\n");
}

} // namespace dicttoolkit
} // namespace latinime
