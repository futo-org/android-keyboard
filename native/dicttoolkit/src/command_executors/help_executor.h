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

#ifndef LATINIME_DICT_TOOLKIT_HELP_EXECUTOR_H
#define LATINIME_DICT_TOOLKIT_HELP_EXECUTOR_H

#include "dict_toolkit_defines.h"

namespace latinime {
namespace dicttoolkit {

class HelpExecutor final {
 public:
    static const char *const COMMAND_NAME;

    static int run(const int argc, char **argv);
    static void printUsage();

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(HelpExecutor);
};

} // namespace dicttoolkit
} // namepsace latinime
#endif // LATINIME_DICT_TOOLKIT_HELP_EXECUTOR_H
